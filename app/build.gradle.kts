plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // 包名刻意与 V1（com.local.qrjump）和厂商（com.qiekj.*）都不同，
    // 免得在设置里混淆、也不会被误认为官方应用。
    namespace = "com.local.qkjump"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.local.qkjump"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // 本工程只做「拉起胖乖 + 代点一下右上角的扫描图标」，
    // 不需要相机、不需要 ML Kit、不需要 ZXing —— 扫由胖乖自己扫。
}
