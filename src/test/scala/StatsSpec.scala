package stats

import org.scalacheck.Gen
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object StatsSpec extends Properties("Stats") with Cleanup {

  val stats = Stats()
  val reciepts = Reciepts(stats.address)

  def cleanup() {
    reciepts.close()
    stats.close()
  }

  property("counter#incrs") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    Await.result(for {
      sent <- stats.counter(key).incr(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|c"
    }, 1.second)
  }

  property("counter#decrs") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    Await.result(for {
      sent <- stats.counter(key).decr(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:-$i|c"
    }, 1.second)
  }

  property("set#adds") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    Await.result(for {
      sent <- stats.set[Int](key).add(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|s"
    }, 1.second)
  }

  property("gauge#adds") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    Await.result(for {
      sent <- stats.gauge[Int](key).add(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|g"
    }, 1.second)
  }

  
}
