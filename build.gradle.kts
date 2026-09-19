plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

val minecraftVersion = project.property("minecraft_version") as String
val loaderVersion = project.property("loader_version") as String
val modmenuVersion = project.property("modmenu_version") as String
val jdkVersion = 25

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName = project.property("archives_base_name") as String
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    // 不需要 Fabric API：生命周期/键位/HUD 都用 Mixin 自己接（见 mixin 包）

    // ModMenu 只用于编译期（配置界面入口），运行期可选
    compileOnly("maven.modrinth:modmenu:$modmenuVersion")
    // 开发环境里真的装上它，好把入口跑通验证
    localRuntime("maven.modrinth:modmenu:$modmenuVersion")

    // 纯 Java 的 MP3 解码器（没有 ffmpeg 时的 fallback），会被打进模组 jar
    implementation("javazoom:jlayer:1.0.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jdkVersion))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(jdkVersion)
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to minecraftVersion,
        "loader_version" to loaderVersion,
        "loader_min_version" to project.property("loader_min_version").toString(),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}

// 把 JLayer 的类直接打进模组 jar（它不是 mod，Fabric 的 jar-in-jar 不会加载它）
tasks.jar {
    from(configurations.runtimeClasspath.get()
        .filter { it.name.startsWith("jlayer") }
        .map { zipTree(it) }) {
        exclude("META-INF/**")
    }
}
