pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
    // 2.3.x 的插件标记只有 pom、没有 gradle module，Android Studio 会报 Plugin was not found。
    // 直接用带 jar 的 symbol-processing 构件。
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.devtools.ksp") {
                val version = requested.version ?: return@eachPlugin
                useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:$version")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
    }
}

rootProject.name = "Ainsmail"
include(":app")
