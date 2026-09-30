plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":sdk"))
    compileOnly(libs.runelite.api)
    compileOnly(libs.runelite.client)
    compileOnly(libs.slf4j.api)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("imp-killer")
    archiveVersion.set("")
}
