plugins { java }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":bridge-protocol"))
    compileOnly("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
    testImplementation(libs.junit)
    testImplementation("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.jar {
    archiveBaseName.set("CraftConnectBridge")
    archiveVersion.set("0.1.0")
    from(project(":bridge-protocol").extensions.getByType<SourceSetContainer>().getByName("main").output)
}
