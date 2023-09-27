ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

enablePlugins(ScalaJSPlugin)

name         := "ps17-google-sheets"
scalaVersion := "2.13.1" // or any other Scala version >= 2.11.12

// This is an application with a main method
scalaJSUseMainModuleInitializer := true

lazy val root = (project in file("."))
  .settings(
    name := "ps17-google-sheets-appscript"
  )
