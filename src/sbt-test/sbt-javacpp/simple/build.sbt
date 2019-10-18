version := "0.1"

scalaVersion := "2.11.12"

javaCppClasses := Seq("javacpp.*")

javaCppVersion := "1.5.1"

javaCppCustomizer := { builder => builder.compilerOptions(Array("-std=c++11")) }

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8" % Test
