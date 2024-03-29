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

version = WeLiteJavaTimeCoordinates.VERSION

plugins {
  id("com.android.library")
  kotlin("android")
//  kotlin("plugin.serialization")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
}

android {
  compileSdk = SdkVersion.COMPILE

  defaultConfig {
    minSdk = SdkVersion.MIN
    targetSdk = SdkVersion.TARGET

    version = WeLiteJavaTimeCoordinates.VERSION

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  buildTypes {
    debug {
      isTestCoverageEnabled = false
    }

    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  lint {
    warningsAsErrors = false
    abortOnError = true
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  kotlinOptions {
    jvmTarget = "1.8"
    languageVersion = "1.6"
    apiVersion = "1.6"
    suppressWarnings = false
    verbose = true
    freeCompilerArgs = listOf(
      "-Xopt-in=kotlin.RequiresOptIn",
      "-Xexplicit-api=warning"
    )
  }
}

dependencies {
  coreLibraryDesugaring(Libs.DESUGAR)
  implementation(kotlin("stdlib-jdk8"))
  implementation(project(":welite-core"))
  implementation(Libs.AndroidX.Ktx.CORE)
  implementation(Libs.Log.EALVALOG)
  implementation(Libs.Log.CORE)
  implementation(Libs.Coroutines.CORE)
  implementation(Libs.Coroutines.ANDROID)

  testImplementation(Libs.JUnit.JUNIT)
  testImplementation(Libs.AndroidX.Test.CORE) {
    exclude("junit", "junit")
  }
  testImplementation(Libs.AndroidX.Test.RULES) {
    exclude("junit", "junit")
  }
  testImplementation(Libs.Expect.EXPECT)
  testImplementation(Libs.Robolectric.ROBOLECTRIC)
  testImplementation(Libs.Coroutines.TEST)

  androidTestImplementation(Libs.AndroidX.Test.RUNNER) {
    exclude("junit", "junit")
  }
  androidTestImplementation(Libs.AndroidX.Test.Ext.JUNIT) {
    exclude("junit", "junit")
  }
  androidTestImplementation(Libs.JUnit.JUNIT)
  androidTestImplementation(Libs.Expect.EXPECT)
  androidTestImplementation(Libs.Coroutines.TEST)
}
