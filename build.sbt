ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

enablePlugins(ScalaJSPlugin)

name         := "ps17-google-sheets"
scalaVersion := "2.13.1" // or any other Scala version >= 2.11.12

// This is an application with a main method
scalaJSUseMainModuleInitializer := true

val circeVersion = "0.14.1"
// CommonJS
scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }

lazy val root = (project in file("."))
  .settings(
    name                                    := "ps17-google-sheets-appscript",
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.9.0",
    libraryDependencies += "com.lihaoyi"   %%% "utest"     % "0.8.1" % "test",
    libraryDependencies += "com.lihaoyi" %%% "pprint" % "0.7.0",
    // libraryDependencies += "com.lihaoyi" %%% "fastparse" % "3.0.2",
    // libraryDependencies ++= Seq(
    //  "io.circe" %%% "circe-core",
    //  "io.circe" %%% "circe-generic",
    //  "io.circe" %%% "circe-parser"
    // ).map(_ % circeVersion)
    testFrameworks += new TestFramework("utest.runner.Framework")
  )
