import java.util.Properties

val releaseKeystoreFile = rootProject.file("keystore.properties")
val releaseKeystore = Properties().apply {
    if (releaseKeystoreFile.isFile) {
        releaseKeystoreFile.inputStream().use(::load)
    }
}
val ciKeystorePath = providers.environmentVariable("DENARO_UPLOAD_KEYSTORE_PATH").orNull
val ciStorePassword = providers.environmentVariable("DENARO_UPLOAD_STORE_PASSWORD").orNull
val ciKeyAlias = providers.environmentVariable("DENARO_UPLOAD_KEY_ALIAS").orNull
val ciKeyPassword = providers.environmentVariable("DENARO_UPLOAD_KEY_PASSWORD").orNull
val ciSigningValues = listOf(
    ciKeystorePath,
    ciStorePassword,
    ciKeyAlias,
    ciKeyPassword,
)
val hasCiSigning = ciSigningValues.all { !it.isNullOrBlank() }
check(ciSigningValues.all { it.isNullOrBlank() } || hasCiSigning) {
    "CI signing requires all four DENARO_UPLOAD_* environment variables."
}
val releaseVersionCode = providers.gradleProperty("releaseVersionCode").orNull?.let {
    requireNotNull(it.toIntOrNull()) { "releaseVersionCode must be an integer." }
}
val releaseVersionName = providers.gradleProperty("releaseVersionName").orNull?.also {
    require(it.isNotBlank()) { "releaseVersionName must not be blank." }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.gradle.play.publisher)
}

android {
    namespace = "it.rfmariano.denaro"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "it.rfmariano.denaro"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersionCode ?: 38
        versionName = releaseVersionName ?: "2.0.0-pre-alpha.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            if (hasCiSigning) {
                signingConfig = signingConfigs.create("ciRelease") {
                    storeFile = rootProject.file(requireNotNull(ciKeystorePath))
                    storePassword = requireNotNull(ciStorePassword)
                    keyAlias = requireNotNull(ciKeyAlias)
                    keyPassword = requireNotNull(ciKeyPassword)
                }
            } else if (releaseKeystoreFile.isFile) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = rootProject.file(
                        releaseKeystore.getProperty("storeFile"),
                    )
                    storePassword = releaseKeystore.getProperty("storePassword")
                    keyAlias = releaseKeystore.getProperty("keyAlias")
                    keyPassword = releaseKeystore.getProperty("keyPassword")
                }
            }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

play {
    useApplicationDefaultCredentials.set(true)
    track.set("internal")
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.COMPLETED)
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    implementation(libs.icons.lucide.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.sqlcipher.android)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
