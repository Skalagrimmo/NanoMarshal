plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example.nanomarshal.core"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.compose.ui:ui")
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
}
