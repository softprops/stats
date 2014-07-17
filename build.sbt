organization := "me.lessis"

name := "stats"

version := "0.1.0-SNAPSHOT"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.4" % "test"

testOptions in Test += Tests.Cleanup { loader =>
  val c = loader.loadClass("stats.Cleanup$")
  c.getMethod("cleanup").invoke(c.getField("MODULE$").get(c))
}
