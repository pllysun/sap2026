import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "edu.csuft.sap"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.csuft.sap"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.5"
        // 后端地址：模拟器用 10.0.2.2 指向宿主机；真机改成局域网IP或部署域名
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8081\"")
    }

    // 发版签名：从项目根目录的 keystore.properties 读取（该文件不入库）。
    // 缺该文件时 release 回退用 debug 签名，保证 CI/本地仍能打包（但请勿用 debug 签名分发）。
    // keystore.properties 示例：
    //   storeFile=/绝对路径/sap-release.jks
    //   storePassword=xxxx
    //   keyAlias=sap
    //   keyPassword=xxxx
    // 生成：keytool -genkeypair -v -keystore sap-release.jks -alias sap -keyalg RSA -keysize 2048 -validity 36500
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // 多方案签名：v1 兼容老设备/部分加固平台，v2/v3 现代校验与密钥轮换
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8081\"")
        }
        release {
            isMinifyEnabled = true          // R8 代码混淆 + 裁剪
            isShrinkResources = true         // 资源压缩（依赖 minify）
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 正式环境后端域名（应用内升级 + 所有接口都走它，https）
            buildConfigField("String", "BASE_URL", "\"https://csuftsap.top\"")
            signingConfig = if (keystorePropsFile.exists())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.jsoup) // WebView 抓取的课表 HTML 端上解析
    testImplementation("junit:junit:4.13.2")
}

// 打包后把签名 release APK + mapping 复制到「项目根目录/release」(= 仓库根 sap2026/release/)，统一取用。
val releaseOutDir = rootProject.projectDir.parentFile.resolve("release")
tasks.register<Copy>("copyReleaseApk") {
    val vn = android.defaultConfig.versionName
    val vc = android.defaultConfig.versionCode
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release.apk"); rename { "sap-$vn-$vc.apk" }
    }
    from(layout.buildDirectory.dir("outputs/mapping/release")) {
        include("mapping.txt"); rename { "mapping-$vn-$vc.txt" } // 崩溃栈反混淆，随版本归档
    }
    into(releaseOutDir)
    doLast { println("✓ release APK + mapping -> $releaseOutDir") }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy("copyReleaseApk") }
