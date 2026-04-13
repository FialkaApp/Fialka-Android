import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

// Single source of truth for app version — edit version.properties only
val versionProps = Properties().apply {
    load(FileInputStream(rootProject.file("version.properties")))
}

android {
    namespace = "com.fialkaapp.fialka"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fialkaapp.fialka"
        minSdk = 33
        targetSdk = 36
        versionCode = versionProps["VERSION_CODE"].toString().toInt()
        versionName = versionProps["VERSION_NAME"].toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: localProps.getProperty("RELEASE_STORE_FILE", "../fialka-release.p12"))
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: localProps.getProperty("RELEASE_STORE_PASSWORD", "")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: localProps.getProperty("RELEASE_KEY_ALIAS", "")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: localProps.getProperty("RELEASE_KEY_PASSWORD", "")
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // signingConfig intentionally absent — APK is signed locally, not by CI
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.7")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")

    // Room (local database)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // SQLCipher (encrypted Room database)
    implementation("net.zetetic:sqlcipher-android:4.14.1")
    implementation("androidx.sqlite:sqlite-ktx:2.6.2")

    // Security / Crypto
    implementation("androidx.biometric:biometric:1.1.0")

    // Tor — Guardian Project Tor binary (BSD) + Briar privacy libs (Apache/BSD)
    implementation("info.guardianproject:tor-android:0.4.9.5.1")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("org.briarproject:socks-socket:0.1")
    implementation("org.briarproject:lyrebird-android:0.6.2")
    implementation("org.briarproject:moat-api:0.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // QR Code — generation + scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") {
        isTransitive = false
    }
    implementation("com.google.zxing:core:3.5.4")

    // Splash Screen
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")

    // WorkManager — background wallet sync
    implementation("androidx.work:work-runtime-ktx:2.11.2")
}
