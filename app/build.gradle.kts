plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // No signingConfig: release artifacts are signed by the owner's
            // upload key / Play App Signing, not from this repo.
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
