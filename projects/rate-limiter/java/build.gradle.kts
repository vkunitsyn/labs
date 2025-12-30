plugins {
    application
    id("com.diffplug.spotless") version "8.1.0"
}

group = "io.github.vkunitsyn"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

spotless {
    java {
        // googleJavaFormat("1.33.0")
        trimTrailingWhitespace()
        palantirJavaFormat()
        endWithNewline()
        target("src/**/*.java")
    }

    kotlinGradle {
        ktlint()
    }

    format("misc") {
        target(
            "*.md",
            ".gitignore",
            ".gitattributes",
            ".editorconfig",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.check {
    dependsOn(tasks.spotlessCheck)
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
    useJUnitPlatform()
}

application {
    mainClass = "io.github.vkunitsyn.RateLimiterDemo"
}
