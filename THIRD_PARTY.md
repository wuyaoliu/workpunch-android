# 第三方依赖

本仓库使用 Gradle 从 Google Maven、Maven Central 与 Gradle Plugin Portal 获取依赖，
没有将这些库的源代码纳入本项目的 MIT 授权。

主要依赖包括：

- AndroidX Activity、Compose、Material 3、Lifecycle、Navigation、Room、Core 与 AppCompat。
- Kotlin Android / Compose 编译插件。
- Kotlin Symbol Processing (KSP)。
- JUnit 4（单元测试）。
- Android Gradle Plugin 与 Gradle Wrapper（构建工具）。

版本以 `app/build.gradle.kts`、`build.gradle.kts` 和
`gradle/wrapper/gradle-wrapper.properties` 为准。分发修改版或安装包时，需遵循各依赖
自身的许可证与声明；本文件不替代依赖自带的 LICENSE / NOTICE。
