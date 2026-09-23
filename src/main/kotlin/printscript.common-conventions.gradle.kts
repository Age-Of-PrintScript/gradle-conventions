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


val detektConfigFile = rootProject.layout.buildDirectory.file("detekt/detekt.yml").get().asFile
if (!detektConfigFile.exists()) {
    detektConfigFile.parentFile.mkdirs()
    val stream = javaClass.classLoader.getResourceAsStream("detekt.yml")
    if (stream != null) {
        stream.use { input ->
            detektConfigFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(detektConfigFile))
    autoCorrect = true
}

// Extraer .editorconfig centralizado si el microservicio no tiene uno
val editorConfigFile = file("${rootProject.rootDir}/.editorconfig")
if (!editorConfigFile.exists()) {
    javaClass.classLoader.getResourceAsStream(".editorconfig")?.use { input ->
        editorConfigFile.outputStream().use { output ->
            input.copyTo(output)
        }
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
