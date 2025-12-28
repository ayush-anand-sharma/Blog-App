@file:Suppress("UnstableApiUsage", "KDocMissingDocumentation", "ObjectPropertyName", "RemoveRedundantBackticks", "UnusedImport", "UsePropertyAccessSyntax", "SpellCheckingInspection") // Suppresses various lint warnings for the build file
plugins {
    alias(libs.plugins.android.application) // Applies the Android application plugin
    alias(libs.plugins.kotlin.android) // Applies the Kotlin Android plugin
    alias(libs.plugins.google.gms.google.services) // Applies the Google Services plugin
    id("kotlin-parcelize") // Applies the Kotlin Parcelize plugin for Parcelable implementation
}

android {
    namespace = "com.ayushcodes.blogapp" // Sets the namespace for the application
    compileSdk = 36 // Sets the compile SDK version

    defaultConfig {
        applicationId = "com.ayushcodes.blogapp" // Sets the unique application ID
        minSdk = 24 // Sets the minimum SDK version supported
        targetSdk = 36 // Sets the target SDK version
        versionCode = 1 // Sets the version code for the app
        versionName = "1.0" // Sets the version name for the app

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" // Sets the test instrumentation runner
    }

    buildTypes {
        release { // Configures the release build type
            isMinifyEnabled = false // Disables code minification
            proguardFiles( // Specifies ProGuard rules files
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11 // Sets Java source compatibility to version 11
        targetCompatibility = JavaVersion.VERSION_11 // Sets Java target compatibility to version 11
    }
    kotlinOptions {
        jvmTarget = "11" // Sets the JVM target for Kotlin compiler to 11
    }
    buildFeatures{
        viewBinding = true // Enables View Binding feature
    }
}

dependencies {

    // ANDROIDX LIBRARIES
    implementation(libs.androidx.core.ktx) // Provides essential Kotlin extensions for Android development.
    implementation(libs.androidx.appcompat) // Enables use of modern Android APIs on older Android versions.
    implementation(libs.material) // A set of UI components for implementing Material Design.
    implementation(libs.androidx.activity) // Manages activity lifecycle and provides components like OnBackPressedDispatcher.
    implementation(libs.androidx.constraintlayout) // A flexible layout manager for creating responsive UIs.
    implementation(libs.androidx.cardview) // A UI component for creating card-based layouts.
    implementation(libs.androidx.credentials) // Simplifies user sign-in and credential management.
    implementation(libs.androidx.credentials.play.services.auth) // Integrates Credential Manager with Google Play Services for sign-in.
    implementation(libs.androidx.recyclerview) // Efficiently displays large lists of data.

    // LIFECYCLE EXTENSIONS (Added for ViewModel and repeatOnLifecycle support)
    implementation(libs.androidx.lifecycle.viewmodel.ktx) // Lifecycle-aware components for ViewModels
    implementation(libs.androidx.lifecycle.runtime.ktx) // Lifecycle-aware components for runtime
    implementation(libs.androidx.activity.ktx) // Activity KTX extensions
    implementation(libs.androidx.fragment.ktx) // Fragment KTX extensions

    // CIRCLE IMAGE VIEW
    implementation(libs.circleimageview) // Library for displaying circular images

    // FIREBASE LIBRARIES
    implementation(libs.firebase.analytics) // Provides Google Analytics for Firebase to track user engagement.
    implementation(libs.firebase.database) // A real-time NoSQL database for syncing data across clients.
    implementation(libs.firebase.storage) // Provides cloud storage for user-generated content like images and videos.
    implementation(libs.firebase.firestore) // A scalable NoSQL cloud database for storing and syncing data.
    implementation(libs.firebase.auth) // Provides services to authenticate and manage users.

    // GOOGLE SERVICES
    implementation(libs.googleid) // Provides APIs for Google Sign-In and other identity services.
    implementation(libs.play.services.auth) // Provides access to Google authentication services.

    // TESTING LIBRARIES
    testImplementation(libs.junit) // A popular framework for writing unit tests in Java.
    androidTestImplementation(libs.androidx.junit) // Provides Android-specific extensions for JUnit tests.
    androidTestImplementation(libs.androidx.espresso.core) // A UI testing framework for writing automated tests.

    // CUSTOM SWEET ALERT DIALOGUE
    implementation(libs.sweetalert) // A library for creating beautiful and interactive alert dialogs.

    // FANCY TOAST
    implementation(libs.fancytoast) // A library for creating custom and visually appealing toast messages.

    // GLIDE LIBRARY
    implementation(libs.glide) // A powerful image loading and caching library for Android.
}
