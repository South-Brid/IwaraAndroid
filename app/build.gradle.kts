import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 发布签名：读取项目根目录的 keystore.properties（不提交到 git）；没有该文件时 release 包不签名
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

// 每次发版改这里：versionCode 递增，versionName 用 主.次.修订
val appVersionCode = 4
val appVersionName = "1.3.0"

// 安装包文件名带版本号：IwaraPhone-1.1.0-debug.apk / IwaraPhone-1.1.0-release.apk
base.archivesName = "IwaraPhone-$appVersionName"

android {
    namespace = "com.example.iwaraphone"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.iwaraphone"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        manifestPlaceholders["appLabel"] = "IwaraPhone"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // v2 + v3 签名（minSdk ≥ 24 时 AGP 不生成 v1，系统也不需要）
    signingConfigs {
        getByName("debug") {
            enableV2Signing = true
            enableV3Signing = true
        }
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    // 原生库压缩存放、安装时解压（extractNativeLibs=true）：兼容性最好，安装包也更小
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildTypes {
        // 测试版用独立包名，可以和正式版同时安装（签名不同也不冲突），桌面名称和版本号带"测试版 / -debug"以便区分
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "IwaraPhone 测试版"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            // 整个应用一起优化。模板默认的 packageScope 只优化 androidx/kotlin，
            // 会导致应用代码访问被改写的 kotlin 类时报 IllegalAccessError（启动即崩溃）
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}