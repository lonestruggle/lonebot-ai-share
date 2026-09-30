rootProject.name = "lonebot-client"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://repo.runelite.net")
    }
}

include("api", "sdk", "client", "example-plugin", "script-imp-killer", "script-woodcutter", "script-fishing", "script-imps2", "script-star-miner", "script-giants", "script-clue", "script-quest")

