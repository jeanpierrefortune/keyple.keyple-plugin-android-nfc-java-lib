///////////////////////////////////////////////////////////////////////////////
//  GRADLE CONFIGURATION
///////////////////////////////////////////////////////////////////////////////

plugins {
  id("com.android.library")
  id("kotlin-android")
  id("kotlin-parcelize")
  id("org.jetbrains.dokka")
  `maven-publish`
  signing
  id("com.diffplug.spotless") version "6.25.0"
}

///////////////////////////////////////////////////////////////////////////////
//  APP CONFIGURATION
///////////////////////////////////////////////////////////////////////////////

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.20")
  implementation("org.eclipse.keyple:keyple-common-java-api:2.0.1")
  implementation("org.eclipse.keyple:keyple-plugin-java-api:2.3.1")
  implementation("org.eclipse.keyple:keyple-util-java-lib:2.4.0")
  implementation("org.slf4j:slf4j-api:1.7.32")
}

///////////////////////////////////////////////////////////////////////////////
//  STANDARD CONFIGURATION FOR ANDROID PROJECTS
///////////////////////////////////////////////////////////////////////////////

if (project.hasProperty("releaseTag")) {
  project.version = project.property("releaseTag") as String
  println("Release mode: version set to ${project.version}")
} else {
  println("Development mode: version is ${project.version}")
}

val javaSourceLevel: String by project
val javaTargetLevel: String by project
val archivesBaseName: String by project

android {
  compileSdk = 33

  buildFeatures { viewBinding = true }

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.toVersion(javaSourceLevel)
    targetCompatibility = JavaVersion.toVersion(javaTargetLevel)
  }

  testOptions {
    unitTests.apply {
      isReturnDefaultValues = true // mock Log Android object
      isIncludeAndroidResources = true
    }
  }

  lint { abortOnError = false }

  kotlinOptions { jvmTarget = javaTargetLevel }

  sourceSets {
    getByName("main").java.srcDirs("src/main/kotlin")
    getByName("debug").java.srcDirs("src/debug/kotlin")
    getByName("test").java.srcDirs("src/test/kotlin")
    getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
  }

  // Configuration pour publier les sources et la javadoc avec AAR
  publishing {
    singleVariant("release") {
      withSourcesJar()
      // Pas de withJavadocJar() car on va le configurer manuellement avec Dokka
    }
  }
}

java {
  sourceCompatibility = JavaVersion.toVersion(javaSourceLevel)
  targetCompatibility = JavaVersion.toVersion(javaTargetLevel)
  println("Compiling Java $sourceCompatibility to Java $targetCompatibility.")
}

fun copyLicenseFiles() {
  val metaInfDir = File(layout.buildDirectory.get().asFile, "resources/main/META-INF")
  val licenseFile = File(project.rootDir, "LICENSE")
  val noticeFile = File(project.rootDir, "NOTICE.md")
  metaInfDir.mkdirs()
  licenseFile.copyTo(File(metaInfDir, "LICENSE"), overwrite = true)
  noticeFile.copyTo(File(metaInfDir, "NOTICE.md"), overwrite = true)
}

tasks {
  spotless {
    kotlin {
      target("src/**/*.kt")
      licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
      ktfmt()
    }
    kotlinGradle {
      target("**/*.kts")
      ktfmt()
    }
  }

  dokkaHtml.configure {
    dokkaSourceSets {
      named("main") {
        noAndroidSdkLink.set(false)
        includeNonPublic.set(false)
        includes.from(files("src/main/kdoc/overview.md"))
      }
    }
  }

  // Configuration spéciale pour la javadoc avec AAR - Dokka comme générateur
  dokkaJavadoc.configure {
    dokkaSourceSets {
      named("main") {
        noAndroidSdkLink.set(false)
        includeNonPublic.set(false)
        jdkVersion.set(11)

        // Configuration pour simuler les options javadoc
        perPackageOption {
          matchingRegex.set(".*")
          includeNonPublic.set(false)
        }
      }
    }

    doFirst { println("Generating Javadoc for ${project.name} version ${project.version}") }
  }

  // Task personnalisée pour créer un JAR de la javadoc avec Dokka
  register<Jar>("javadocJar") {
    dependsOn(dokkaJavadoc)
    archiveClassifier.set("javadoc")
    from(dokkaJavadoc.flatMap { it.outputDirectory })

    doFirst { copyLicenseFiles() }
    manifest {
      attributes(
          mapOf(
              "Implementation-Title" to "${project.findProperty("title") as String} Documentation",
              "Implementation-Version" to project.version))
    }
  }

  // Task personnalisée pour configurer les JARs sources générés automatiquement
  withType<Jar>().configureEach {
    if (archiveClassifier.get() == "sources") {
      doFirst { copyLicenseFiles() }
      manifest {
        attributes(
            mapOf(
                "Implementation-Title" to "${project.findProperty("title") as String} Sources",
                "Implementation-Version" to project.version))
      }
    }
  }

  // Task pour copier les licences
  register("copyLicenseFiles") { doLast { copyLicenseFiles() } }
}

afterEvaluate {
  // Assurer que les licences sont copiées avant l'assemblage
  tasks.named("assembleRelease") { dependsOn("copyLicenseFiles") }

  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"])

        // Ajout explicite de notre JAR javadoc personnalisé
        artifact(tasks["javadocJar"])

        pom {
          name.set(project.findProperty("title") as String)
          description.set(project.findProperty("description") as String)
          url.set(project.findProperty("project.url") as String)
          licenses {
            license {
              name.set(project.findProperty("license.name") as String)
              url.set(project.findProperty("license.url") as String)
              distribution.set(project.findProperty("license.distribution") as String)
            }
          }
          developers {
            developer {
              name.set(project.findProperty("developer.name") as String)
              email.set(project.findProperty("developer.email") as String)
            }
          }
          organization {
            name.set(project.findProperty("organization.name") as String)
            url.set(project.findProperty("organization.url") as String)
          }
          scm {
            connection.set(project.findProperty("scm.connection") as String)
            developerConnection.set(project.findProperty("scm.developerConnection") as String)
            url.set(project.findProperty("scm.url") as String)
          }
          ciManagement {
            system.set(project.findProperty("ci.system") as String)
            url.set(project.findProperty("ci.url") as String)
          }
          properties.set(
              mapOf(
                  "project.build.sourceEncoding" to "UTF-8",
                  "maven.compiler.source" to javaSourceLevel,
                  "maven.compiler.target" to javaTargetLevel))
        }
      }
    }
    repositories {
      maven {
        if (project.hasProperty("sonatypeURL")) {
          url = uri(project.property("sonatypeURL") as String)
          credentials {
            username = project.property("sonatypeUsername") as String
            password = project.property("sonatypePassword") as String
          }
        }
      }
    }
  }
}

signing {
  if (project.hasProperty("releaseTag")) {
    useGpgCmd()
    sign(publishing.publications["release"])
  }
}
