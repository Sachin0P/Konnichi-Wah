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
}

// Export Room's schema to app/schemas so database changes show up in code
// review and we can write migrations later.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Copy the etiquette rulebook from the backend into the app's assets, so the
// app and the API always ship the same rules and tips work offline. The
// backend's file is the single source of truth; don't edit the copy.
abstract class CopyRulebookTask : DefaultTask() {
    @get:InputFile
    abstract val rulebook: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        rulebook.get().asFile.copyTo(out.resolve("etiquette.json"))
    }
}

val copyRulebook = tasks.register<CopyRulebookTask>("copyRulebook") {
    rulebook.set(layout.projectDirectory.file("../../backend/rulebook/etiquette.json"))
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

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
