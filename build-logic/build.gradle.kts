plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // AGP types are needed to wire generated source into Android variants.
    // compileOnly so build-logic stays a plain JVM build (no Android compile pulled in).
    compileOnly("com.android.tools.build:gradle:8.7.3")

    // Free-form JSON parsing via kotlinx-serialization's runtime JsonElement API.
    // No Kotlin compiler plugin required because we parse to JsonElement, not @Serializable classes.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
}

gradlePlugin {
    plugins {
        create("localizationCodegen") {
            id = "nyasa.localization-codegen"
            implementationClass = "com.nyasa.notifybridge.loc.LocalizationCodegenPlugin"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
