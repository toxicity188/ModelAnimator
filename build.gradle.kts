plugins {
    `java-library`
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version("8.1.1")
    id("io.papermc.paperweight.userdev") version "1.5.12" apply false
}

val api = project(":api")
val dist = project(":dist")
val scheduler = project(":scheduler")

val minecraftVersion = "1.20.4"
val adventureVersion = "4.16.0"
val platformVersion = "4.3.2"

val nmsVersions = listOf(
    "v1_20_R3"
)

val shadedDependencies = listOf(
    "com.ticxo.playeranimator:PlayerAnimator:R1.2.8"
)

allprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")

    group = "kr.toxicity.animator"
    version = "1.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://mvn.lumine.io/repository/maven/")
    }

    dependencies {
        compileOnly("com.ticxo.modelengine:ModelEngine:R4.0.4")
    }

    tasks {
        compileJava {
            options.encoding = Charsets.UTF_8.name()
        }
        javadoc {
            options.encoding = Charsets.UTF_8.name()
        }
    }
}

fun Project.setupDependencies() {
    dependencies {
        shadedDependencies.forEach { dependency ->
            compileOnly(dependency)
        }
    }
}

nmsVersions.forEach {
    val project = project(":nms:$it")
    project.apply(plugin = "io.papermc.paperweight.userdev")
    project.dependencies {
        compileOnly(api)
    }
    project.setupDependencies()
}

dist.dependencies {
    compileOnly(api)
    scheduler.subprojects.forEach {
        compileOnly(it)
    }
    nmsVersions.forEach { nms ->
        compileOnly(project(":nms:$nms"))
    }
}

dist.tasks.processResources {
    filteringCharset = Charsets.UTF_8.name()
    val props = mapOf(
        "version" to project.version,
        "adventure" to adventureVersion,
        "platform" to platformVersion
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

listOf(api, dist, scheduler.project("standard") {
    dependencies {
        compileOnly(api)
    }
}).forEach {
    it.dependencies {
        compileOnly("net.kyori:adventure-api:$adventureVersion")
        compileOnly("net.kyori:adventure-platform-bukkit:$platformVersion")

        compileOnly("org.spigotmc:spigot-api:$minecraftVersion-R0.1-SNAPSHOT")
    }
    it.setupDependencies()
}

dependencies {
    implementation(api)
    implementation(dist)
    scheduler.subprojects.forEach {
        implementation(it)
    }
    shadedDependencies.forEach { dependency ->
        implementation(dependency)
    }
    nmsVersions.forEach {
        implementation(project(":nms:$it", configuration = "reobf"))
    }
}

scheduler.project("folia") {
    dependencies {
        compileOnly(api)
        compileOnly("dev.folia:folia-api:$minecraftVersion-R0.1-SNAPSHOT")
        setupDependencies()
    }
}


tasks {
    jar {
        dependsOn(clean)
        finalizedBy(shadowJar)
    }
    shadowJar {
        nmsVersions.forEach {
            dependsOn(":nms:$it:reobfJar")
        }
        archiveClassifier = ""
        fun prefix(pattern: String) {
            relocate(pattern, "${project.group}.shaded.$pattern")
        }
        dependencies {
            exclude(dependency("org.jetbrains:annotations:13.0"))
        }
        prefix("kotlin")
        prefix("org.apache.commons.io")
        shadedDependencies.map {
            it.substringBefore(':')
        }.distinct().forEach {
            prefix(it)
        }
    }
}

val targetJavaVersion = 17

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
}

kotlin {
    jvmToolchain(targetJavaVersion)
}