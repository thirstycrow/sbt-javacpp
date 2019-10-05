package org.bytedeco.sbt.javacpp

import org.bytedeco.javacpp.tools.Builder
import sbt.Keys._
import sbt._

import scala.language.postfixOps
import scala.util.Try

object JavaCppPlugin extends AutoPlugin {

  override def projectSettings: Seq[Setting[_]] = {
    import autoImport._
    Seq(
      autoCompilerPlugins := true,
      javaCppPlatform := Platform.current,
      javaCppPresetLibs := Seq.empty,
      libraryDependencies += {
        "org.bytedeco" % "javacpp" % Versions.javaCppVersion jar
      },
      javaCppPresetDependencies,
      javaCppBuild := javaCpp.value,
      products in Compile := (products in Compile).dependsOn(javaCppBuild).value)
  }

  object Versions {
    val javaCppVersion = {
      val javaCppJar = classOf[Builder].getProtectionDomain.getCodeSource.getLocation.getFile
      "(?<=javacpp-)(.*)(?=\\.jar)".r.findFirstIn(javaCppJar).get
    }
  }

  object autoImport {
    val javaCppPlatform = SettingKey[Seq[String]]("javaCppPlatform", """The platform that you want to compile for (defaults to the platform of the current computer). You can also set this via the "sbt.javacpp.platform" System Property """)
    val javaCppPresetLibs = SettingKey[Seq[(String, String)]]("javaCppPresetLibs", "List of additional JavaCPP presets that you would wish to bind lazily, defaults to an empty list")
    val javaCppClasses = SettingKey[Seq[String]]("javaCppClasses", "A list of Java CPP classes. Suffix of '.*' ('.**') can be used to match all classes under the specified package (and any subpackages)")
    val javaCppBuild = TaskKey[Seq[File]]("javaCppBuild", "Build Java CPP products")
  }

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  private def javaCppPresetDependencies: Def.Setting[Seq[ModuleID]] = {
    import autoImport._
    libraryDependencies ++= {
      lazy val cppPresetVersion = buildPresetVersion(Versions.javaCppVersion)
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
    val dependencies = (dependencyClasspath in Compile).value
    val output = (classDirectory in Compile).value
    val _ = (compile in Compile).value

    val thread = Thread.currentThread()
    val saved = thread.getContextClassLoader
    thread.setContextClassLoader(classOf[Builder].getClassLoader)
    try {
      new Builder()
        .classPaths(output.getAbsolutePath)
        .classPaths(dependencies.map(_.data.absolutePath): _*)
        .classesOrPackages(classes: _*)
        .build()
    } finally {
      thread.setContextClassLoader(saved)
    }
  }
}
