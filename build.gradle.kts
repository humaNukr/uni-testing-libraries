plugins {
    java
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "ua.edu.kma"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}

pitest {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(listOf("ua.edu.kma.*"))
    mutators.set(listOf("DEFAULTS"))
}
