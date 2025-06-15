plugins {
    java
    application
}

group = "me.kmathers.wrench"
version = "0.4.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

application {
    mainClass.set("me.kmathers.wrench.Wrench")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-common:4.1.107.Final")
}