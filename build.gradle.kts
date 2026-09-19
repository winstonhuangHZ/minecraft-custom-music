plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

val minecraftVersion = project.property("minecraft_version") as String
val loaderVersion = project.property("loader_version") as String
val fabricApiVersion = project.property("fabric_api_version") as String
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

    // 只取需要的 Fabric API 模块。
    // 注意：Loom 1.17 起不再有 modImplementation/modCompileOnly，
    // implementation/compileOnly 本身就会对 mod 依赖做重映射。
    listOf(
        "fabric-api-base",
        "fabric-lifecycle-events-v1",
        "fabric-key-mapping-api-v1",
        "fabric-resource-loader-v1",
        "fabric-rendering-v1",
    ).forEach { implementation(fabricApi.module(it, fabricApiVersion)) }

    // ModMenu 只用于编译期（配置界面入口），运行期可选
    compileOnly("maven.modrinth:modmenu:$modmenuVersion")
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
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
}
