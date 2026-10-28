import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// WebRadioDroid Libero: companion app that shows WebRadioDroid's own player on Android Auto.
// Android Auto offers one car app per package and WebRadioDroid is a media app, so the free player -
// which needs a navigation app's drawing surface - lives in this separate package.
android {
    namespace = "io.github.thamichele.webradiodroid.free"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.thamichele.webradiodroid.free"
        minSdk = 25
        targetSdk = 37
        // same version scheme as WebRadioDroid (see app/build.gradle.kts)
        val appVersionMajor = 1
        val appVersionMinor = 0
        val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
        versionCode = 2000000 + appVersionMajor * 100000 + appVersionMinor * 1000 + ciRunNumber
        versionName = "$appVersionMajor.$appVersionMinor.$ciRunNumber"
        // the WebRadioDroid build whose player this app controls
        val mainAppId = "io.github.thamichele.webradiodroid"
        buildConfigField("String", "MAIN_APP_ID", "\"$mainAppId\"")
        manifestPlaceholders["mainAppId"] = mainAppId
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        getByName("debug") {
            // same fixed debug key as WebRadioDroid: the player only hands full control to apps signed with its own key
            storeFile = file("../app/ci-debug.keystore")
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
            buildConfigField("String", "MAIN_APP_ID", "\"io.github.thamichele.webradiodroid.debug\"")
            manifestPlaceholders["mainAppId"] = "io.github.thamichele.webradiodroid.debug"
            manifestPlaceholders["appLabel"] = "@string/app_name_debug"
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
}
