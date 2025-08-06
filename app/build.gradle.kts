import com.mudita.sentry.plugins.tasks.SentryReleaseTask
import com.mudita.sentry.plugins.tasks.model.AppMetadata
import com.mudita.sentry.plugins.util.generateSentryUuid
import tasks.DeployTask
import tasks.GenerateChangelogTask
import tasks.Versions
import tasks.Versions.APP_ID
import tasks.Versions.COMPILE_SDK
import tasks.Versions.MIN_SDK
import tasks.Versions.TARGET_SDK
import tasks.Versions.VERSION_CODE
import tasks.Versions.VERSION_NAME
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.mudita.sentry.plugins.release")
    id("app.cash.licensee")
}

android {
    namespace = "com.mudita.opencalculator"
    compileSdk = COMPILE_SDK

    val sentryDsn = localProperties(rootDir).getProperty("sentry_dsn", "")

    defaultConfig {
        applicationId = APP_ID
        minSdk = MIN_SDK
        targetSdk = TARGET_SDK
        versionCode = VERSION_CODE
        versionName = VERSION_NAME
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SENTRY_DSN", "\"${sentryDsn}\"")
        buildConfigField("String", "PROGUARD_UUID", "\"${generateSentryUuid()}\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "system-debug"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "system-debug"
            keyPassword = "android"
        }
    }

    buildTypes {
        val appName = APP_ID.split(".").last()
        setProperty("archivesBaseName", "${appName}-${VERSION_NAME}")

        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }

        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        create("qa") {
            initWith(buildTypes.getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

licensee {
    allow("Apache-2.0")
    allow("MIT")
    ignoreDependencies("com.mudita") {
        because("Internal dependency")
    }
}

dependencies {
    implementation(project(":frontitude"))

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("androidx.core:core-splashscreen:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.10")

    //Sentry
    implementation("com.mudita:sentry-sdk:0.0.61")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
    implementation("com.google.code.gson:gson:2.9.1")
}

fun localProperties(rootDir: File): Properties {
    val localPropertiesFile = File(rootDir, "local.properties")
    val localProperties = Properties()

    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    return localProperties
}

tasks.register("uploadApkToNexus", DeployTask::class) {
    versionName = VERSION_NAME
    tagPrefix = project.property("tagPrefix") as String? ?: "development"
    appName = APP_ID.split(".").last()

    nexusUrl = project.property("nexusUrl") as String? ?: ""
    nexusUsername = project.property("nexusUsername") as String? ?: ""
    nexusPassword = project.property("nexusPassword") as String? ?: ""
}

tasks.register("generateChangelog", GenerateChangelogTask::class) {
    appName = Versions.APP_ID.split(".").last()
    versionName = VERSION_NAME
    tagPrefix = project.findProperty("tagPrefix") as String? ?: "development"
}

tasks.register("checkVersion") {
    doFirst {
        val currentVersion = Versions.VERSION_NAME

        // Extracting the tag from the GITHUB_REF environment variable
        val githubRef = System.getenv("GITHUB_REF") ?: throw GradleException("GITHUB_REF not found.")
        // Example of githubRef: refs/tags/development.0.0.1

        val pattern = Regex("(release|development|qa)\\.(\\d+\\.\\d+\\.\\d+(-rc\\d+)?)")
        val matchResult = pattern.find(githubRef.removePrefix("refs/tags/"))
            ?: throw GradleException("The git tag does not follow the required 'type.x.y.z' pattern.")
        val tagVersion = matchResult.groupValues[2]

        if (currentVersion != tagVersion) {
            throw GradleException("The version in build.gradle.kts ($currentVersion) does not match the tag version ($tagVersion).")
        }
    }
}

tasks.named("createReleaseAndUploadMapping", SentryReleaseTask::class) {
    val variantOutput = project.android.applicationVariants
        .firstOrNull {
            gradle.startParameter.taskNames.any { taskName ->
                taskName.contains(
                    it.name,
                    ignoreCase = true
                )
            }
        } ?: throw IllegalStateException("No matching variant found for the task name.")

    val buildVariant = variantOutput.buildType.name.takeIf {
        it.equals("release", ignoreCase = true) || it.equals("qa", ignoreCase = true)
    } ?: throw IllegalStateException(
        "Invalid build variant: ${variantOutput.buildType.name}." +
                " Supported variants are: \"release\", \"qa\""
    )

    appMetadata = AppMetadata(
        buildVariant = buildVariant,
        packageName = variantOutput.applicationId,
        versionName = variantOutput.versionName,
        versionCode = variantOutput.versionCode.toString()
    )
}
