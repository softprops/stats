package stats

import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.ByteBuffer
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.control.NonFatal

case class Reciepts(
  address: InetSocketAddress)
 (implicit ec: ExecutionContext) {

  private[this] val svr = try {
    val chan = DatagramChannel.open()
    chan.socket.bind(address)
    Some(chan)
  } catch {
    case NonFatal(e) =>
      e.printStackTrace()
    None
  }

  def apply(): Future[String] =
    svr match {
      case Some(open) if open.isOpen =>
        Future {
          val buf = ByteBuffer.allocate(1024)
          buf.clear()
          open.receive(buf)
          new String(buf.array).trim()
        }
      case _ =>
        Promise.failed(new RuntimeException("invalid svr")).future
    }

  def close() = svr.foreach(_.close())
}
