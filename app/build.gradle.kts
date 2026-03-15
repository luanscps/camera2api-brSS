plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.camera2rtsp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.camera2rtsp"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // NanoHTTPD - Servidor HTTP leve
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // RootEncoder - library principal (RTMP/RTSP push + Camera2)
    implementation("com.github.pedroSG94.RootEncoder:library:2.4.5")

    // RootEncoder - modulo servidor RTSP embutido
    // Fornece: RtspServerCamera2, ClientListener, ServerClient
    implementation("com.github.pedroSG94.RootEncoder:rtsp-server:2.4.5")
}
