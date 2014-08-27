package stats

object Packet {
  private[this] val Sep = "\n"
  private[this] val SepLen = Sep.length

  def bytes(lines: Iterable[String]) =
    lines.mkString(Sep).getBytes(Stats.charset)

  /** much like IterableLike#grouped but with statsd packet sematics.  */
  def grouped
   (max: Short)
   (metrics: Iterable[String]): Iterable[List[String]] = {
     val (packets, _) =
       ((List.empty[List[String]], 0) /: metrics) {
         case ((lines, total), str) =>
           val len = str.length
           lines match {
             case Nil =>
               if (len > max) (Nil, 0) // we can't actually send this line
               else ((str :: Nil) :: Nil, len + SepLen)
             case head :: tail =>
               val nextTotal = len + total
               if (nextTotal > max) (
                 (str :: Nil) :: head :: tail, len + SepLen
               )
               else ((str :: head) :: tail, nextTotal + SepLen)
           }
       }
     packets.reverse.map(_.reverse)
   }
}
