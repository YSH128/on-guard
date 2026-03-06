import de.undercouch.gradle.tasks.download.Download

plugins {
    alias(libs.plugins.android.application)
    id("de.undercouch.download") version "5.4.0"
}

val nativeAarDeps by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    val pytorch = "org.pytorch:pytorch_android:2.1.0"
    val opencv = "com.quickbirdstudios:opencv:4.5.3.0"
    implementation(pytorch)
    implementation(opencv)
    nativeAarDeps(pytorch)
    nativeAarDeps(opencv)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}

val nativeLibsDir = layout.buildDirectory.dir("native_libs").get().asFile

nativeAarDeps.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
    val aar = artifact.file
    when {
        aar.name.contains("pytorch_android") && !aar.name.contains("lite") -> {
            copy {
                from(zipTree(aar))
                into(nativeLibsDir.resolve("pytorch"))
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
        aar.name.contains("opencv") -> {
            copy {
                from(zipTree(aar))
                into(nativeLibsDir.resolve("opencv"))
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
    }
}

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

android {
    namespace = "com.example.shutter_android_ai"
    compileSdk = 36
    ndkVersion = "21.4.7075529"

    defaultConfig {
        applicationId = "com.example.shutter_android_ai"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        viewBinding = true
    }
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