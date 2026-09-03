plugins {
    `java-library`
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.2")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
}
