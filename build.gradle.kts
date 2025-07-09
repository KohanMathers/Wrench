plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.0"
    id ("org.jetbrains.kotlin.jvm") version "1.9.0"
}

group = "org.wrench"
version = "1.0-DEV"

repositories {
    mavenCentral()
    maven {
        name = "asordaPublic"
        url = uri("https://mvn.everbuild.org/public")
    }
}

dependencies {
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("net.minestom:minestom-snapshots:4fe2993057")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.everbuild.blocksandstuff:blocksandstuff-fluids:1.2.0-SNAPSHOT")
    implementation("org.everbuild.blocksandstuff:blocksandstuff-blocks:1.2.0-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "20"
}

tasks {
    jar {
        manifest {
            attributes["Main-Class"] = "org.wrench.Main"
        }
    }

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("")
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "20"
    }
}
