import java.util.Properties
import java.io.FileInputStream


// Load keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

val hasSigningProps: Boolean =
    keystorePropertiesFile.exists() &&
        listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
            .all { key -> !keystoreProperties.getProperty(key).isNullOrBlank() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.kapt)
}

apply(plugin = "com.google.android.gms.oss-licenses-plugin")

val updateManifestUrl: String = (project.findProperty("updateManifestUrl") as String?)
    ?.trim()
    .orEmpty()

fun String.escapeForBuildConfigString(): String =
    this.replace("\\\\", "\\\\\\\\").replace("\"", "\\\\\"")

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            // AGP output types vary across versions; use reflection to set outputFileName when present.
            @Suppress("UNCHECKED_CAST")
            val outputFileNameProperty = output.javaClass.methods
                .firstOrNull { it.name == "getOutputFileName" && it.parameterCount == 0 }
                ?.invoke(output) as? org.gradle.api.provider.Property<String>

            outputFileNameProperty?.set("mjc-jm.apk")
        }
    }
}

android {
    namespace = "com.bossless.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bossless.companion"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"

        // Optional: URL to a JSON update manifest hosted externally.
        // Configure via gradle.properties: updateManifestUrl=https://example.com/mjc-jm/update.json
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "\"${updateManifestUrl.escapeForBuildConfigString()}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasSigningProps) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningProps) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Security
    implementation(libs.androidx.security.crypto)

    // Background Work
    implementation(libs.androidx.work.runtime.ktx)

    // Image Loading
    implementation(libs.coil.compose)

    // QR Code generation for payment links
    implementation("com.lightspark:compose-qr-code:1.0.1")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Location
    implementation(libs.play.services.location)

    // Open source licenses screen
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

kapt {
    correctErrorTypes = true
}
