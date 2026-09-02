dependencies {
    implementation(project(":paper"))
}

base {
    archivesName.set("opengate-bukkit")
}

tasks.jar {
    dependsOn(":paper:jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { file -> if (file.isDirectory) file else zipTree(file) }
    })
}
