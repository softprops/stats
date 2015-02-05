package stats

import org.scalacheck.Gen
import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object StatsSpec extends Properties("Stats") with Cleanup {

  val stats = Stats()
  val reciepts = Reciepts(stats.address)

  def cleanup() {
    reciepts.close()
    stats.close()
  }

  def await(f: Future[Boolean]) = Await.result(f, 1.second)

  property("counter#incrs") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    await(for {
      sent <- stats.counter(key).incr(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|c"
    })
  }

  property("counter#decrs") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    await(for {
      sent <- stats.counter(key).decr(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:-$i|c"
    })
  }

  property("set#records") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    await(for {
      sent <- stats.set[Int](key).record(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|s"
    })
  }

  property("gauge#records") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    await(for {
      sent <- stats.gauge[Int](key).record(i)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|g"
    })
  }


  property("time#records") = forAll(Gen.posNum[Int]) { (i: Int) =>
    val key = "test"
    await(for {
      sent <- stats.time(key).record(i.milliseconds)
      str  <- reciepts()
    } yield {
      sent && str == s"$key:$i|ms"
    })
  }

  property("multi#sends") = forAll(for {
    a <- Gen.posNum[Int]
    b <- Gen.posNum[Int]
    c <- Gen.posNum[Int]
    d <- Gen.posNum[Int]
  } yield (a,b,c,d)) {
    case (a: Int, b: Int, c: Int, d: Int) =>
      await(for {
        sent <- stats.multi(
          stats.counter("a")(a),
          stats.set[Int]("b").apply(b),
          stats.gauge[Int]("c").apply(c),
          stats.time("d")(d.milliseconds)
        )
        str  <- reciepts()
      } yield {
        sent && str == s"a:$a|c\nb:$b|s\nc:$c|g\nd:$d|ms"
      })
  }
}
