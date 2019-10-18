package org.bytedeco.sbt.javacpp

import java.net.URLClassLoader
import java.util.Properties

import sbt.Keys._
import sbt._

import scala.language.{ postfixOps, reflectiveCalls }
import scala.util.Try

object JavaCppPlugin extends AutoPlugin {

  override def projectSettings: Seq[Setting[_]] = {
    import autoImport._
    Seq(
      autoCompilerPlugins := true,
      javaCppClasses := Seq.empty,
      javaCppCustomizer := identity,
      javaCppPlatform := Platform.current,
      javaCppPresetLibs := Seq.empty,
      javaCppVersion := Versions.javaCppVersion,
      libraryDependencies += {
        "org.bytedeco" % "javacpp" % javaCppVersion.value jar
      },
      javaCppPresetDependencies,
      javaCppBuild := javaCpp.value,
      products in Compile := (products in Compile).dependsOn(javaCppBuild).value)
  }

  object Versions {
    val javaCppVersion = "1.4.3"
  }

  object autoImport {
    type JavaCppBuilder = {
      def classPaths(classPath: String): this.type
      def classPaths(classPath: Array[String]): this.type
      def encoding(encoding: String): this.type
      def outputDirectory(outputDirectory: String): this.type
      def outputDirectory(outputDirectory: File): this.type
      def clean(clean: Boolean): this.type
      def generate(generate: Boolean): this.type
      def compile(compile: Boolean): this.type
      def deleteJniFiles(deleteJniFiles: Boolean): this.type
      def header(header: Boolean): this.type
      def copyLibs(copyLibs: Boolean): this.type
      def copyResources(copyResources: Boolean): this.type
      def outputName(outputName: String): this.type
      def jarPrefix(jarPrefix: String): this.type
      def properties(platform: String): this.type
      def properties(properties: Properties): this.type
      def propertyFile(propertyFile: String): this.type
      def propertyFile(propertyFile: File): this.type
      def property(keyValue: String): this.type
      def property(key: String, value: String): this.type
      def classesOrPackages(classes: Array[String]): this.type
      def buildCommand(buildCommand: Array[String]): this.type
      def workingDirectory(workingDirectory: String): this.type
      def workingDirectory(workingDirectory: File): this.type
      def environmentVariables(environmentVariables: java.util.Map[String, String]): this.type
      def compilerOptions(options: Array[String]): this.type
      def build(): Array[File]
      def printHelp(): Unit
      def getClass(): Class[_]
    }

    val javaCppBuild = TaskKey[Seq[File]]("javaCppBuild", "Build Java CPP products")
    val javaCppClasses = SettingKey[Seq[String]]("javaCppClasses", "A list of Java CPP classes. Suffix of '.*' ('.**') can be used to match all classes under the specified package (and any subpackages)")
    val javaCppCustomizer = SettingKey[JavaCppBuilder => JavaCppBuilder]("javaCppCustomization", "Customize the Java CPP builder")
    val javaCppPlatform = SettingKey[Seq[String]]("javaCppPlatform", """The platform that you want to compile for (defaults to the platform of the current computer). You can also set this via the "sbt.javacpp.platform" System Property """)
    val javaCppPresetLibs = SettingKey[Seq[(String, String)]]("javaCppPresetLibs", "List of additional JavaCPP presets that you would wish to bind lazily, defaults to an empty list")
    val javaCppVersion = SettingKey[String]("javaCppVersion", s"Version of Java CPP that you want to use, defaults to ${Versions.javaCppVersion}")
  }

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  private def javaCppPresetDependencies: Def.Setting[Seq[ModuleID]] = {
    import autoImport._
    libraryDependencies ++= {
      lazy val cppPresetVersion = buildPresetVersion(javaCppVersion.value)
      javaCppPresetLibs.value.flatMap {
        case (libName, libVersion) =>
          val generic = "org.bytedeco.javacpp-presets" % libName % s"$libVersion-$cppPresetVersion" classifier ""
          val platformSpecific = javaCppPlatform.value.map { platform =>
            "org.bytedeco.javacpp-presets" % libName % s"$libVersion-$cppPresetVersion" classifier platform
          }
          generic +: platformSpecific
      }
    }
  }

  /**
   * Before javacpp 1.4
   * Given a version string, simply drops the patch level and returns the major-minor version only
   *
   * Starting from javacpp 1.4
   * The version number of the presets are equal to the javacpp version.
   *
   * @param version eg. "1.4.2"
   */
  private def buildPresetVersion(version: String): String =
    version match {
      case VersionSplit(a :: b :: _) if a < 2 & b < 4 => s"$a.$b"
      case VersionSplit(_) => version
      case _ => throw new IllegalArgumentException("Version format not recognized")
    }

  private object VersionSplit {
    def unapply(arg: String): Option[List[Int]] =
      Try(arg.split('.').map(_.toInt).toList).toOption
  }

  private def javaCpp = Def.task {
    import autoImport._
    val classes = javaCppClasses.value
    val customizer = javaCppCustomizer.value
    val dependencies = (dependencyClasspath in Compile).value
    val output = (classDirectory in Compile).value
    val _ = (compile in Compile).value

    val cl = new URLClassLoader(dependencies.map(_.data.toURI.toURL).toArray, null)
    val thread = Thread.currentThread()
    thread.setContextClassLoader(cl)
    val builder = cl.loadClass("org.bytedeco.javacpp.tools.Builder").newInstance().asInstanceOf[JavaCppBuilder]
    val saved = thread.getContextClassLoader

    try {
      customizer(
        builder
          .classPaths(output.getAbsolutePath)
          .classPaths(dependencies.map(_.data.absolutePath).toArray)
          .classesOrPackages(classes.toArray)).build()
    } finally {
      thread.setContextClassLoader(saved)
    }
  }
}
