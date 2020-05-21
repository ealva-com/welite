val PUBLISHING_GROUP = "com.ealva"

object AppCoordinates {
    const val APP_ID = "com.ealva.welite"

    const val APP_VERSION_NAME = "1.0.0"
    const val APP_VERSION_CODE = 1
}

object LibraryAndroidCoordinates {
  // All parts of versioning can be up to 2 digits: 0-99
  private const val versionMajor = 0
  private const val versionMinor = 0
  private const val versionPatch = 1
  private const val versionBuild = 0

  const val LIBRARY_VERSION_CODE = versionMajor * 1000000 + versionMinor * 10000 + versionPatch * 100 + versionBuild
  const val LIBRARY_VERSION = "${versionMajor}.${versionMinor}.${versionPatch}-${versionBuild}"
}

