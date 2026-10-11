import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(key: String, default: String = ""): String =
    System.getenv(key)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: (project.findProperty(key) as String?)?.takeIf { it.isNotBlank() }
        ?: default

val appId = "io.github.chinatsu033.airpodsmode"

val signingProps = Properties().apply {
    val path = cfg("AIRPODSMODE_SIGNING_PROPERTIES")
    if (path.isNotBlank() && file(path).exists()) file(path).inputStream().use { load(it) }
}
val ksFile = cfg("AIRPODSMODE_KEYSTORE_FILE", signingProps.getProperty("storeFile") ?: "")
val ksPassword = cfg("AIRPODSMODE_KEYSTORE_PASSWORD", signingProps.getProperty("storePassword") ?: "")
val ksAlias = cfg("AIRPODSMODE_KEY_ALIAS", signingProps.getProperty("keyAlias") ?: "")
val ksKeyPassword = cfg("AIRPODSMODE_KEY_PASSWORD", signingProps.getProperty("keyPassword") ?: "")
val hasReleaseSigning = ksFile.isNotBlank() && file(ksFile).exists() && ksPassword.isNotBlank()

android {
    namespace = appId
    compileSdk = 36

    defaultConfig {
        applicationId = appId
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.3.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en", "zh-rCN")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = ksPassword
                keyAlias = ksAlias
                keyPassword = ksKeyPassword.ifBlank { ksPassword }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-service:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Bypass hidden API restrictions for classic BR/EDR L2CAP sockets (Apache-2.0).
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
