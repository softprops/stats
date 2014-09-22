package stats

// port of Doug Lea's work that went into java7 (java.util.concurrent.ThreadLocalRandom) backported for java6
import scala.concurrent.forkjoin.ThreadLocalRandom

private[stats] trait Random {
  def nextDouble: Double
}

private[stats] object Random extends Random {
  def nextDouble = ThreadLocalRandom.current.nextDouble
}

