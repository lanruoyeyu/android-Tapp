plugins {
    id("com.android.application") version "8.3.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}

// 项目路径含中文（e:\小程序\...），Windows 下 Gradle test worker 通过
// arg file 传递 classpath（UTF-8 写入、JDK 以 GBK 解码）会导致路径乱码、
// 测试类 ClassNotFoundException。将 build 目录重定向到 ASCII 路径规避。
allprojects {
    val buildRoot = file("F:/software/app-build")
    layout.buildDirectory.set(java.io.File(buildRoot, name))
}
