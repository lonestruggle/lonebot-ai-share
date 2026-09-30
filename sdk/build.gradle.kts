dependencies {
    api(project(":api"))
    compileOnly(libs.runelite.api)
    compileOnly(libs.runelite.client)
    implementation(libs.slf4j.api)
    implementation(libs.gson)
}
