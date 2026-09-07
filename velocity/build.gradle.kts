plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:4.1.2-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:4.1.2-SNAPSHOT")
}

base {
    archivesName.set("opengate-velocity")
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
