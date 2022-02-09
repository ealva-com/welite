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
  const val COMPILE = 31
  const val MIN = 21
  const val TARGET = 30
}

object PluginsVersion {
  const val AGP = "7.1.1"
  const val DETEKT = "1.19.0"
  const val DOKKA = "1.6.10"
  const val KOTLIN = "1.6.10"
  const val PUBLISH = "0.18.0"
  const val SERIALIZATION = "1.6.10"
  const val VERSIONS = "0.42.0"
}

object Libs {
  const val AGP = "com.android.tools.build:gradle:${PluginsVersion.AGP}"
  const val DESUGAR = "com.android.tools:desugar_jdk_libs:1.1.5"

  object AndroidX {
    const val APPCOMPAT = "androidx.appcompat:appcompat:1.4.1"

    object Ktx {
      const val CORE = "androidx.core:core-ktx:1.7.0"
    }

    object Constraint {
      const val LAYOUT = "com.android.support.constraint:constraint-layout:2.0.4"
    }

    object Lifecycle {
      private const val VERSION = "2.4.0"
      const val RUNTIME_KTX = "androidx.lifecycle:lifecycle-runtime-ktx:$VERSION"
    }

    object Test {
      private const val VERSION = "1.4.0"
      const val CORE = "androidx.test:core:$VERSION"
      const val RULES = "androidx.test:rules:$VERSION"
      const val RUNNER = "androidx.test:runner:$VERSION"

      object Ext {
        private const val VERSION = "1.1.3"
        const val JUNIT = "androidx.test.ext:junit-ktx:$VERSION"
      }
    }
  }

  object Coroutines {
    private const val VERSION = "1.6.0"
    const val CORE = "org.jetbrains.kotlinx:kotlinx-coroutines-core:$VERSION"
    const val ANDROID = "org.jetbrains.kotlinx:kotlinx-coroutines-android:$VERSION"
    const val TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test:$VERSION"
  }

  object Expect {
    const val EXPECT = "com.nhaarman:expect.kt:1.0.1"
  }

  object Fastutil {
    const val FASTUTIL = "it.unimi.dsi:fastutil:7.2.1"
  }

  object Koin {
    private const val VERSION = "3.1.5"
    const val CORE = "io.insert-koin:koin-core:$VERSION"
    const val ANDROID = "io.insert-koin:koin-android:$VERSION"
  }

  object Kotlin {
    private const val VERSION = "1.5.31"
    const val KGP = "org.jetbrains.kotlin:kotlin-gradle-plugin:$VERSION"

    object Serialization {
      private const val VERSION = "1.3.2"
      const val CORE = "org.jetbrains.kotlinx:kotlinx-serialization-core:$VERSION"
      const val JSON = "org.jetbrains.kotlinx:kotlinx-serialization-json:$VERSION"
    }
  }

  object JUnit {
    private const val VERSION = "4.13.2"
    const val JUNIT = "junit:junit:$VERSION"
  }

  object Log {
    private const val VERSION = "0.5.6-SNAPSHOT"
    const val EALVALOG = "com.ealva:ealvalog:$VERSION"
    const val CORE = "com.ealva:ealvalog-core:$VERSION"
    const val ANDROID = "com.ealva:ealvalog-android:$VERSION"
  }

  object Result {
    private const val VERSION = "1.1.14"
    const val RESULT = "com.michael-bull.kotlin-result:kotlin-result:$VERSION"
    const val COROUTINES = "com.michael-bull.kotlin-result:kotlin-result-coroutines:$VERSION"
  }

  object Robolectric {
    const val ROBOLECTRIC = "org.robolectric:robolectric:4.7.3"
  }
}
