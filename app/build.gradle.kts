import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Upload-key material for the release build. It is never in the repo:
 * `keystore.properties` and every `*.jks` / `*.keystore` are gitignored, and CI
 * supplies the same four values as environment variables instead.
 *
 * When nothing is configured — a fresh clone, a contributor's machine, a debug
 * build — this resolves to null and the release build stays UNSIGNED rather
 * than failing. That keeps `assembleRelease` and `lintRelease` runnable by
 * anyone, which is how the repo has worked from the start.
 */
val REQUIRED_SIGNING_KEYS = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

val releaseSigning: Map<String, String>? = run {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        val props = Properties().apply { file.inputStream().use { load(it) } }
            .entries.associate { (k, v) -> k.toString() to v.toString() }
        // The file exists, so signing was clearly intended. A missing or blank
        // value here is a typo, not a choice — fail rather than fall through to
        // an unsigned artifact that only Play will reject, hours later.
        val missing = REQUIRED_SIGNING_KEYS.filter { props[it].isNullOrBlank() }
        require(missing.isEmpty()) {
            "keystore.properties is present but incomplete — missing or blank: " +
                "${missing.joinToString()}. Fill it in (see keystore.properties.example), " +
                "or delete the file to build unsigned."
        }
        props
    } else {
        val fromEnv = mapOf(
            "storeFile" to System.getenv("HADRON_KEYSTORE_FILE"),
            "storePassword" to System.getenv("HADRON_KEYSTORE_PASSWORD"),
            "keyAlias" to System.getenv("HADRON_KEY_ALIAS"),
            "keyPassword" to System.getenv("HADRON_KEY_PASSWORD"),
        ).filterValues { !it.isNullOrBlank() }.mapValues { it.value!! }
        if (REQUIRED_SIGNING_KEYS.all { fromEnv[it] != null }) fromEnv else null
    }
}

android {
    namespace = "com.hadronmemory.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hadronmemory.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // The OAuth redirect scheme, referenced from the manifest's
        // intent-filter. The server's DCR validator requires scheme +
        // non-empty authority, so the registered URI is the double-slash
        // form "<scheme>://oauth-callback" — the Apple-style single-slash
        // form is rejected (see HadronClientConfig).
        manifestPlaceholders["oauthRedirectScheme"] = "com.hadronmemory.android"
    }

    signingConfigs {
        releaseSigning?.let { creds ->
            create("release") {
                storeFile = rootProject.file(creds.getValue("storeFile"))
                storePassword = creds.getValue("storePassword")
                keyAlias = creds.getValue("keyAlias")
                keyPassword = creds.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Null when no upload key is configured — the artifact is then
            // unsigned, which is fine for everything except a Play upload.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Built-in Kotlin derives kotlin.compilerOptions.jvmTarget from this.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":hadronkit"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
