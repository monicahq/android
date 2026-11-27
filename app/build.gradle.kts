plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.monicahq.android"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 2
            }
    }

    defaultConfig {
        applicationId = "com.monicahq.android"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    // Lists the languages of res/values-* in the system's per-app language setting (Android 13+).
    androidResources {
        generateLocaleConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        checkDependencies = true
        sarifReport = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Coverage gate for non-UI code (constitution, principle III). UI code is covered by the Compose
// UI tests, which run on a device and are not measured here.
kover {
    reports {
        filters {
            excludes {
                annotatedBy(
                    "androidx.compose.runtime.Composable",
                    "androidx.compose.ui.tooling.preview.Preview",
                )
                packages("com.monicahq.android.ui.theme")
                // Screen and sheet files hold only composables; their logic lives in other files.
                classes(
                    "com.monicahq.android.MainActivity",
                    "com.monicahq.android.MonicaApplication",
                    "*ScreenKt",
                    "*ScreenKt$*",
                    "*SheetKt",
                    "*SheetKt$*",
                    "*.R",
                    "*.R$*",
                    "*.BuildConfig",
                    "*ComposableSingletons*",
                )
            }
        }
        verify {
            rule {
                minBound(80)
            }
        }
    }
}
