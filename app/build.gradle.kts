plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.selfiememory"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.selfiememory"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.3.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    dependencies {
        val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
        implementation(composeBom)

        // Compose
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.material3:material3")
        implementation("androidx.compose.material:material-icons-extended")
        implementation("androidx.activity:activity-compose:1.8.2")
        implementation("androidx.navigation:navigation-compose:2.7.7")
        implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
        implementation("androidx.lifecycle:lifecycle-service:2.7.0")

        // CameraX
        val cameraxVersion = "1.3.1"
        implementation("androidx.camera:camera-core:$cameraxVersion")
        implementation("androidx.camera:camera-camera2:$cameraxVersion")
        implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
        implementation("androidx.camera:camera-view:$cameraxVersion")

        // Room
        val roomVersion = "2.6.1"
        implementation("androidx.room:room-runtime:$roomVersion")
        implementation("androidx.room:room-ktx:$roomVersion")
        ksp("androidx.room:room-compiler:$roomVersion")

        // Hilt
        implementation("com.google.dagger:hilt-android:2.50")
        ksp("com.google.dagger:hilt-compiler:2.50")
        implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

        // DataStore
        implementation("androidx.datastore:datastore-preferences:1.0.0")

        // Location
        implementation("com.google.android.gms:play-services-location:21.1.0")

        // Coil for image loading
        implementation("io.coil-kt:coil-compose:2.5.0")
    }
}