/*
 * Copyright 2020 eAlva.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

object AppCoordinates {
  const val APP_ID = "com.ealva.welite"

  const val APP_VERSION_NAME = "1.0.0"
  const val APP_VERSION_CODE = 1
}

object WeLiteCoreCoordinates {
  // All parts of versioning can be up to 2 digits: 0-99
  private const val versionMajor = 0
  private const val versionMinor = 0
  private const val versionPatch = 19
  private const val versionBuild = 0

  const val LIBRARY_VERSION_CODE = versionMajor * 1000000 + versionMinor * 10000 +
    versionPatch * 100 + versionBuild
  const val LIBRARY_VERSION = "$versionMajor.$versionMinor.$versionPatch-SNAPSHOT"
}

object WeLiteJavaTimeCoordinates {
  // All parts of versioning can be up to 2 digits: 0-99
  private const val versionMajor = 0
  private const val versionMinor = 0
  private const val versionPatch = 19
  private const val versionBuild = 0

  const val LIBRARY_VERSION_CODE = versionMajor * 1000000 + versionMinor * 10000 +
    versionPatch * 100 + versionBuild
  const val LIBRARY_VERSION = "$versionMajor.$versionMinor.$versionPatch-SNAPSHOT"
}
