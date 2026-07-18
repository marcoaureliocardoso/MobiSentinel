import com.mobisentinel.signing.ReleaseSigningEnvironment
import com.mobisentinel.versioning.AppVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val appVersion = AppVersion.parse("0.1.1") // x-release-please-version
val releaseSigning = ReleaseSigningEnvironment.resolve(
    environment = System.getenv(),
    taskNames = gradle.startParameter.taskNames,
)

android {
    namespace = "br.com.marcocardoso.mobisentinel"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.marcocardoso.mobisentinel"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersion.versionCode
        versionName = appVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val productionSigningConfig = releaseSigning?.let { credentials ->
        val signingFile = file(credentials.storeFile)
        require(signingFile.isFile) {
            "ANDROID_SIGNING_STORE_FILE does not point to a readable file"
        }
        signingConfigs.create("production") {
            storeFile = signingFile
            storePassword = credentials.storePassword
            keyAlias = credentials.keyAlias
            keyPassword = credentials.keyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = productionSigningConfig
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(composeBom)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
