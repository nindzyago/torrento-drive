import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.nindzyago.torrentodrive"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "io.github.nindzyago.torrentodrive"
        minSdk = 28
        targetSdk = 36
        versionCode = 10
        versionName = "0.5.1"
    }

    // Release key: from environment variables (CI) or keystore.properties (your machine, git-ignored).
    // Without either, release builds come out unsigned.
    val keystore = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.reader()?.use(::load)
    }
    fun secret(name: String): String? = System.getenv(name) ?: keystore.getProperty(name)
    val storePath = secret("RELEASE_STORE_FILE")
    signingConfigs {
        if (storePath != null) create("release") {
            storeFile = file(storePath)
            storePassword = secret("RELEASE_STORE_PASSWORD")
            keyAlias = secret("RELEASE_KEY_ALIAS")
            keyPassword = secret("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    // byedpi, the native core of the ByeDPI connection method
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    testImplementation("junit:junit:4.13.2")
}
