plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

fun readDotEnvValue(key: String): String? {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return null

    val prefix = "$key="
    return envFile.readLines()
        .asSequence()
        .map { it.trim() }
        .firstOrNull { line -> line.isNotBlank() && !line.startsWith("#") && line.startsWith(prefix) }
        ?.substringAfter("=")
        ?.trim()
        ?.removeSurrounding("\"")
        ?.removeSurrounding("'")
}

val googleAndroidClientId = (findProperty("GOOGLE_ANDROID_CLIENT_ID") as String?)
    ?: System.getenv("GOOGLE_ANDROID_CLIENT_ID")
    ?: readDotEnvValue("GOOGLE_ANDROID_CLIENT_ID")
    ?: "884168293120-cngr633jrrkuq5hcuv0cqv19latmfb9j.apps.googleusercontent.com"

android {
    namespace = "cc.dlabs.pesamind"
    compileSdk = 36

    defaultConfig {
        applicationId = "cc.dlabs.pesamind"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/my-release-key.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "K@sh404730"
            keyAlias = "pesa_mind"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "K@sh404730"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$googleAndroidClientId\"")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$googleAndroidClientId\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            assets {
                srcDirs("src/main/assets")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation.foundation)
    implementation(libs.androidx.compose.ui.graphics)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Google Sign-In
    implementation(libs.google.signin)
    
    // Android Security (EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    
    // Testing
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
}

kapt {
    correctErrorTypes = true
}