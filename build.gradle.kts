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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
  id("com.android.application") version BuildPluginsVersion.AGP apply false
  id("com.android.library") version BuildPluginsVersion.AGP apply false
  kotlin("android") version BuildPluginsVersion.KOTLIN apply false
  id("io.gitlab.arturbosch.detekt") version BuildPluginsVersion.DETEKT
  id("com.github.ben-manes.versions") version BuildPluginsVersion.VERSIONS
  id("org.jetbrains.dokka") version BuildPluginsVersion.DOKKA
  id("com.vanniktech.maven.publish") version BuildPluginsVersion.VANNIKTECH_PUBLISH
}

allprojects {
  repositories {
    google()
    mavenCentral()
    jcenter()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
  }
}

subprojects {
  apply {
    plugin("io.gitlab.arturbosch.detekt")
  }

  detekt {
    config = rootProject.files("config/detekt/detekt.yml")
    reports {
      html {
        enabled = true
        destination = file("build/reports/detekt.html")
      }
    }
  }
}

buildscript {
  dependencies {
    classpath("com.android.tools.build:gradle:7.0.0-alpha09")
  }
}

tasks.withType<DependencyUpdatesTask> {
  rejectVersionIf {
    isNonStable(candidate.version)
  }
  checkForGradleUpdate = true
}

fun isNonStable(version: String) = "^[0-9,.v-]+(-r)?$".toRegex().matches(version).not()
