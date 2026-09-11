plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "io.github.playfoundryhq.adaptiveflow"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.github.playfoundryhq.adaptiveflow"
    minSdk = 24
    targetSdk = 36
    versionCode = 19
    versionName = "0.8.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing is wired up ONLY when CI injects the keystore via environment
  // variables (see .github/workflows/ci.yml). Local and CI debug builds use
  // Android's automatic debug signing — no keystore file needs to exist.
  signingConfigs {
    create("release") {
      System.getenv("KEYSTORE_FILE")?.let { keystoreFile ->
        storeFile = file(keystoreFile)
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      // default debug signing
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (System.getenv("KEYSTORE_FILE") != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    jvmToolchain(17)
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  packaging {
    resources {
      excludes += "META-INF/DEPENDENCIES"
      excludes += "META-INF/LICENSE*"
      excludes += "META-INF/NOTICE*"
      excludes += "META-INF/*.kotlin_module"
      excludes += "META-INF/versions/**"
    }
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all { it.systemProperty("robolectric.graphicsMode", "NATIVE") }
    }
  }

  lint {
    // CI's `verify` job already runs `lintDebug` on every PR/push. Re-running
    // the whole analysis as `lintVitalRelease` during `assembleRelease` just
    // slows the release job down for no extra coverage.
    checkReleaseBuilds = false
  }
}

ksp {
  arg("room.schemaLocation", "${projectDir}/schemas")
  arg("room.generateKotlin", "true")
}

// Visual-regression reference images live in the repo. Plain `testDebugUnitTest`
// (the CI gate) still executes the screenshot tests — rendering each composable
// so a crash or exception fails the build — but only `recordRoborazziDebug` /
// `verifyRoborazziDebug` write or diff the PNGs, keeping CI free of font-hinting
// flake.
roborazzi {
  outputDir.set(file("src/test/screenshots"))
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Persistence
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)

  // Networking (Gemini + DeepSeek REST)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.logging.interceptor)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // PDF text extraction (Apache-2.0 — replaces AGPL iText)
  implementation(libs.pdfbox.android)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
