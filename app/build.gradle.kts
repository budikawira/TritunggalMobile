import org.gradle.api.JavaVersion

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.navigation.safe.args)
    id("io.sentry.android.gradle") version "5.11.0" apply false
}

android {
    namespace = "com.inventory.app.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.inventory.app.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 10108
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") { // Or create("debug") if it's not automatically created, though debug usually is.
            // It's unusual to use a release keystore for debug builds.
            // Android Studio typically manages a default debug keystore.
            // If you truly intend to use this for debug, the path needs to be correct.
            // Assuming "/uhf-serial_release.jks" means it's in the root of your project or module.
            // More commonly, keystore files are placed within the module's directory (e.g., app/keystores)
            // or a project-level directory.

            // Example if the file is in the root of the current module:
            // storeFile = file("uhf-serial_release.jks")

            // Example if the file is in a 'keystores' subfolder of the current module:
            // storeFile = file("keystores/uhf-serial_release.jks")

            // Using the path as in your Groovy example:
            // This path "/uhf-serial_release.jks" refers to the root of the filesystem,
            // which is likely NOT what you intend unless the keystore is actually there.
            // It's more common for paths to be relative to the project or module.
            // Let's assume you meant it to be relative to the module directory for now:
            storeFile = file("uhf-serial_release.jks") // Corrected to be relative
            storePassword = "123456"
            keyPassword = "123456"
            keyAlias = "uhf-serial-key"
        }
        // Use create for new configurations or getByName if modifying an existing one.
        // "release" is not created by default in signingConfigs, so you usually create it.
        create("release") {
            // Again, ensure the path to your keystore is correct.
            // If "uhf-serial_release.jks" is in the root of your module directory:
            storeFile = file("uhf-serial_release.jks")
            // If it's in your project's root directory (and your module is, for example, app/):
            // storeFile = rootProject.file("uhf-serial_release.jks")
            storePassword = "123456"
            keyPassword = "123456"
            keyAlias = "uhf-serial-key"
        }
    }


    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.navigation.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit2.converter.scalars)
    implementation(libs.converter.gson)  // For JSON parsing
    implementation(libs.logging.interceptor)  // Optional: Logs requests/responses

    //user interface
    implementation(libs.androidx.cardview)
    implementation(libs.material.v150)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    //implementation(libs.androidx.navigation.safe.args.gradle.plugin)

    implementation(libs.library) //sweetalertdialog
    implementation(libs.lottie)

    //qc code scanning
    implementation(libs.barcode.scanning) // ML Kit for QR code
    implementation(libs.play.services.mlkit.barcode.scanning) // Play Services version
    implementation(libs.androidx.camera.core) // CameraX Core
    implementation(libs.androidx.camera.camera2) // CameraX Camera2
    implementation(libs.androidx.camera.lifecycle) // CameraX Lifecycle
    implementation(libs.androidx.camera.view) // CameraX View (for Preview)

    //logging
    implementation("io.sentry:sentry-android:6.30.0")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.*"))))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Or the latest stable version
}