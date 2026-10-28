import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "org.y20k.transistor"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.y20k.transistor"
        minSdk = 25
        targetSdk = 37
        // GitHub Actions builds get a unique, increasing version so every APK installs as an update
        // and the build number is visible in Settings > Apps
        val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 117 * 10000 + ciRunNumber
        versionName = if (ciRunNumber > 0) "4.3.10-wrd.$ciRunNumber" else "4.3.10-wrd"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    androidResources {
        // Languages listed here must also be listed in
        // /app/src/main/res/xml/locales_config.xml — the two lists are synced by hand.
        localeFilters += listOf("en", "ar", "ast", "bg", "ca", "ckb", "cs", "cy", "de", "el", "eo", "es", "et", "eu", "fi", "fr", "fy", "ga", "gl", "hi", "hr", "hu", "ia", "id", "it", "iw", "ja", "ko", "nb-rNO", "nl", "pl", "pt", "pt-rBR", "ro", "ru", "sk", "sl", "sr", "ta", "tr", "uk", "zh-rCN")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true // required - do not remove: since AGP 8 BuildConfig is opt-in, Keys.USER_AGENT and SettingsFragment need BuildConfig.VERSION_NAME
    }

    signingConfigs {
        getByName("debug") {
            // fixed debug key (committed on purpose) so APKs built on GitHub Actions can be installed over each other
            storeFile = file("ci-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "@string/app_name_debug"
        }
    }


    lint {
        disable += "MissingTranslation"
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.kotlin.stdlib.jdk8)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.material)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.media)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.gson)
    implementation(libs.volley)

    implementation(libs.glide)

}
