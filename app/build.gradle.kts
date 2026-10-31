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
        // WebRadioDroid's own application id: installs next to the original Transistor without conflicts
        applicationId = "io.github.thamichele.webradiodroid"
        minSdk = 25
        targetSdk = 37
        // WebRadioDroid's own version (based on Transistor 4.3.10): MAJOR.MINOR.PATCH
        // versionCode starts at 2000000 to stay above the codes of the earlier Transistor-based builds.
        val appVersionMajor = 2
        val appVersionMinor = 0
        val appVersionPatch = 0
        versionCode = 2000000 + appVersionMajor * 100000 + appVersionMinor * 1000 + appVersionPatch
        versionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"
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
        // release key: never committed - GitHub Actions decodes it from the repository secrets (see .github/workflows/build-apk.yml)
        val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE_FILE")
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = "webradiodroid"
                keyPassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "@string/app_name_debug"
        }
    }


    lint {
        disable += "MissingTranslation"
        // CI prints the findings in the log, they do not stop the build
        abortOnError = false
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
