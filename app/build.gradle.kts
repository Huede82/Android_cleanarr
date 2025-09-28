plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "huedes.unraid.unraid_cleanarr"
    compileSdk = 36

    defaultConfig {
        applicationId = "huedes.unraid.unraid_cleanarr"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
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
        compose = true
    }
}

dependencies {

    // Standard Android & Jetpack Compose Bibliotheken
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ---- HIER SIND ALLE FEHLENDEN BIBLIOTHEKEN ----

    // Für die volle Icon-Auswahl
    implementation("androidx.compose.material:material-icons-extended:1.6.7")

    // Für die korrekte Verwaltung des ViewModels ("Gehirn" der App)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")

    // Für Netzwerk-Anfragen (HTTP Client & WebSockets)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Für die einfache Umwandlung von JSON in Kotlin-Objekte
    implementation("com.google.code.gson:gson:2.10.1")

    // Kotlin Coroutines für Hintergrund-Tasks (ist meist schon da, aber zur Sicherheit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


    // Standard Test-Bibliotheken (unverändert lassen)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}