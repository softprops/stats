package stats

import java.nio.channels.DatagramChannel
import scala.concurrent.{ ExecutionContext, Future }
import java.net.InetSocketAddress
import java.nio.ByteBuffer

trait Transport {
  def send(stats: Seq[Stat]): Future[Boolean]
  def close()
}

object Transport {

  object None extends Transport {
    def send(seq: Seq[Stat]) =
      Future.successful(true)
    def close() = ()
  }

  def none(addr: InetSocketAddress, packetMax: Short, ec: ExecutionContext) =
    None

  def datagram(addr: InetSocketAddress, packetMax: Short, ec: ExecutionContext) =
    Datagram(addr, packetMax)(ec)

  case class Datagram
   (address: InetSocketAddress, packetMax: Short)
   (implicit ec: ExecutionContext) extends Transport {
    private[this] lazy val channel = DatagramChannel.open()

    def close() = channel.close()

    def send(xs: Seq[Stat]) =
      Future {
        val packets = Packet.grouped(packetMax)(xs.map(_.str))
        val (sent, expected) = ((0,0) /: packets) {
          case ((s,e), packet) =>
            val bytes = Packet.bytes(packet)
            val sent = channel.send(ByteBuffer.wrap(bytes), address)
            (s + sent, e + bytes.length)
        }
        sent == expected
      }
  }
}
