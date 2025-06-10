plugins {
    java
    application
}

group = "me.kmathers.wrench"
version = "0.1.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

application {
    mainClass.set("me.kmathers.wrench.Wrench")
}

repositories {
    mavenCentral()
}