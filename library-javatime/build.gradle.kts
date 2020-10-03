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
    coreLibraryDesugaringEnabled = true
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
  implementation(kotlin("stdlib-jdk8"))
  implementation(project(":library-android"))
  coreLibraryDesugaring(ToolsLib.DESUGARING)
  implementation(SupportLibs.ANDROIDX_CORE_KTX)
  implementation(ThirdParty.EALVALOG)
  implementation(ThirdParty.FASTUTIL)
  implementation(ThirdParty.COROUTINE_CORE)
  implementation(ThirdParty.COROUTINE_ANDROID)

  testImplementation(TestingLib.JUNIT)
  testImplementation(AndroidTestingLib.ANDROIDX_TEST_CORE)
  testImplementation(AndroidTestingLib.ANDROIDX_TEST_RULES)
  testImplementation(TestingLib.EXPECT)
  testImplementation(TestingLib.ROBOLECTRIC)
  testImplementation(TestingLib.COROUTINE_TEST)
  testImplementation(ThirdParty.EALVALOG)
  testImplementation(ThirdParty.EALVALOG_CORE)
  testImplementation(ThirdParty.EALVALOG_ANDROID)

  androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_RUNNER) {
    exclude("junit", "junit")
  }
  androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_EXT_JUNIT) {
    exclude("junit", "junit")
  }
  androidTestImplementation(TestingLib.JUNIT)
  androidTestImplementation(TestingLib.EXPECT)
  androidTestImplementation(TestingLib.COROUTINE_TEST)
  androidTestImplementation(ThirdParty.EALVALOG)
  androidTestImplementation(ThirdParty.EALVALOG_CORE)
  androidTestImplementation(ThirdParty.EALVALOG_ANDROID)
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
