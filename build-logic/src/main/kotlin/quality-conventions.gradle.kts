import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    id("com.diffplug.spotless")
    id("dev.detekt")
}

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

private val headerFile = rootProject.layout.projectDirectory.file("config/license-header.txt")
private val editorConfig = rootProject.layout.projectDirectory.file(".editorconfig")
private val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion

private val delimiter =
    "^\\s*(plugins|pluginManagement|import|buildscript|" +
            "dependencyResolutionManagement|enableFeaturePreview|include|rootProject)\\b"

configure<SpotlessExtension> {

    val commonExcludes =
        listOf(
            "**/build/**",
            "**/build-*/**",
            "**/.gradle/**",
            "**/.idea/**",
            "**/.git/**",
            "**/generated/**",
            "**/.gradle-test-kit/**",
        )

    kotlin {
        target("**/*.kt")
        targetExclude(commonExcludes)
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig).editorConfigOverride(
            mapOf(
                "ktlint_annotation_handle_annotations_with_parameters_same_as_annotations_without_parameters" to "disabled",
            )
        )
        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(headerFile)
        lineEndings = LineEnding.UNIX
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(commonExcludes)
        ktlint(ktlintVersion).setEditorConfigPath(editorConfig)
        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(headerFile, delimiter)
        lineEndings = LineEnding.UNIX
    }
    format("misc") {
        target(
            "**/*.md",
            "**/*.properties",
            "**/*.yml",
            "**/*.yaml",
            "**/*.xml",
            "**/.gitignore",
            "**/*.txt",
        )
        targetExclude(commonExcludes)
        trimTrailingWhitespace()
        endWithNewline()
        lineEndings = LineEnding.UNIX
    }
}

private val detektConfig = rootProject.layout.projectDirectory.file("config/detekt/detekt.yml")
private val detektBaseline =
    rootProject.layout.projectDirectory
        .file("config/detekt/detekt-baseline.xml")
        .asFile

configure<DetektExtension> {
    toolVersion = libs.findVersion("detekt").get().requiredVersion
    parallel = true
    buildUponDefaultConfig = true
    if (detektConfig.asFile.exists()) {
        config.setFrom(detektConfig)
    }
    baseline = detektBaseline
}

tasks.withType<Detekt>().configureEach {

    autoCorrect = false

    reports {
        checkstyle.required.set(true)
        html.required.set(true)
        sarif.required.set(true)
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck", "detekt")
}
