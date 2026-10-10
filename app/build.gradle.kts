plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.roamly.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.roamly.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val rawSupabaseUrl = (project.findProperty("SUPABASE_URL") as? String)?.trim()
            ?: System.getenv("SUPABASE_URL")?.trim()
            ?: ""
        val rawSupabaseAnonKey = (project.findProperty("SUPABASE_ANON_KEY") as? String)?.trim()
            ?: System.getenv("SUPABASE_ANON_KEY")?.trim()
            ?: ""
        val googleWebClientId = (project.findProperty("GOOGLE_WEB_CLIENT_ID") as? String)?.trim()
            ?: System.getenv("GOOGLE_WEB_CLIENT_ID")?.trim()
            ?: ""

        if (rawSupabaseUrl.isEmpty() || rawSupabaseAnonKey.isEmpty()) {
            logger.warn(
                "[Roamly Warning] SUPABASE_URL or SUPABASE_ANON_KEY is missing/empty in gradle.properties or environment. " +
                "Supabase backend authentication will remain unconfigured until valid project credentials are provided."
            )
        }

        fun escapeStringForBuildConfig(value: String): String {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        }

        buildConfigField("String", "SUPABASE_URL", escapeStringForBuildConfig(rawSupabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", escapeStringForBuildConfig(rawSupabaseAnonKey))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", escapeStringForBuildConfig(googleWebClientId))
    }

    buildTypes {
        release {
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Networking & Serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Google Credential Manager & Google ID
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Firebase Authentication
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    // Play Services Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
}