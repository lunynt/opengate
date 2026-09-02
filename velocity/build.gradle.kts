dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:4.1.1-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:4.1.1-SNAPSHOT")
}

base {
    archivesName.set("opengate-velocity")
}

tasks.jar {
    dependsOn(":common:jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { file -> if (file.isDirectory) file else zipTree(file) }
    })
}
