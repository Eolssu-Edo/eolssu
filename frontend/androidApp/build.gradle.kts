import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Secrets are supplied by the build environment; no debug-key fallback for release.
val releaseStore = providers.environmentVariable("EOLSSU_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("EOLSSU_STORE_PASSWORD").orNull
val releaseAlias = providers.environmentVariable("EOLSSU_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("EOLSSU_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
// TEMP S15P31E102-309: opt-in QA APK only; remove after normal consent flow is verified.
val qaSkipConsent = providers.gradleProperty("eolssu.qaSkipConsent").orElse("false").get().toBooleanStrict()
val debugApiBaseUrl = providers.gradleProperty("eolssu.debugApiBaseUrl").orElse("https://k15e102.p.ssafy.io").get()
val debugLoopbackPort = Regex("http://127\\.0\\.0\\.1:([0-9]{1,5})").matchEntire(debugApiBaseUrl)
    ?.groupValues?.get(1)?.toIntOrNull()
check(debugApiBaseUrl == "https://k15e102.p.ssafy.io" || debugLoopbackPort in 1..65535) {
    "Debug API override must be a loopback URL; release always uses production HTTPS."
}
check(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningValues.all { !it.isNullOrBlank() }) {
    "Supply all four EOLSSU_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD environment variables."
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.ssafy.eolssu"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ssafy.eolssu"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("eolssu.versionCode").orElse("2").get().toInt()
        versionName = providers.gradleProperty("eolssu.versionName").orElse("1.0.0").get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (!releaseStore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            // TEMP S15P31E102-309
            buildConfigField("boolean", "QA_SKIP_CONSENT", qaSkipConsent.toString())
            if (qaSkipConsent) versionNameSuffix = "-qa309"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            // TEMP S15P31E102-309: never enable this in release, even when the property is true.
            buildConfigField("boolean", "QA_SKIP_CONSENT", "false")
            buildConfigField("String", "API_BASE_URL", "\"https://k15e102.p.ssafy.io\"")
            isDebuggable = false
            if (!releaseStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
