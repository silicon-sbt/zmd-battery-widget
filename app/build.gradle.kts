plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zmd.charge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zmd.charge"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.7"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    // 彩蛋文件按原始字节存储，不做 zip 压缩 —— 这样 APK 体积可以精确控制到字节
    androidResources {
        noCompress += "pad.bin"
        noCompress += "bin"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
}