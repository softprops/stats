package stats

// validMetricChars - https://github.com/graphite-project/graphite-web/blob/master/webapp/graphite/render/grammar.py#L78
// printables - https://github.com/greghaskins/pyparsing/blob/master/src/pyparsing.py#L166

object Keys {
  val Separator = "."
  def format(name: Iterable[String]) = name.mkString(Separator)
}
