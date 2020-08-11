/*
 * Copyright 2020 Eric A. Snell
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

version = LibraryJavaTimeCoordinates.LIBRARY_VERSION

plugins {
  id("com.android.library")
  kotlin("android")
  id("kotlin-android-extensions")
  id("maven-publish")
}

android {
  compileSdkVersion(Sdk.COMPILE_SDK_VERSION)

  defaultConfig {
    minSdkVersion(Sdk.MIN_SDK_VERSION)
    targetSdkVersion(Sdk.TARGET_SDK_VERSION)

    versionCode = LibraryJavaTimeCoordinates.LIBRARY_VERSION_CODE
    versionName = LibraryJavaTimeCoordinates.LIBRARY_VERSION

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  lintOptions {
    isWarningsAsErrors = false
    isAbortOnError = true
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  packagingOptions {
    exclude("META-INF/*")
    exclude("META-INF/LICENSE.txt")
    exclude("META-INF/license.txt")
    exclude("META-INF/javolution*")
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  api(project(":library-android"))
  api(ThirdParty.COROUTINE_CORE)
  api(ThirdParty.COROUTINE_ANDROID)
//  api(TestingLib.JUNIT)
  api(AndroidTestingLib.ANDROIDX_TEST_CORE)
  api(AndroidTestingLib.ANDROIDX_TEST_RULES)
//  api(TestingLib.EXPECT)
//  api(TestingLib.ROBOLECTRIC)
  api(TestingLib.COROUTINE_TEST)
//  api(ThirdParty.EALVALOG)
//  api(ThirdParty.EALVALOG_CORE)
//  api(ThirdParty.EALVALOG_ANDROID)
}

afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"])
      }
    }
  }
}
