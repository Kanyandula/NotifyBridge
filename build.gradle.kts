plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs the local static quality gate."
    dependsOn(":app:detekt", ":app:lintDebug")
}

tasks.register<Exec>("installGitHooks") {
    group = "verification"
    description = "Configures this repository to use the committed Git hooks."
    commandLine("git", "config", "core.hooksPath", ".githooks")
}
