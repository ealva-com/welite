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

private const val IS_SNAPSHOT = false

object WeLiteCoreCoordinates {
  // All parts of versioning can be up to 2 digits: 0-99
  private const val MAJOR = 0
  private const val MINOR = 2
  private const val PATCH = 10
  private const val BUILD = 0

  val VERSION = "$MAJOR.$MINOR.$PATCH-${buildPart(IS_SNAPSHOT, BUILD)}"
}

object WeLiteJavaTimeCoordinates {
  // All parts of versioning can be up to 2 digits: 0-99
  private const val MAJOR = 0
  private const val MINOR = 2
  private const val PATCH = 10
  private const val BUILD = 0

  val VERSION = "$MAJOR.$MINOR.$PATCH-${buildPart(IS_SNAPSHOT, BUILD)}"
}

@Suppress("SameParameterValue")
private fun buildPart(isSnapshot: Boolean, build: Int): String =
  if (isSnapshot) "SNAPSHOT" else build.toString()
