plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:4.1.1-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:4.1.1-SNAPSHOT")
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
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}

tasks.assemble { dependsOn(tasks.shadowJar) }
