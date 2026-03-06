plugins {
    alias(libs.plugins.android.application)
}

val nativeAarDeps by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get())
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
                // C++17 표준 사용 설정
                cppFlags += "-std=c++17"

                arguments(
                    "-DANDROID_STL=c++_shared"
                )
            }
        }
        // ndk 필터 설정 (테스트 기기에 맞게 설정, 보통 arm64-v8a 사용)
        ndk {
            abiFilters.add("arm64-v8a")
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

val pytorchVersion = "2.1.0"

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("org.pytorch:pytorch_android:$pytorchVersion")
}

val extractAARForNativeBuild by tasks.registering(Copy::class) {
    // Gradle 캐시에서 pytorch_android AAR 파일을 찾음
    val pytorchAar = nativeAarDeps.files.find { it.name.contains("pytorch_android") && !it.name.contains("lite") }
    if (pytorchAar != null) {
        from(zipTree(pytorchAar))
        // 추출된 파일이 저장될 경로 설정
        into(layout.buildDirectory.dir("native_libs/pytorch"))
    }
}

// C++ 빌드(preBuild) 전에 추출 Task가 먼저 실행되도록 설정
tasks.named("preBuild") {
    dependsOn(extractAARForNativeBuild)
}