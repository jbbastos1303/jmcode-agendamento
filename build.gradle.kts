plugins {
    application
}

group = "br.com.jmcodestudio"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("br.com.jmcodestudio.agendamento.Main")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

repositories {
    mavenCentral()
}

dependencies {
    // vazio por enquanto — vamos usar só a lib padrão do Java
}

tasks.test {
    useJUnitPlatform()
}