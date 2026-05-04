plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.klimate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.klimate"
        minSdk = 24
        targetSdk = 34
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.cardview)
    implementation(libs.core)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation ("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.work:work-runtime:2.9.1")
    androidTestImplementation(libs.androidx.work.testing)
    implementation("com.google.guava:guava:31.1-android")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.3.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.work:work-testing:2.9.0")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.3")
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("app.rive:rive-android:9.11.3")
}
