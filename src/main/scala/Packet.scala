package stats

object Packet {
  /** much like IterableLike#grouped but with statsd packet sematics */
  def grouped
   (max: Short)
   (metrics: Iterable[String]): Iterable[List[String]] = {
     val (packets, _) = ((List.empty[List[String]], 0) /: metrics) {
       case ((lines, total), str) =>
         val len = str.length + 1
         val nextLen = total + len
         val overflow = nextLen > max
         lines match {
           case Nil =>
             if (overflow) (Nil, 0) // we can't actually send this line
             else ((str :: Nil) :: Nil, nextLen)
           case head :: Nil =>
             if (overflow) ((str :: Nil) :: head :: Nil, len)
             else ((str :: head) :: Nil, nextLen)
           case head :: prev :: rest =>
             if (overflow) ((str :: Nil) :: head :: prev :: rest, len)
             else ((str :: head) :: prev :: rest, nextLen)
         }
     }
     packets
   }
}
