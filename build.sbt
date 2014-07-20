organization := "me.lessis"

name := "stats"

version := "0.1.0-SNAPSHOT"

description := "a nonblocking statsd client"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.4" % "test"

crossScalaVersions := Seq("2.10.4", "2.11.1")

scalaVersion := crossScalaVersions.value.last

scalacOptions += Opts.compile.deprecation

testOptions in Test += Tests.Cleanup { loader =>
  val c = loader.loadClass("stats.Cleanup$")
  c.getMethod("cleanup").invoke(c.getField("MODULE$").get(c))
}
