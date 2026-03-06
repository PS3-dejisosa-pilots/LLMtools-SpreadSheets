import java.io.ByteArrayOutputStream

plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.3.3"
	id("io.spring.dependency-management") version "1.1.6"

	kotlin("plugin.serialization") version "1.9.24"
}

group = "moros"
version = "0.1.0"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "1.1.2"

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.springframework.ai:spring-ai-starter-model-openai")
	implementation("org.springframework.boot:spring-boot-starter-logging")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	implementation("com.formdev:flatlaf:3.5.1")
	implementation("com.formdev:flatlaf-extras:3.5.1")
	implementation("com.github.weisj:jsvg:1.6.0")
	implementation("org.swinglabs.swingx:swingx-all:1.6.5-1")
	implementation("com.formdev:flatlaf-swingx:3.5.1")

	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
	implementation("org.apache.httpcomponents.client5:httpclient5")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// ----------------------------------------------------------------------------
// Generate standalone executable package Tasks
// ----------------------------------------------------------------------------

tasks.getByName<org.springframework.boot.gradle.plugin.ResolveMainClassName>("resolveMainClassName") {
    configuredMainClassName.set("moros.asf.tools.forspreadsheets.LLMToolsForSpreadsheetsJavaEditionKt")
}

// Generate exe dir
tasks.register("genExeDir") {
	doLast {
		// Generate exe dir
        val exeDir = File("exe")

        if (exeDir.exists()) {
			exeDir.deleteRecursively()
		}

        exeDir.mkdir()
	}
}

// Copy JRE
tasks.register<Copy>("copyJRE") {
	dependsOn(tasks.named("genExeDir"))

	if (File("launcher/jre.zip").exists()) {
        from(zipTree("launcher/jre.zip"))
		into("exe")
	}
}

// Copy config files
tasks.register("copyConf") {
	dependsOn(tasks.named("genExeDir"))
	doLast {
		// Generate exe/conf dir
        val confDir = File("exe/conf")

        if (confDir.exists()) {
			confDir.deleteRecursively()
		}

        confDir.mkdir()

		// Copy conf files
		copy {
			from("launcher/application.properties")
			into("exe/conf")
		}
	}
}

// Copy bat files
tasks.register("copyBat") {
	dependsOn(tasks.named("genExeDir"))
	doLast {
		// Generate exe/bat dir
        val batDir = File("exe/bat")

        if (batDir.exists()) {
			batDir.deleteRecursively()
		}

        batDir.mkdir()

		// Copy bat files
        val batFilesPatterns = Action<CopySpec> {
            include("*.ps1")
		}

		copy {
			from("bat", batFilesPatterns)
			into("exe/bat")
		}
	}
}

// Copy application
tasks.register("copyApp") {
    dependsOn(tasks.named("bootJar"))
	dependsOn(tasks.named("genExeDir"))
	doLast {
		// Copy Application JAR
		copy {
			from("build/libs/LLMToolsForSpreadsheets-0.1.0.jar")
			into("exe")
		}

		// Copy Launch BAT file
		copy {
			from("launcher/LaunchLLMtoolsForSpreadsheets.ps1")
			into("exe")
		}

		copy {
			from("launcher/LLMtoolsForSpreadsheets.bat")
			into("exe")
		}

		// Copy README
		copy {
			from("launcher/README.md")
			into("exe")
		}

		copy {
			from("launcher")
			into("exe")
			include("*.png")
		}
	}
}

// Generate standalone executable package
tasks.register("genExe") {
	dependsOn(tasks.named("copyJRE"))
	dependsOn(tasks.named("copyApp"))
	dependsOn(tasks.named("copyBat"))
	dependsOn(tasks.named("copyConf"))
}
