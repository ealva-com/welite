import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.application") version BuildPluginsVersion.AGP apply false
  id("com.android.library") version BuildPluginsVersion.AGP apply false
  kotlin("android") version BuildPluginsVersion.KOTLIN apply false
  id("io.gitlab.arturbosch.detekt") version BuildPluginsVersion.DETEKT
  id("org.jlleitschuh.gradle.ktlint") version BuildPluginsVersion.KTLINT
  id("com.github.ben-manes.versions") version BuildPluginsVersion.VERSIONS_PLUGIN
}

allprojects {
  group = PUBLISHING_GROUP
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
    plugin("org.jlleitschuh.gradle.ktlint")
  }

  ktlint {
    debug.set(false)
    version.set(Versions.KTLINT)
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)
    filter {
      exclude("**/generated/**")
      include("**/kotlin/**")
    }
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

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
  kotlinOptions.suppressWarnings = false
  kotlinOptions.verbose = true
  kotlinOptions.freeCompilerArgs += "-XXLanguage:+InlineClasses"
  kotlinOptions.freeCompilerArgs += "-Xinline-classes"
  kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.register("clean", Delete::class.java) {
  delete(rootProject.buildDir)
}

tasks.withType<DependencyUpdatesTask> {
  rejectVersionIf {
    isNonStable(candidate.version)
  }
}

fun isNonStable(version: String) = "^[0-9,.v-]+(-r)?$".toRegex().matches(version).not()

