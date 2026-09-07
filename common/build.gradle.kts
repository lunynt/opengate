plugins {
    `java-library`
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("com.mysql:mysql-connector-j:9.5.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation("com.h2database:h2:2.4.240")
    implementation("io.lettuce:lettuce-core:6.7.1.RELEASE")
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.2")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    compileOnly("us.ajg0702.queue.api:api:2.9.1")
}
