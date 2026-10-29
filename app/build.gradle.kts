plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
}

// Room 的 KSP 校验器会把 sqlite-jdbc 原生库解压到 java.io.tmpdir 再加载；云编译机的 /tmp 不允许映射可执行代码
// （failed to map segment from shared object）。KSP 跑在 Gradle 进程里，这里把解压目录换到构建目录，本地构建不受影响。
System.setProperty(
    "org.sqlite.tmpdir",
    layout.buildDirectory.dir("sqlite-native").get().asFile.apply { mkdirs() }.absolutePath,
)

android {
    namespace = "io.github.xingyingyuzhui.modbusmobile"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.xingyingyuzhui.modbusmobile"
        minSdk = 26
        targetSdk {
            version = release(37)
        }
        versionCode = 5
        versionName = "0.1.4"
        // 署名：Corn Modbus by xingyingyuzhui（与 application/Authorship.kt 一致）
        buildConfigField("String", "APP_AUTHOR", "\"xingyingyuzhui\"")
        buildConfigField("String", "APP_REPO", "\"https://github.com/xingyingyuzhui/corn-modbus\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":modbus-core"))
    implementation(libs.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.usb.serial)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.compose.material3.adaptive)

    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.robolectric)
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "roborazzi.record.filePathStrategy",
        "relativePathFromRoborazziContextOutputDirectory",
    )
}
