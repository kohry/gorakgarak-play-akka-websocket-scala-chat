package controllers

import javax.inject._
import play.api._
import play.api.mvc._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import play.api.libs.iteratee._
import akka.actor.ActorRef
import play.api.libs.streams._
import akka.stream.Materializer
import akka.actor.ActorRefFactory
import play.api.libs.json.Format
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._
import akka.actor.PoisonPill

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject() (implicit materializer: Materializer) extends Controller {

  /**
   * Create an Action to render an HTML page with a welcome message.
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def chatStart = Action { request =>
    Ok(views.html.chat(request))
  }

  def chatStart2 = Action { request =>
    Ok(views.html.chat2(request))
  }

  def akka = Action {

    val actorSystem = ActorSystem("mySystem")
    val alba1 = actorSystem.actorOf(Props[Alba], name = "alba_actor1")
    val alba2 = actorSystem.actorOf(Props[Alba], name = "alba_actor2")

    alba1 ! "Hey Alba!"
    alba2 ! "hi"

    Ok(views.html.index("Your new application is ready."))

  }

  def chat = WebSocket.using[String] { implicit request =>

    val in = Iteratee.foreach[String] { msgFromClient => println(msgFromClient) }
    val out = Enumerator("Server To Client").andThen(Enumerator.eof)

    (in, out)
  }

  case class Join(username: String)
  case class Leave(username: String)
  case class Talk(username: String, message: String)

  class ChatActor(actorRef: ActorRef) extends Actor {

    def receive = {

      case str: String => {
        println("클라이언트로부터 " + str + " 문자열을 받았습니다.")
        val json = Json.parse(str)
        println("JSON으로 변환 하였습니다")
        (json \ "type").as[String] match {
          case "join"  => self ! Join((json \ "username").as[String])
          case "leave" => self ! Leave((json \ "username").as[String])
          case "talk" => self ! Talk((json \ "username").as[String],
            (json \ "msg").as[String])
        }
      }

      case Join(username) => {
        ChatActor.users = (username, actorRef) :: ChatActor.users
        println(ChatActor.users)
        actorRef ! ("채팅방에 입장하셨습니다.")
        broadcast(username + "께서 채팅에 참가하셨습니다.")
      }
      case Leave(username) => {
        ChatActor.users = ChatActor.users.filter(u => u._1 != username)
        broadcast(username + "께서 나가셨습니다.")
        actorRef ! ("채팅방에서 퇴장하였습니다")
        actorRef ! PoisonPill
      }
      case Talk(username, msg) => {
        println(msg)
        broadcast(username + " : " + msg)
      }
    }

    def broadcast(msg: String) = {
      ChatActor.users.foreach(u => u._2 ! msg)
    }

  }

  object ChatActor {
    var users = List[(String, ActorRef)]() //대화에 참여하는 user의 리스트
    def props(actor: ActorRef) = Props(new ChatActor(actor))
  }

  def chat2 = WebSocket.accept[String, String] { request =>
    implicit val system = ActorSystem("MyActorSystem")
    ActorFlow.actorRef(actor => ChatActor.props(actor))

  }

}

class Alba extends Actor {
  def receive = {
    case "Hey Alba!" =>
      Thread.sleep(1000); println("sleep")
    case _           => println("Pardon?")
  }
}
