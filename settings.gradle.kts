pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 오닉스(BOOX) 이잉크 화면 제어 도구 — sam 10 Plus 같은 오닉스 계열 기기에서 고화질(Regal) 모드 요청용
        maven { url = uri("https://repo.boox.com/repository/maven-public/") }
    }
}

rootProject.name = "EbookReader"
include(":app")
