plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.pigfarmerjc.galleryplayer.player.libvlc"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-player-api"))
    api(libs.libvlc.all)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Test dependencies
    testImplementation(libs.junit)
}
