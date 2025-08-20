plugins {
  java
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)

  // Code quality plugins
  checkstyle
  jacoco
  alias(libs.plugins.sonarqube)
}

group = "uk.nhs.tis.trainee"
version = "0.14.2"

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
    mavenBom(libs.spring.cloud.dependencies.aws.get().toString())
  }
}

dependencies {
  // Spring Boot starters
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework.boot:spring-boot-starter-web")

  // AWS
  implementation(libs.aws.xray)
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
  implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")

  // Lombok
  compileOnly("org.projectlombok:lombok")
  annotationProcessor("org.projectlombok:lombok")

  // MapStruct
  implementation(libs.mapstruct.core)
  annotationProcessor(libs.mapstruct.processor)

  // Mongock
  implementation(libs.bundles.mongock)

  // Sentry reporting
  implementation(libs.bundles.sentry)
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
        annotationProcessor(libs.mapstruct.processor)
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
