plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    jacoco
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

    testImplementation(platform("org.junit:junit-bom:5.12.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Aislar el classpath de Detekt a Kotlin 2.0.21 para evitar conflictos de versiones
configurations.matching { it.name in listOf("detekt", "detektPlugins") }.configureEach {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21",
            "org.jetbrains.kotlin:kotlin-reflect:2.0.21",
            "org.jetbrains.kotlin:kotlin-stdlib:2.0.21",
        )
    }
}


val defaultDetektConfigFile = layout.buildDirectory.file("detekt/default-detekt-config.yml")
val extractDetektConfig = tasks.register("extractDetektConfig") {
    outputs.file(defaultDetektConfigFile)
    doLast {
        val stream = javaClass.classLoader.getResourceAsStream("default-detekt-config.yml")
        if (stream != null) {
            val file = defaultDetektConfigFile.get().asFile
            file.parentFile.mkdirs()
            file.outputStream().use { stream.copyTo(it) }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    val customConfig = files("$rootDir/config/detekt/detekt.yml").filter { it.exists() }
    if (!customConfig.isEmpty) {
        config.setFrom(customConfig)
    } else {
        config.setFrom(files(defaultDetektConfigFile))
    }
    autoCorrect = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    val customConfig = files("$rootDir/config/detekt/detekt.yml").filter { it.exists() }
    if (customConfig.isEmpty) {
        dependsOn(extractDetektConfig)
    }
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set("1.5.0")
    filter {
        exclude("*.kts")
        exclude("**/*.kts")
    }
}

tasks.matching { it.name.contains("KotlinScript") }.configureEach {
    enabled = false
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

val hooksDir = file("${rootProject.rootDir}/hooks")
if (hooksDir.exists()) {
    val installGitHooks = rootProject.tasks.maybeCreate<Copy>("installGitHooks").apply {
        description = "Copies git hooks from /hooks to /.git/hooks with execution permissions"
        group = "git hooks"
        from("${rootProject.rootDir}/hooks")
        into("${rootProject.rootDir}/.git/hooks")
        filePermissions {
            user {
                read = true
                write = true
                execute = true
            }
            group {
                read = true
                execute = true
            }
            other {
                read = true
                execute = true
            }
        }
    }

    tasks.matching { it.name in listOf("compileKotlin", "check", "test") }.configureEach {
        dependsOn(installGitHooks)
    }
}
