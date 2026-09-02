import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
    alias(libs.plugins.ktlint)
}

// Release signing secrets, resolved in order: Gradle property (-P, or
// ~/.gradle/gradle.properties) -> env var -> gitignored keystore.properties at repo root.
// No literal fallback for storePassword/keyPassword — if unset, release signing is skipped.
val keystoreProperties =
    Properties().apply {
        val keystorePropertiesFile = rootProject.file("keystore.properties")
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

fun resolveSigningValue(
    gradlePropertyOrEnvKey: String,
    keystorePropertiesKey: String,
): String? =
    (findProperty(gradlePropertyOrEnvKey) as String?)
        ?: System.getenv(gradlePropertyOrEnvKey)
        ?: keystoreProperties.getProperty(keystorePropertiesKey)

val releaseStorePassword = resolveSigningValue("KEYSTORE_PASSWORD", "storePassword")
val releaseKeyPassword = resolveSigningValue("KEY_PASSWORD", "keyPassword")
val releaseKeyAlias = resolveSigningValue("KEY_ALIAS", "keyAlias") ?: "pesa_mind"
val releaseStoreFilePath =
    resolveSigningValue("KEYSTORE_FILE", "storeFile")
        ?: (System.getProperty("user.home") + "/.android/my-release-key.keystore")

// Gate: only wire up release signing if both secrets are actually available.
val hasReleaseSigningConfig = releaseStorePassword != null && releaseKeyPassword != null

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

// API base URL. Release is pinned to production and is NOT overridable — a release
// build accidentally pointing at a laptop would be far worse than the inconvenience.
// Debug resolves Gradle property -> env var -> .env, so the app can be pointed at a
// locally-running backend (e.g. -PAPI_BASE_URL=http://192.168.1.5:8099/api/v1/) to
// exercise features that aren't deployed yet, like subscription checkout.
val productionApiBaseUrl = "https://api.dlabs.cc/api/v1/"
val resolvedDebugApiBaseUrl =
    (findProperty("API_BASE_URL") as String?)
        ?: System.getenv("API_BASE_URL")
        ?: readDotEnvValue("API_BASE_URL")
        ?: productionApiBaseUrl

// Retrofit requires a trailing slash on the base URL, and forgetting it fails at
// runtime rather than at configuration time — so normalise it here.
val debugApiBaseUrl =
    if (resolvedDebugApiBaseUrl.endsWith("/")) resolvedDebugApiBaseUrl else "$resolvedDebugApiBaseUrl/"

val googleAndroidClientId =
    (findProperty("GOOGLE_ANDROID_CLIENT_ID") as String?)
        ?: System.getenv("GOOGLE_ANDROID_CLIENT_ID")
        ?: readDotEnvValue("GOOGLE_ANDROID_CLIENT_ID")
        ?: "884168293120-cngr633jrrkuq5hcuv0cqv19latmfb9j.apps.googleusercontent.com"

configurations.all {
    resolutionStrategy {
        // Play Billing Library 8.x/9.x's published Gradle module metadata pulls in
        // kotlin-stdlib 2.2.10 transitively, which bumps it project-wide even though
        // this project's Kotlin Gradle plugin is pinned to 2.1.0. That mismatch makes
        // kapt's Dagger/Hilt annotation processor (whose bundled kotlinx-metadata-jvm
        // reader tops out at metadata version 2.1.0) fail reading @Metadata on any
        // class compiled against the bumped stdlib — not specific to billing's own
        // code, it breaks kapt for the whole module. Pin back to the stdlib version
        // matching our Kotlin plugin; billing-ktx's actual API surface doesn't require
        // 2.2-only stdlib features, so this is safe.
        force("org.jetbrains.kotlin:kotlin-stdlib:2.1.10")
    }
}

android {
    namespace = "cc.dlabs.pesamind"
    compileSdk = 36

    defaultConfig {
        applicationId = "cc.dlabs.pesamind"
        minSdk = 26
        targetSdk = 36
        versionCode = 35
        versionName = "35"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema history (see docs/decisions/ADR-0004-offline-first.md) — committed
        // under app/schemas so a future version bump has a real migration to test against.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
            }
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFilePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Disabled: R8 minification obfuscates Gson-serialized DTO field names, and every
            // package that isn't covered by an explicit proguard keep rule silently breaks
            // request/response parsing in this build type only (see proguard-rules.pro history —
            // this already broke Google Sign-In and the dashboard fetch in two different DTO
            // packages). Not worth the size/perf tradeoff until the API models are stable and a
            // keep-rule audit process exists.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$googleAndroidClientId\"")
            buildConfigField("String", "API_BASE_URL", "\"$productionApiBaseUrl\"")
            // SYMBOL_TABLE (not FULL): enough for Play Console to symbolicate native crashes/ANRs
            // from a dependency's bundled .so (this app has no first-party NDK/JNI code of its
            // own) without the larger size of full native debug info. Requires an NDK component
            // to be installed locally/in CI — AGP uses its objcopy/llvm-strip to extract the
            // symbol table; run `./gradlew bundleRelease` and follow the version it reports if
            // one isn't installed yet.
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
        debug {
            // Debug builds sign with the default debug keystore — do not reuse release signing.
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$googleAndroidClientId\"")
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
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
        getByName("androidTest") {
            assets {
                srcDirs("$projectDir/schemas")
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
    implementation(libs.androidx.biometric)
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
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.ui)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Google Sign-In
    implementation(libs.google.signin)

    // Chrome Custom Tabs — opens the card 3DS authorization page for subscription
    // checkout. A Custom Tab, not a WebView: the customer authenticates with their
    // bank there, and only a Custom Tab shows the real URL and padlock.
    implementation(libs.androidx.browser)

    // Google Play Billing Library — subscription purchases routed through Play's
    // own billing system, as required by Play Store policy for digital
    // subscriptions sold in-app. Replaces the card-via-Flutterwave checkout
    // option on this (Play-distributed) build; mobile money is unaffected.
    implementation(libs.billing.ktx)

    // Tink + Android Keystore — encrypts TokenManager's DataStore-persisted secrets at rest.
    // Not EncryptedSharedPreferences: deprecated in security-crypto 1.1.0-alpha07 (April 2025)
    // for main-thread StrictMode violations and OEM keyset-corruption crashes. See
    // docs/decisions/ADR-0005-token-storage-encryption.md.
    implementation(libs.tink.android)

    // Room (offline-first local database — see docs/decisions/ADR-0004-offline-first.md)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)

    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // WorkManager + Hilt integration (offline-first sync worker — ADR-0004 Slice A2)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Testing
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)

    // Robolectric (JVM-runnable real Android runtime, notably a real Context for
    // Room.inMemoryDatabaseBuilder) — needed to exercise real SQLite unique-index/transaction
    // behavior for the channel-dedup/TID-dedup fix without a device/emulator. See
    // app/src/test/java/cc/dlabs/pesamind/core/data/*DedupTest.kt.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

kapt {
    correctErrorTypes = true
}
