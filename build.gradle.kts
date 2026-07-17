plugins {
    java
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // 페이퍼 라이브러리를 다운로드받는 공식 저장소 주소
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // 서버 버전(마인크래프트 26.1.2)에 맞춘 Paper API.
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // 서버가 Java 25를 쓰므로 빌드도 Java 25 버전에 맞게 타겟팅합니다.
    options.release.set(25)
}