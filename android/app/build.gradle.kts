plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.osakapatnam.konnichiwah"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.osakapatnam.konnichiwah"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"

        // ML Kit (the menu reader) ships about 28 MB of native code per CPU
        // type. Phones from the last several years are arm64, and the
        // emulator on our Macs is x86_64, so we leave out 32-bit ARM and x86.
        // An app bundle on Play would split these per device anyway.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            val apiBaseUrl = providers.gradleProperty("apiBaseUrl").get()
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        release {
            // There's no deployed backend yet. Set releaseApiBaseUrl (HTTPS)
            // once there is one.
            val apiBaseUrl = providers.gradleProperty("releaseApiBaseUrl").getOrElse("https://example.invalid/")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The app switches between English and Hindi itself (see InLanguage), so
    // an app bundle must ship both languages to every phone, not just the
    // phone's own language.
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Export Room's schema to app/schemas so database changes show up in code
// review and we can write migrations later.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Copy the etiquette rulebook and the checked diet cards (and their Hindi
// versions) from the backend into
// the app's assets, so the app and the API always ship the same content and
// it works offline. The backend's files are the single source of truth; don't
// edit the copies.
abstract class CopyRulebookTask : DefaultTask() {
    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        files.forEach { it.copyTo(out.resolve(it.name)) }
    }
}

val copyRulebook = tasks.register<CopyRulebookTask>("copyRulebook") {
    files.from(
        layout.projectDirectory.file("../../backend/rulebook/etiquette.json"),
        layout.projectDirectory.file("../../backend/rulebook/phrases.json"),
        layout.projectDirectory.file("../../backend/rulebook/etiquette.hi.json"),
        layout.projectDirectory.file("../../backend/rulebook/phrases.hi.json"),
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyRulebook, CopyRulebookTask::outputDir)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    // The menu reader: CameraX for the camera, and ML Kit's on-device
    // Japanese text recognition (the model is bundled in the APK, so it works
    // offline) and translation (the ja-en model is downloaded once).
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.translate)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
