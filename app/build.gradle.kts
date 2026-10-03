plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.moneymap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moneymap"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A fixed debug key, committed on purpose: every build (local or CI) is signed the same way,
    // so a new APK installs over the old one and keeps the app's data.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Real release key, supplied by CI secrets or local env vars; never committed.
        val releaseStore = System.getenv("MONEYMAP_KEYSTORE")
        if (!releaseStore.isNullOrBlank() && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("MONEYMAP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MONEYMAP_KEY_ALIAS")
                keyPassword = System.getenv("MONEYMAP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release key the APK is signed with the debug key so it still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // "standard" installs anywhere. "full" adds the bank/UPI notification reader, which Google Play Protect blocks
    // when the APK is installed from a browser or file manager (install it with Play Protect paused, or adb install).
    flavorDimensions += "features"
    productFlavors {
        create("standard") {
            dimension = "features"
            buildConfigField("boolean", "NOTIFICATION_READER", "false")
        }
        create("full") {
            dimension = "features"
            versionNameSuffix = "-full"
            buildConfigField("boolean", "NOTIFICATION_READER", "true")
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.generateKotlin", "true")
    // Schema history for every database version; commit new files from app/schemas when the version changes.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Real org.json for JVM tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")

    // Screenshot tests (run with -Pscreenshots), rendered on the JVM by Robolectric + Roborazzi.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Screenshot tests only run when asked: ./gradlew testDebugUnitTest -Pscreenshots
val screenshots = project.hasProperty("screenshots")
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.record", screenshots.toString())
    if (!screenshots) exclude("**/ScreenshotTest*")
    else filter.includeTestsMatching("com.moneymap.ScreenshotTest")
}
