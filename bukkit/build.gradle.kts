plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":paper"))
}

base {
    archivesName.set("opengate-bukkit")
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    dependsOn(":paper:jar")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}

tasks.assemble { dependsOn(tasks.shadowJar) }
