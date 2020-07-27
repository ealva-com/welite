
version = LibraryAndroidCoordinates.LIBRARY_VERSION

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

    versionCode = LibraryAndroidCoordinates.LIBRARY_VERSION_CODE
    versionName = LibraryAndroidCoordinates.LIBRARY_VERSION

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
    isWarningsAsErrors = true
    isAbortOnError = true
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  packagingOptions {
    exclude("META-INF/*")
  }

  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))

  implementation(SupportLibs.ANDROIDX_APPCOMPAT)
  implementation(SupportLibs.ANDROIDX_CORE_KTX)
  implementation(ThirdParty.EALVALOG)
  implementation(ThirdParty.FASTUTIL)
  implementation(ThirdParty.COROUTINE_CORE)
  implementation(ThirdParty.COROUTINE_ANDROID)

  // unsure exactly why receiving a warning for not including this testAnnotationProcessor
  testAnnotationProcessor("com.google.auto.service:auto-service:1.0-rc4")
  testImplementation(TestingLib.JUNIT)
  testImplementation(AndroidTestingLib.ANDROIDX_TEST_CORE)
  testImplementation(AndroidTestingLib.ANDROIDX_TEST_RULES)
  testImplementation(TestingLib.EXPECT)
  testImplementation(TestingLib.ROBOLECTRIC)
  testImplementation(TestingLib.COROUTINE_TEST)
  testImplementation(ThirdParty.EALVALOG)
  testImplementation(ThirdParty.EALVALOG_CORE)
  testImplementation(ThirdParty.EALVALOG_ANDROID)

  androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_RUNNER)
  androidTestImplementation(AndroidTestingLib.ANDROIDX_TEST_EXT_JUNIT)
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
