package stats

import java.net.{ InetAddress, InetSocketAddress }
import java.nio.channels.DatagramChannel
import java.nio.ByteBuffer
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Gen
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.control.NonFatal

object StatsSpec extends Properties("Stats") with Cleanup {

  val reciepts = Reciepts(8125)
  val stats = Stats()

  def cleanup() {
    reciepts.close()
    stats.close()
  }

  property("incrs") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    Await.result(for {
      sent <- stats.counter(key).incr(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|c"
    }, 3.seconds)
  }

  case class Reciepts(port: Int) {
    val addr = new InetSocketAddress(
        InetAddress.getByName("localhost"), port)
    private[this] val svr =
    try {
      val chan = DatagramChannel.open()
      chan.socket.bind(addr)
      Some(chan)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        None
    }

    def apply(): Future[String] =
      svr match {
        case Some(open) =>
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
}
