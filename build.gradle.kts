plugins {
  java
  id("org.springframework.boot") version "3.4.1"
  id("io.spring.dependency-management") version "1.1.4"

  // Code quality plugins
  checkstyle
  jacoco
  id("org.sonarqube") version "5.1.0.4882"
}

group = "uk.nhs.tis.trainee"
version = "0.9.1"

configurations {
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

repositories {
  mavenCentral()
}

dependencyManagement {
  imports {
    mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:3.1.0")
  }
}

val mapstructVersion = "1.5.5.Final"
val mongockVersion = "5.4.0"
val sentryVersion = "7.11.0"

dependencies {
  // Spring Boot starters
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-web")

  // AWS
  implementation("com.amazonaws:aws-xray-recorder-sdk-spring:2.16.0")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // MapStruct
  implementation("org.mapstruct:mapstruct:$mapstructVersion")
  annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")

  // Mongock
  implementation("io.mongock:mongock-springboot:${mongockVersion}")
  implementation("io.mongock:mongodb-springdata-v4-driver:${mongockVersion}")

  // Sentry reporting
  implementation("io.sentry:sentry-spring-boot-starter-jakarta:$sentryVersion")
  implementation("io.sentry:sentry-logback:$sentryVersion")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
    vendor.set(JvmVendorSpec.ADOPTIUM)
  }
}

checkstyle {
  config = resources.text.fromArchiveEntry(configurations.checkstyle.get().first(), "google_checks.xml")
}

sonarqube {
  properties {
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.login", System.getenv("SONAR_TOKEN"))
    property("sonar.organization", "health-education-england")
    property("sonar.projectKey", "Health-Education-England_tis-trainee-actions")

    property("sonar.java.checkstyle.reportPaths",
      "build/reports/checkstyle/main.xml,build/reports/checkstyle/test.xml")
  }
}

testing {
  suites {

    configureEach {
      if (this is JvmTestSuite) {
        useJUnitJupiter()
        dependencies {
          implementation(project())
          implementation("org.springframework.boot:spring-boot-starter-test")
        }
      }
    }

    val test by getting(JvmTestSuite::class) {
      dependencies {
        annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
      }
    }

    val integrationTest by registering(JvmTestSuite::class)  {
      dependencies {
        implementation("org.springframework.boot:spring-boot-testcontainers")
        implementation("org.testcontainers:junit-jupiter")
        implementation("org.testcontainers:localstack")
        implementation("org.testcontainers:mongodb")
        implementation("org.awaitility:awaitility")
      }

      targets {
        all {
          testTask.configure {
            systemProperty("spring.profiles.active", "test")
          }
        }
      }
    }

    val integrationTestImplementation by configurations.getting {
      extendsFrom(configurations.implementation.get())
    }
  }
}

tasks.jacocoTestReport {
  reports {
    html.required.set(true)
    xml.required.set(true)
  }
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
}
