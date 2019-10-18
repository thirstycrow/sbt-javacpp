package org.bytedeco.sbt.javacpp

/**
 * Created by Lloyd on 2/22/16.
 */
object Platform {

  private val platformOverridePropertyKey: String = "sbt.javacpp.platform"

  /**
   * To override, set the "sbt.javacpp.platform" System Property. Multiple platforms can be passed as a space-separated string
   *
   * @example
   * {{{
   * sbt compile -Dsbt.javacpp.platform="android-arm android-x86"
   * }}}
   */
  val current: Seq[String] = sys.props.get(platformOverridePropertyKey) match {
    case Some(platform) if platform.trim().nonEmpty => platform.split(' ')
    case _ =>
      val jvmName = System.getProperty("java.vm.name", "").toLowerCase
      var osName = System.getProperty("os.name", "").toLowerCase
      var osArch = System.getProperty("os.arch", "").toLowerCase
      val abiType = System.getProperty("sun.arch.abi", "").toLowerCase
      val libPath = System.getProperty("sun.boot.library.path", "").toLowerCase
      if (jvmName.startsWith("dalvik") && osName.startsWith("linux")) {
        osName = "android"
      } else if (jvmName.startsWith("robovm") && osName.startsWith("darwin")) {
        osName = "ios"
        osArch = "arm"
      } else if (osName.startsWith("mac os x") || osName.startsWith("darwin")) {
        osName = "macosx"
      } else {
        val spaceIndex = osName.indexOf(' ')
        if (spaceIndex > 0) osName = osName.substring(0, spaceIndex)
      }
      if (osArch == "i386" || osArch == "i486" || osArch == "i586" || osArch == "i686") osArch = "x86"
      else if (osArch == "amd64" || osArch == "x86-64" || osArch == "x64") osArch = "x86_64"
      else if (osArch.startsWith("aarch64") || osArch.startsWith("armv8") || osArch.startsWith("arm64")) osArch = "arm64"
      else if (osArch.startsWith("arm") && ((abiType == "gnueabihf") || libPath.contains("openjdk-armhf"))) osArch = "armhf"
      else if (osArch.startsWith("arm")) osArch = "arm"
      Seq(osName + "-" + osArch)
  }
}
