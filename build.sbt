organization := "me.lessis"

name := "stats"

version := "0.1.0-SNAPSHOT"

description := "a nonblocking statsd client"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.11.4" % "test"

licenses := Seq(
  ("MIT", url(s"https://github.com/softprops/${name.value}/blob/${version.value}/LICENSE")))

homepage := Some(url(s"https://github.com/softprops/${name.value}/#readme"))

crossScalaVersions := Seq("2.10.4", "2.11.1")

scalaVersion := crossScalaVersions.value.last

scalacOptions += Opts.compile.deprecation

seq(bintraySettings:_*)

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("email", "mail", "javamail")

seq(lsSettings:_*)

LsKeys.tags in LsKeys.lsync := (bintray.Keys.packageLabels in bintray.Keys.bintray).value

externalResolvers in LsKeys.lsync := (resolvers in bintray.Keys.bintray).value

testOptions in Test += Tests.Cleanup { loader =>
  val c = loader.loadClass("stats.Cleanup$")
  c.getMethod("cleanup").invoke(c.getField("MODULE$").get(c))
}


pomExtra := (
  <scm>
    <url>git@github.com:softprops/stats.git</url>
    <connection>scm:git:git@github.com:softprops/stats.git</connection>
  </scm>
  <developers>
    <developer>
      <id>softprops</id>
      <name>Doug Tangren</name>
      <url>https://github.com/softprops</url>
    </developer>
  </developers>)
