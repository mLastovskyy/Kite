pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // HMS SDK artifacts (hms flavor only)
        maven("https://developer.huawei.com/repo/")
    }
}

rootProject.name = "Kite"

include(":core")
include(":app-parent")
include(":app-child")
include(":filter")
