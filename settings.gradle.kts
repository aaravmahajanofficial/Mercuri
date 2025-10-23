pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.danilopianini.gradle-pre-commit-git-hooks") version "2.1.3"
}

gitHooks {
    commitMsg { conventionalCommits() }
    createHooks()
}

rootProject.name = "Mercuri"
