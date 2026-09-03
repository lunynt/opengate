import java.net.URLClassLoader
import java.util.zip.ZipFile

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1" apply false
}

allprojects {
    group = "dev.lunynt.opengate"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "spigot"
            url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        }
        maven {
            name = "opencollab"
            url = uri("https://repo.opencollab.dev/main/")
        }
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))
    }

    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.13.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

val platformProjects = listOf("paper", "bukkit", "velocity", "bungee")
val distributableJars = platformProjects.map { projectName ->
    project(":$projectName").layout.buildDirectory.file("libs/opengate-$projectName-${project.version}.jar")
}

val verifyPluginJars = tasks.register("verifyPluginJars") {
    group = "verification"
    description = "Checks that every distributable plugin JAR is self-contained and loadable."
    dependsOn(platformProjects.map { ":$it:shadowJar" })
    inputs.files(distributableJars)

    doLast {
        inputs.files.files.sortedBy { it.name }.forEach { jar ->
            ZipFile(jar).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                check(entries.none { it.matches(Regex("META-INF/.*\\.(SF|RSA|DSA)", RegexOption.IGNORE_CASE)) }) {
                    "$jar contains dependency signatures"
                }
                check("dev/lunynt/opengate/crypto/Argon2idPasswordHasher.class" in entries) {
                    "$jar does not contain the shared authentication core"
                }
                check(entries.any { it.startsWith("org/bouncycastle/") }) {
                    "$jar does not contain its password-hashing provider"
                }
                check(entries.any { it.startsWith("org/sqlite/") }) {
                    "$jar does not contain its database driver"
                }
            }

            URLClassLoader(arrayOf(jar.toURI().toURL()), null).use { loader ->
                val type = loader.loadClass("dev.lunynt.opengate.crypto.Argon2idPasswordHasher")
                val hasher = type.getConstructor().newInstance()
                val password = "artifact-smoke-test".toCharArray()
                val encoded = type.getMethod("hash", CharArray::class.java).invoke(hasher, password) as String
                check(type.getMethod("verify", CharArray::class.java, String::class.java)
                    .invoke(hasher, password, encoded) == true) { "$jar failed its password-hasher smoke test" }
                password.fill('\u0000')
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyPluginJars)
}
