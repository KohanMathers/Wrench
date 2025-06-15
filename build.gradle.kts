plugins {
    java
    application
}

group = "me.kmathers.wrench"
version = "0.3.8"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

application {
    mainClass.set("me.kmathers.wrench.Wrench")
}

repositories {
    mavenCentral()
}