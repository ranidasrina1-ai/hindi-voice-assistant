import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val llamaVersion = "v0.7.0"
val llamaSha256 = "48ec218be461975cd4d39f2155675851e6f3ad7835f2522412ec2b610f71c20c"
val llamaDir = layout.projectDirectory.dir("src/main/cpp/llama.cpp")
val llamaReady = llamaDir.file(".complete").asFile

val fetchLlamaCpp = tasks.register("fetchLlamaCpp") {
    doLast {
        if (llamaReady.isFile && llamaReady.readText().trim() == llamaVersion) return@doLast

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(1024 * 1024).use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { it.toInt().and(0xff).toString(16).padStart(2, '0') }
        }

        val archive = layout.buildDirectory.file("llama-$llamaVersion.zip").get().asFile
        archive.parentFile.mkdirs()
        if (!archive.isFile || sha256(archive) != llamaSha256) {
            archive.delete()
            logger.lifecycle("Downloading llama.cpp $llamaVersion")
            URL("https://github.com/ggml-org/llama.cpp/archive/refs/tags/$llamaVersion.zip")
                .openStream()
                .use { input -> archive.outputStream().use { output -> input.copyTo(output) } }
        }
        check(sha256(archive) == llamaSha256) {
            "llama.cpp archive checksum mismatch"
        }

        val dest = llamaDir.asFile
        dest.deleteRecursively()
        dest.mkdirs()
        ZipInputStream(archive.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val relative = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relative.isNotBlank()) {
                    val out = File(dest, relative)
                    require(out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
                        "Unsafe zip entry: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zis.copyTo(it) }
                    }
                }
                zis.closeEntry()
            }
        }
        File(dest, ".complete").writeText(llamaVersion)
        archive.delete()
    }
}

tasks.matching {
    it.name.startsWith("configureCMake") ||
        it.name.startsWith("buildCMake") ||
        it.name.startsWith("externalNativeBuild")
}.configureEach { dependsOn(fetchLlamaCpp) }

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchLlamaCpp) }

android {
    namespace = "com.voiceassistant.hindi"
    compileSdk = 34
    ndkVersion = "26.1.10909125"

    defaultConfig {
        applicationId = "com.voiceassistant.hindi"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
                cppFlags += listOf("-O2")
            }
        }
    }

    val keystorePath = System.getenv("KEYSTORE_PATH")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && File(keystorePath).isFile

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = File(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        noCompress += "zip"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
