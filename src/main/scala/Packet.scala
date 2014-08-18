package stats

object Packet {
  /** much like IterableLike#grouped with with statsd packet sematics */
  def grouped
   (max: Short)
   (metrics: Iterable[String]): Iterable[List[String]] = {
     val (packets, _) = ((List.empty[List[String]], 0) /: metrics) {
       case ((lines, total), str) =>
         val len = str.length + 1
         val nextLen = total + len
         val overflow = nextLen > max
         // the general algorithm below follows the following pattern
         // * compute the length to allocate a line (with newline char)
         // * carry over running packet length
         // * keep track of whether or not we overlow a given 
         // max packet size
         // * if we haven't gone over the max, prepend the metric to 
         //   the head list, accumulating the running packet len counter
         // * if we have gone over the max, prepend a new list containing
         //   only the current metric to the list of lists, resetting the
         //   packet length counter to that of the current metric's len
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
