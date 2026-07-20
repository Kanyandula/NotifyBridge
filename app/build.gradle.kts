plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.detekt)
    id("nyasa.localization-codegen")
}
android {
    namespace = "com.nyasa.notifybridge"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.nyasa.notifybridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.nyasa.notifybridge.HiltTestRunner"
        // Restrict bundled locales to the four we ship — avoids pulling in
        // AppCompat's full locale baggage. Add new tags here in lockstep with
        // app/src/main/assets/localization/<tag>/strings.json.
        resourceConfigurations += setOf("en", "fr", "es", "pt")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Local/internal-testing signing only (debug keystore) so a release
            // build — which, unlike debug, includes MqttForegroundService /
            // NotifListenerService / BootReceiver — is installable for the
            // Task 25 manual checklist. NOT a production signing identity.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        // Non-actionable, network-driven nag that fails the build whenever a
        // newer AGP publishes upstream — can't be baselined (version string
        // moves each release). Disable rather than chase it.
        disable += "AndroidGradlePluginVersion"
        baseline = file("lint-baseline.xml")
        htmlReport = true
        xmlReport = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}
// jlink in JDK 18+ (GraalVM 23 is the machine default) cannot process AGP's
// core-for-system-modules.jar. Pin all JVM tasks to a JDK 17 toolchain so the
// build is reproducible regardless of which JDK runs the Gradle daemon.
kotlin {
    jvmToolchain(17)
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
    parallel = true
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.hivemq.mqtt)
    implementation(libs.work.runtime)
    implementation(libs.biometric)
    implementation(libs.appcompat)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test.junit)
    debugImplementation(libs.compose.test.manifest)
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.52")
    detektPlugins(libs.detekt.formatting)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
        txt.required.set(false)
    }
    exclude("**/build/**")
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
}
