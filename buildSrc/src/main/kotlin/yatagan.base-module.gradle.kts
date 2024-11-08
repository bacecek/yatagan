plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjvm-default=all-compatibility",
            "-Werror",
        )
    }

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }

    sourceSets.configureEach {
        languageSettings {
            optIn("kotlin.ExperimentalStdlibApi")
            optIn("kotlin.contracts.ExperimentalContracts")
        }
    }
}

val yataganVersion: String by extra(
    providers.fileContents(rootProject.layout.projectDirectory.file("yatagan.version"))
        .asText.get().trim()
)

val enableCoverage: Boolean by extra(
    providers.gradleProperty("enable_coverage").orNull.toBoolean()
)
