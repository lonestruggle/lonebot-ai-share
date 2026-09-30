plugins {
    java
}

allprojects {
    group = "com.lonebot"
    version = "0.1.0"

    repositories {
        mavenCentral()
        maven("https://repo.runelite.net")
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}
