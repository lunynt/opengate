dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

tasks.jar {
    dependsOn(":common:jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { file -> if (file.isDirectory) file else zipTree(file) }
    })
}
