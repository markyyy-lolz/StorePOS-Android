import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localProperty(name: String): String {
    val fallback = when (name) {
        "SUPABASE_URL" -> "https://qgyzdoltjlryjthxxscw.supabase.co"
        "SUPABASE_PUBLISHABLE_KEY" -> "sb_publishable_mCjtfE-W75s1yyUdw2NY2g_z6ic5DIc"
        else -> ""
    }
    return localProperties.getProperty(name, fallback)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
}

fun quotedLocalProperty(name: String): String = "\"${localProperty(name)}\""

val releaseStorePath = System.getenv("STOREPOS_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("STOREPOS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("STOREPOS_KEY_ALIAS")
val releaseKeyPassword = System.getenv("STOREPOS_KEY_PASSWORD")
val releaseSigningAvailable =
    !releaseStorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.storepos.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.storepos.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.3.2"

        buildConfigField("String", "SUPABASE_URL", quotedLocalProperty("SUPABASE_URL"))
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            quotedLocalProperty("SUPABASE_PUBLISHABLE_KEY")
        )
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}
