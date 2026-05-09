import org.gradle.api.tasks.Exec

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}

val installGitHooks by tasks.registering(Exec::class) {
    group = "build setup"
    description = "Installs repository Git hooks from .githooks/."
    workingDir = rootDir
    commandLine("bash", ".githooks/install-hooks.sh")

    onlyIf {
        val installer = rootDir.resolve(".githooks/install-hooks.sh")
        val isCi = (System.getenv("CI") ?: "false").equals("true", ignoreCase = true)
        installer.exists() && rootDir.resolve(".git").exists() && !isCi
    }
}

tasks.matching { it.name == "dependencies" }.configureEach {
    dependsOn(installGitHooks)
}

subprojects {
    tasks.matching { it.name == "dependencies" || it.name == "androidDependencies" || it.name == "preBuild" }
        .configureEach {
            dependsOn(rootProject.tasks.named("installGitHooks"))
        }
}
