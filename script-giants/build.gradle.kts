plugins {
    `java-library`
}

dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":sdk"))
    compileOnly(project(":script-imp-killer"))
    compileOnly(libs.runelite.api)
    compileOnly(libs.runelite.client)
    compileOnly(libs.slf4j.api)
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("giants")
    archiveVersion.set("")
}
