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

object SdkVersion {
  const val COMPILE = 30
  const val MIN = 21
  const val TARGET = 30
}

object PluginsVersion {
  const val AGP = "7.0.0-beta03"
  const val DETEKT = "1.17.1"
  const val DOKKA = "1.4.32"
  const val KOTLIN = "1.5.10"
  const val PUBLISH = "0.15.1"
  const val VERSIONS = "0.39.0"
}

object Libs {
  const val AGP = "com.android.tools.build:gradle:${PluginsVersion.AGP}"
  const val DESUGAR = "com.android.tools:desugar_jdk_libs:1.1.5"

  object Log {
    private const val VERSION = "0.5.6-SNAPSHOT"
    const val EALVALOG = "com.ealva:ealvalog:$VERSION"
    const val CORE = "com.ealva:ealvalog-core:$VERSION"
    const val ANDROID = "com.ealva:ealvalog-android:$VERSION"
  }

  object Kotlin {
    private const val VERSION = "1.4.32"
    const val KGP = "org.jetbrains.kotlin:kotlin-gradle-plugin:$VERSION"

    // const val STDLIB = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$VERSION"
    // const val EXTENSIONS = "org.jetbrains.kotlin:kotlin-android-extensions:$VERSION"
  }

  object Coroutines {
    private const val VERSION = "1.5.0"
    const val CORE = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$VERSION"
    const val ANDROID = "org.jetbrains.kotlinx:kotlinx-coroutines-android:$VERSION"
    const val TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$VERSION"
  }

  object Koin {
    private const val VERSION = "3.0.2"
    const val CORE = "io.insert-koin:koin-core:$VERSION"
    const val ANDROID = "io.insert-koin:koin-android:$VERSION"
  }

  object JUnit {
    private const val VERSION = "4.13.2"
    const val JUNIT = "junit:junit:$VERSION"
  }

  object AndroidX {
    const val APPCOMPAT = "androidx.appcompat:appcompat:1.3.0"
    const val PALETTE = "androidx.palette:palette:1.0.0"

    object Ktx {
      const val CORE = "androidx.core:core-ktx:1.6.0-alpha03"
    }

    object Activity {
      const val ACTIVITY_COMPOSE = "androidx.activity:activity-compose:1.3.0-alpha08"
    }

    object Constraint {
      const val LAYOUT = "com.android.support.constraint:constraint-layout:2.0.4"
    }

    object Compose {
      private const val VERSION = "1.0.0-beta07"
      const val FOUNDATION = "androidx.compose.foundation:foundation:$VERSION"
      const val UI = "androidx.compose.ui:ui:$VERSION"
      const val MATERIAL = "androidx.compose.material:material:$VERSION"
      const val TOOLING = "androidx.compose.ui:ui-tooling:$VERSION"

//      const val RUNTIME = "androidx.compose.runtime:runtime:$VERSION"
//      const val LAYOUT = "androidx.compose.foundation:foundation-layout:${VERSION}"
//      const val MATERIAL_ICONS_EXTENDED =
//        "androidx.compose.material:material-icons-extended:${VERSION}"
    }

    object Lifecycle {
      private const val VERSION = "2.3.1"
      const val RUNTIME_KTX = "androidx.lifecycle:lifecycle-runtime-ktx:2.4.0-alpha01"

//    const val VIEW_MODEL_COMPOSE = "androidx.lifecycle:lifecycle-viewmodel-compose:1.0.0-alpha05"
//    const val VIEW_MODEL_KTX = "androidx.lifecycle:lifecycle-viewmodel-ktx:$VERSION"
    }

    object Test {
      private const val VERSION = "1.4.0-alpha04"
      const val CORE = "androidx.test:core:$VERSION"
      const val RULES = "androidx.test:rules:$VERSION"
      const val RUNNER = "androidx.test:runner:$VERSION"

      object Ext {
        private const val VERSION = "1.1.3-alpha04"
        const val JUNIT = "androidx.test.ext:junit-ktx:$VERSION"
      }

      // const val ESPRESSO_CORE = "androidx.test.espresso:espresso-core:3.2.0"
    }
  }

  object Expect {
    const val EXPECT = "com.nhaarman:expect.kt:1.0.1"
  }

  object Fastutil {
    const val FASTUTIL = "it.unimi.dsi:fastutil:7.2.1"
  }

  object Robolectric {
    const val ROBOLECTRIC = "org.robolectric:robolectric:4.5.1"
  }
}

/*
  object Sdk {
    const val MIN_SDK_VERSION = 21
    const val TARGET_SDK_VERSION = 29
    const val COMPILE_SDK_VERSION = 29
  }

  object Versions {
    const val ANDROIDX_TEST = "1.3.0"
    const val ANDROIDX_TEST_EXT = "1.1.2"
    const val APPCOMPAT = "1.3.0"
    const val CONSTRAINT_LAYOUT = "2.0.4"
    const val CORE_KTX = "1.5.0"
    const val COROUTINES = "1.5.0"
    const val COROUTINES_TEST = "1.5.0"
    const val DESUGAR = "1.1.5"
    const val EALVALOG = "0.5.6-SNAPSHOT"
    const val ESPRESSO_CORE = "3.2.0"
    const val EXPECT = "1.0.1"
    const val FASTUTIL = "7.2.1"
    const val JUNIT = "4.13.2"
    const val KOIN = "2.2.2"
    const val KOTLIN = "1.4.30"
    const val LIFECYCLE = "2.3.1"
    const val ROBOLECTRIC = "4.4"
  }

  object PluginsVersion {
    const val AGP = "7.0.0-beta03"
    const val DETEKT = "1.17.1"
    const val DOKKA = "1.4.32"
    const val KOTLIN = "1.4.31"
    const val PUBLISH = "0.15.1"
    const val VERSIONS = "0.39.0"
  }

  object SupportLibs {
    const val ANDROIDX_APPCOMPAT =
      "androidx.appcompat:appcompat:${Versions.APPCOMPAT}"
    const val ANDROIDX_CONSTRAINT_LAYOUT =
      "com.android.support.constraint:constraint-layout:${Versions.CONSTRAINT_LAYOUT}"
    const val ANDROIDX_CORE_KTX = "androidx.core:core-ktx:${Versions.CORE_KTX}"
    const val ANDROIDX_LIFECYCLE_RUNTIME_KTX =
      "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.LIFECYCLE}"
  }

  object ThirdParty {
    const val EALVALOG = "com.ealva:ealvalog:${Versions.EALVALOG}"
    const val EALVALOG_CORE = "com.ealva:ealvalog-core:${Versions.EALVALOG}"
    const val EALVALOG_ANDROID = "com.ealva:ealvalog-android:${Versions.EALVALOG}"
    const val FASTUTIL = "it.unimi.dsi:fastutil:${Versions.FASTUTIL}"
    const val COROUTINE_CORE =
      "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}"
    const val COROUTINE_ANDROID =
      "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINES}"
    const val KOIN = "org.koin:koin-core:${Versions.KOIN}"
    const val KOIN_ANDROID = "org.koin:koin-android:${Versions.KOIN}"
  }

  object TestingLib {
    const val JUNIT = "junit:junit:${Versions.JUNIT}"
    const val ROBOLECTRIC = "org.robolectric:robolectric:${Versions.ROBOLECTRIC}"
    const val EXPECT = "com.nhaarman:expect.kt:${Versions.EXPECT}"
    const val COROUTINE_TEST =
      "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES_TEST}"
  //  const val KOIN_TEST = "org.koin:koin-test:${Versions.KOIN}"
  }

  object AndroidTestingLib {
    const val ANDROIDX_TEST_RULES = "androidx.test:rules:${Versions.ANDROIDX_TEST}"
    const val ANDROIDX_TEST_RUNNER = "androidx.test:runner:${Versions.ANDROIDX_TEST}"
    const val ANDROIDX_TEST_EXT_JUNIT = "androidx.test.ext:junit:${Versions.ANDROIDX_TEST_EXT}"
    const val ANDROIDX_TEST_CORE = "androidx.test:core:${Versions.ANDROIDX_TEST}"
    const val ESPRESSO_CORE = "androidx.test.espresso:espresso-core:${Versions.ESPRESSO_CORE}"
  }

  object ToolsLib {
    const val DESUGARING = "com.android.tools:desugar_jdk_libs:${Versions.DESUGAR}"
  }
*/
