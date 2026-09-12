plugins {
    id("com.gradleup.shadow")
}

val oldestSpigotApiVersion = "1.21-R0.1-20240807.214924-87"
val latestSpigotApiVersion = "26.2-R0.1-20260816.205300-12"
val spigotApiVersion = providers.gradleProperty("spigotApiVersion").getOrElse(oldestSpigotApiVersion)

val oldestSpigotApi = configurations.create("oldestSpigotApi") {
    isCanBeConsumed = false
}
val latestSpigotApi = configurations.create("latestSpigotApi") {
    isCanBeConsumed = false
}

dependencies {
    implementation(project(":common"))
    compileOnly("org.spigotmc:spigot-api:$spigotApiVersion")
    compileOnly("org.jetbrains:annotations-java5:24.1.0")
    add(oldestSpigotApi.name, "org.spigotmc:spigot-api:$oldestSpigotApiVersion")
    add(latestSpigotApi.name, "org.spigotmc:spigot-api:$latestSpigotApiVersion")
}

val verifySupportedSpigotApis = tasks.register("verifySupportedSpigotApis") {
    inputs.files(oldestSpigotApi, latestSpigotApi)
}

base {
    archivesName.set("opengate-paper")
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    dependsOn(":common:jar")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module", "META-INF/io.netty.versions.properties")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    append("META-INF/io.netty.versions.properties")
    failOnDuplicateEntries = true
    mergeServiceFiles()
    relocate("io.lettuce", "dev.lunynt.opengate.lib.lettuce")
    relocate("io.netty", "dev.lunynt.opengate.lib.netty")
    relocate("reactor", "dev.lunynt.opengate.lib.reactor")
    relocate("org.reactivestreams", "dev.lunynt.opengate.lib.reactivestreams")
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/LICENSE*", "META-INF/NOTICE*")
}

tasks.assemble { dependsOn(tasks.shadowJar) }
tasks.check { dependsOn(verifySupportedSpigotApis) }
