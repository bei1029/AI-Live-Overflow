// AyanPet — 阿衍的 Android 悬浮窗身体
// 架构约束：桌宠只是「身体 + 传感器」（渲染 + 上报 + 被控制），
// 大脑（人格/记忆/情绪判断）留在 Operit 对话侧，通过 Supabase pet_state 双向通信。
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    signingConfigs {
        create("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    namespace = "com.vael.ayanpet"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vael.ayanpet"
        minSdk = 26            // TYPE_APPLICATION_OVERLAY 需要 API 26+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 刻意保持零第三方运行时依赖：悬浮窗 / WebView / UsageStats / HttpURLConnection
// 全部走 Android framework —— CI 构建更快、失败面更小。
dependencies {
}
