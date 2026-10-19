plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val uploadKeystore: String? = System.getenv("UPLOAD_KEYSTORE_PATH")

android {
    namespace = "com.memdroid.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.memdroid.app"
        minSdk = 31
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        // A fixed key checked into the repo so every CI build can install over the last one.
        // Used for the sideloaded APKs on GitHub releases only.
        create("memdroid") {
            storeFile = rootProject.file("signing/memdroid.keystore")
            storePassword = "memdroid"
            keyAlias = "memdroid"
            keyPassword = "memdroid"
        }
        // The private Play upload key. CI decodes it from GitHub secrets for the Play bundle.
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (uploadKeystore != null) "upload" else "memdroid")
        }
        debug {
            signingConfig = signingConfigs.getByName("memdroid")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
    buildFeatures {
        compose = true
    }
    // Lets people pick Memdroid's language in Android's per-app language settings.
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    testImplementation("junit:junit:4.13.2")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
