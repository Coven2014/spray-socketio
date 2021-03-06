package spray.contrib.socketio

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import scala.collection.concurrent
import akka.contrib.pattern.DistributedPubSubMediator.{ Publish, Unsubscribe, SubscribeAck, Subscribe }

object LocalConnectionActive {
  def props(mediator: ActorRef) = Props(classOf[LocalConnectionActive], mediator)
}

class LocalConnectionActive(mediator: ActorRef) extends ConnectionActive with Actor with ActorLogging {
  val namespaceMediator = mediator
  val broadcastMediator = mediator

  def receive = working
}

object LocalConnectionActiveResolver {
  def props(mediator: ActorRef) = Props(classOf[LocalConnectionActiveResolver], mediator)
}

class LocalConnectionActiveResolver(val mediator: ActorRef) extends Actor with ActorLogging {

  def receive = {
    case ConnectionActive.CreateSession(sessionId: String) =>
      context.child(sessionId) match {
        case Some(_) =>
        case None =>
          val connectActive = context.actorOf(LocalConnectionActive.props(mediator), name = sessionId)
          context.watch(connectActive)
      }

    case cmd: ConnectionActive.Command =>
      context.child(cmd.sessionId) match {
        case Some(ref) => ref forward cmd
        case None      => log.warning("Failed to select actor {}", cmd.sessionId)
      }

    case Terminated(ref) =>
  }
}

object LocalMediator {
  def props() = Props(classOf[LocalMediator])

  private val topicToSubscriptions = concurrent.TrieMap[String, Set[ActorRef]]()
}

class LocalMediator extends Actor with ActorLogging {
  import LocalMediator._

  def subscriptionsFor(topic: String): Set[ActorRef] = {
    topicToSubscriptions.getOrElseUpdate(topic, Set[ActorRef]())
  }

  def receive: Receive = {
    case x @ Subscribe(topic, subscriptions) =>
      val subs = subscriptionsFor(topic)
      topicToSubscriptions(topic) = subs + subscriptions
      context.watch(subscriptions)
      sender() ! SubscribeAck(x)

    case Unsubscribe(topic, subscriptions) =>
      topicToSubscriptions.get(topic) match {
        case Some(xs) =>
          val subs = xs - subscriptions
          if (subs.isEmpty) {
            topicToSubscriptions -= topic
          } else {
            topicToSubscriptions(topic) = subs
          }

        case None =>
      }

    case Terminated(ref) =>
      var topicsToRemove = List[String]()
      for { (topic, xs) <- topicToSubscriptions } {
        val subs = xs - ref
        if (subs.isEmpty) {
          topicsToRemove ::= topic
        } else {
          topicToSubscriptions(topic) = subs
        }
      }
      topicToSubscriptions --= topicsToRemove

    case Publish(topic: String, msg: Any) =>
      topicToSubscriptions.get(topic) foreach { subs => subs foreach (_ ! msg) }
  }
}

