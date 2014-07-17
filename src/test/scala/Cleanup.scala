package stats

trait Cleanup {
  Cleanup.dirties += this
  def cleanup()
}

object Cleanup {
  import scala.collection.mutable.{HashSet,SynchronizedSet}
  private val dirties = new HashSet[Cleanup] with SynchronizedSet[Cleanup]

  def cleanup() {
    try {
      dirties.foreach { _.cleanup() }
    } catch {
      case e: Exception =>
        println("Error on cleanup")
        e.printStackTrace
    }
  }
}


