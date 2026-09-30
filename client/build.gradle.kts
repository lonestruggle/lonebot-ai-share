plugins {
    application
}

dependencies {
    implementation(project(":api"))
    implementation(project(":sdk"))
    implementation(project(":example-plugin"))
    implementation(project(":script-imp-killer"))
    implementation(project(":script-woodcutter"))
    implementation(project(":script-fishing"))
    implementation(project(":script-imps2"))
    implementation(project(":script-star-miner"))
    implementation(project(":script-giants"))
    implementation(project(":script-clue"))
    implementation(project(":script-quest"))

    implementation(libs.runelite.client)
    implementation(libs.runelite.api)
    implementation(libs.slf4j.simple)
}

application {
    // Default: standalone launcher (geen game). Sub-client via runClient / Start geselecteerd.
    mainClass.set("com.lonebot.launcher.LoneBotLauncherMain")
    applicationDefaultJvmArgs = listOf("-Dlonebot.brand=LoneBot", "-Dlonebot.role=launcher")
}

tasks.named<JavaExec>("run") {
    jvmArgs("-Dlonebot.brand=LoneBot", "-Dlonebot.role=launcher")
}

/** Game-client entry (sub-proces). */
tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Start één LoneBot game-client (RuneLite shell)"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.lonebot.client.LoneBotMain")
    jvmArgs("-ea", "-Dlonebot.brand=LoneBot", "-Dlonebot.role=client")
}
