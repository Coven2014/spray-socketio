package spray.contrib.socketio.packet

import spray.json.JsArray
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue

object Packet {
  val reservedEvents = Set(
    "message",
    "connect",
    "disconnect",
    "open",
    "close",
    "error",
    "retry",
    "reconnect")
}

sealed trait Packet {
  def code: Byte
  def id: Long
  def endpoint: String
  def isAckRequested: Boolean

  override def toString = PacketRender.render(this).utf8String
}

/**
 * Signals disconnection. If no endpoint is specified, disconnects the entire socket.
 */
final case class DisconnectPacket(endpoint: String) extends Packet {
  def code = '0'
  def id = -1L
  def isAckRequested = false
}

/**
 * Only used for multiple sockets. Signals a connection to the endpoint. Once the
 * server receives it, it's echoed back to the client.
 */
final case class ConnectPacket(endpoint: String = "", args: Seq[(String, String)] = Nil) extends Packet {
  def code = '1'
  def id = -1L
  def isAckRequested = false
}

/**
 * Sends a heartbeat. Heartbeats must be sent within the interval negotiated with
 * the server. It's up to the client to decide the padding (for example, if the
 * heartbeat timeout negotiated with the server is 20s, the client might want to
 * send a heartbeat evert 15s).
 */
case object HeartbeatPacket extends Packet {
  def code = '2'
  def id = -1L
  def endpoint = ""
  def isAckRequested = false
}

/**
 * A regular message.
 */
final case class MessagePacket(id: Long, isAckRequested: Boolean, endpoint: String, data: String) extends Packet {
  def code = '3'
}

/**
 * A JSON encoded message.
 */
final case class JsonPacket(id: Long, isAckRequested: Boolean, endpoint: String, json: JsValue) extends Packet {
  def code = '4'
}

/**
 * An event is like a json message, but has mandatory name and args fields. name
 * is a string and args an array.
 */
object EventPacket {
  def apply(id: Long, isAckRequested: Boolean, endpoint: String, json: JsValue): EventPacket = json match {
    case JsObject(fields) =>
      val name = fields.get("name") match {
        case Some(JsString(value)) => value
        case _                     => throw new Exception("Event packet is must have name field.")
      }
      val args = fields.get("args") match {
        case Some(JsArray(xs)) => xs
        case _                 => List()
      }
      apply(id, isAckRequested, endpoint, name, args)
    case _ => throw new Exception("Event packet is not a Json object.")
  }

  def apply(id: Long, isAckRequested: Boolean, endpoint: String, name: String, args: List[JsValue]): EventPacket =
    new EventPacket(id, isAckRequested, endpoint, name, args)

  def unapply(x: EventPacket): Option[(String, List[JsValue])] = Some(x.name, x.args)
}
final class EventPacket private (val id: Long, val isAckRequested: Boolean, val endpoint: String, val name: String, val args: List[JsValue]) extends Packet {
  def code = '5'
  def json = JsObject(Map("name" -> JsString(name), "args" -> JsArray(args)))
}

/**
 * An acknowledgment contains the message id as the message data. If a + sign
 * follows the message id, it's treated as an event message packet.
 */
final case class AckPacket(ackId: Long, args: String) extends Packet {
  def code = '6'
  def id = -1L
  def endpoint = ""
  def isAckRequested = false
}

final case class ErrorPacket(endpoint: String, reason: String, advice: String = "") extends Packet {
  def code = '7'
  def id = -1L
  def isAckRequested = false
}

/**
 * No operation. Used for example to close a poll after the polling duration times out.
 */
case object NoopPacket extends Packet {
  def code = '8'
  def id = -1L
  def endpoint = ""
  def isAckRequested = false
}

