import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "online.raylene.pocketpet"
    compileSdk = 34

    defaultConfig {
        applicationId = "online.raylene.pocketpet"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        val secrets = Properties().apply {
            val f = rootProject.file("secrets.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "DEFAULT_API_KEY", "\"${secrets.getProperty("ZAI_API_KEY", "")}\"")
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://api.z.ai/api/paas/v4/\"")
        buildConfigField("String", "DEFAULT_MODEL", "\"glm-4.5-flash\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
}
