pluginManagement {
    includeBuild("build-logic")
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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "opal"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":baselineprofile")
include(":core:model")
include(":core:data")
include(":core:tunnel")
include(":core:designsystem")
include(":feature:home")
include(":feature:apps")
include(":feature:connection")
include(":feature:settings")
include(":feature:onboarding")
