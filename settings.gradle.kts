// ✅ Gradle Plugin 사용을 위한 저장소 정의
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()         // ✅ MPAndroidChart 등 외부 라이브러리
        gradlePluginPortal()   // ✅ Kotlin, Compose 등 플러그인
    }
}

// ✅ 의존성 다운로드를 위한 저장소 정의
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()         // ✅ 꼭 필요: MPAndroidChart 등
        maven { url = uri("https://jitpack.io") } // ✅ JitPack 저장소 추가
    }
}

rootProject.name = "SimpleEnglishDictation" // ✅ 앱 이름에 맞게 수정
include(":app")
