// 顶层构建脚本。插件版本与 V1 保持一致，本机已缓存的依赖可直接复用，不用重新下载。
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
