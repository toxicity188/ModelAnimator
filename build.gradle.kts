plugins {
    `java-library`
    kotlin("jvm") version "2.0.21"
    id("io.github.goooler.shadow") version "8.1.8"
    id("io.papermc.paperweight.userdev") version "1.7.4" apply false
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

val minecraftVersion = "1.21.3"
val adventureVersion = "4.17.0"
val platformVersion = "4.3.4"

val targetJavaVersion = 21

allprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")

    group = "kr.toxicity.animator"
    version = "1.1"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://mvn.lumine.io/repository/maven/")
    }

    dependencies {
        compileOnly("com.ticxo.modelengine:ModelEngine:R4.0.7")
    }

    tasks {
        compileJava {
            options.compilerArgs.addAll(listOf("-source", "17", "-target", "17"))
            options.encoding = Charsets.UTF_8.name()
        }
        compileKotlin {
            compilerOptions {
                freeCompilerArgs.addAll(listOf("-jvm-target", "17"))
            }
        }
    }

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
}

fun Project.legacy() = also { p ->
    p.java.toolchain.languageVersion = JavaLanguageVersion.of(17)
}

val api = project("api").legacy()
val dist = project("dist")
val scheduler = project(":scheduler")

val shadedDependencies = listOf(
    "com.ticxo.playeranimator:PlayerAnimator:R1.2.8"
)

fun Project.setupDependencies() {
    dependencies {
        shadedDependencies.forEach { dependency ->
            compileOnly(dependency)
        }
    }
}

val legacyNMSVersion = listOf(
    "v1_19_R3",
    "v1_20_R1",
    "v1_20_R2",
    "v1_20_R3"
).map {
    project("nms:$it").legacy()
}
val currentNMSVersion = listOf(
    "v1_20_R4",
    "v1_21_R1",
    "v1_21_R2"
).map {
    project("nms:$it")
}

val allNMSVersion = (legacyNMSVersion + currentNMSVersion).onEach {
    it.apply(plugin = "io.papermc.paperweight.userdev")
    it.dependencies {
        compileOnly(api)
    }
    it.setupDependencies()
}

dist.dependencies {
    compileOnly(api)
    scheduler.subprojects.forEach {
        compileOnly(it)
    }
    allNMSVersion.forEach { nms ->
        compileOnly(nms)
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
    allNMSVersion.forEach {
        implementation(project("nms:${it.name}", configuration = "reobf"))
    }
}

scheduler.project("folia") {
    setupDependencies()
    dependencies {
        compileOnly(api)
        compileOnly("io.papermc.paper:paper-api:$minecraftVersion-R0.1-SNAPSHOT")
    }
}


tasks {
    jar {
        dependsOn(clean)
        finalizedBy(shadowJar)
    }
    shadowJar {
        allNMSVersion.forEach {
            dependsOn(it.tasks.named("reobfJar"))
        }
        manifest {
            attributes["paperweight-mappings-namespace"] = "spigot"
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
    runServer {
        version(minecraftVersion)
    }
}
