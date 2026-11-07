// QkJump —— 独立工程，和 V1 的 QrJump 没有任何代码共享，装成两个不同的应用。
// 目的只有一个：打开本应用 = 直奔胖乖生活自己的扫一扫页。
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "QkJump"
include(":app")
