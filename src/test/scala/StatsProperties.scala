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


  property("time#adds") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    Await.result(for {
      sent <- stats.time(key).add(i.milliseconds)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|ms"
    }, 1.second)
  }

  property("multi#sends") = forAll(for {
    a <- Gen.posNum[Int]
    b <- Gen.posNum[Int]
    c <- Gen.posNum[Int]
    d <- Gen.posNum[Int]
  } yield (a,b,c,d)) {
    case (a: Int, b: Int, c: Int, d: Int) =>
      Await.result(for {
        sent <- stats.multi(
          stats.counter("a")(a),
          stats.set[Int]("b").apply(b),
          stats.gauge[Int]("c").apply(c),
          stats.time("d")(d.milliseconds)
        )
        str  <- reciepts()
      } yield {
        sent && str == s"a:$a|c\nb:$b|s\nc:$c|g\nd:$d|ms"
      }, 1.second)
  }
}
