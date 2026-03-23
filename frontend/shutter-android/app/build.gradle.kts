import java.util.Properties

import de.undercouch.gradle.tasks.download.Download

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    id("de.undercouch.download") version "5.4.0"
}

val nativeAarDeps by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.s14p21a404.shutter"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    ndkVersion = "21.4.7075529"

    defaultConfig {
        applicationId = "com.s14p21a404.shutter"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val kakaoKey = localProperties.getProperty("kakao.native.app.key", "")
        resValue("string", "kakao_app_key", kakaoKey)
        resValue("string", "kakao_oauth_scheme", "kakao$kakaoKey")

        val serverBaseUrl = localProperties.getProperty("server.base.url", "https://your-server.com/")
        buildConfigField("String", "SERVER_BASE_URL", "\"$serverBaseUrl\"")

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    packaging {
        resources {
            pickFirsts += "**/libc++_shared.so"
        }
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
            useLegacyPackaging = true // [필수] DSP가 APK 내부 .so를 직접 읽을 수 없으므로 압축 해제 강제
        }
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
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
        resValues = true
        buildConfig = true // BuildConfig 활성화
    }
}

dependencies {
    implementation("io.agora.rtc:full-sdk:4.3.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kakao.sdk.user)
    implementation(libs.kakao.sdk.talk)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation(libs.kakao.map)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    val pytorch = "org.pytorch:pytorch_android:2.1.0"
    val opencv = "com.quickbirdstudios:opencv:4.5.3.0"
    implementation(pytorch)
    implementation(opencv)
    nativeAarDeps(pytorch)
    nativeAarDeps(opencv)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // ONNX Runtime for non-Qualcomm fallback
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.1")
}


val nativeLibsDir = layout.buildDirectory.dir("native_libs").get().asFile

val opencvSdkZip = nativeLibsDir.resolve("opencv-sdk.zip")
val opencvHeaderFile = nativeLibsDir.resolve("opencv/jni/include/opencv2/opencv.hpp")

val downloadOpenCVSDK by tasks.registering(Download::class) {
    onlyIf { !opencvSdkZip.exists() }
    src("https://sourceforge.net/projects/opencvlibrary/files/4.5.3/opencv-4.5.3-android-sdk.zip/download")
    dest(opencvSdkZip)
    overwrite(false)
}

val extractOpenCVHeaders by tasks.registering(Copy::class) {
    onlyIf { !opencvHeaderFile.exists() }
    dependsOn(downloadOpenCVSDK)
    from(Callable { zipTree(opencvSdkZip) }) {
        include("OpenCV-android-sdk/sdk/native/jni/include/**")
        eachFile {
            val segments = relativePath.segments.toMutableList()
            repeat(5) { segments.removeAt(0) }
            relativePath = RelativePath(true, *segments.toTypedArray())
        }
        includeEmptyDirs = false
    }
    into(nativeLibsDir.resolve("opencv/jni/include"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}


val extractPyTorch by tasks.registering(Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val pytorchAar = nativeAarDeps.files.find {
        it.name.contains("pytorch_android") && !it.name.contains("lite")
    }
    pytorchAar?.let {
        from(zipTree(it))
        into(layout.buildDirectory.dir("native_libs/pytorch"))
    }
}

val extractOpenCV by tasks.registering(Copy::class) {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    val opencvAar = nativeAarDeps.files.find { it.name.contains("opencv") }
    opencvAar?.let {
        from(zipTree(it))
        into(layout.buildDirectory.dir("native_libs/opencv"))
    }
}

tasks.named("preBuild") {
    dependsOn(extractPyTorch, extractOpenCV, extractOpenCVHeaders)
}