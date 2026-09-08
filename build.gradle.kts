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
        maven {
            name = "ajg0702"
            url = uri("https://repo.ajg0702.us/releases/")
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
        options.release = if (project.name == "velocity") 25 else 21
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
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
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
                check(entries.size == entries.toSet().size) { "$jar contains duplicate ZIP entries" }
                val drivers = setOf("org.sqlite.JDBC", "org.postgresql.Driver", "com.mysql.cj.jdbc.Driver",
                    "org.mariadb.jdbc.Driver", "org.h2.Driver")
                val driverService = zip.getEntry("META-INF/services/java.sql.Driver")
                    ?: error("$jar is missing JDBC service registration")
                val registeredDrivers = zip.getInputStream(driverService).bufferedReader().use { reader ->
                    reader.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
                }
                check(registeredDrivers.containsAll(drivers)) { "$jar is missing JDBC driver registrations" }
                drivers.forEach { driver ->
                    check(driver.replace('.', '/') + ".class" in entries) { "$jar is missing $driver" }
                }
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
                check(entries.any { it.startsWith("dev/lunynt/opengate/lib/lettuce/") }) {
                    "$jar does not contain its isolated Redis client"
                }
                check(entries.none { it.startsWith("io/lettuce/") || it.startsWith("io/netty/") }) {
                    "$jar exposes Redis networking classes to the platform classpath"
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
