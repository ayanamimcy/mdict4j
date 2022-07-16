import java.io.FileInputStream
import java.util.Properties

plugins {
    checkstyle
    jacoco
    signing
    `java-library`
    `java-library-distribution`
    `maven-publish`
    kotlin("jvm") version "1.7.0"
    id("com.github.spotbugs") version "5.0.9"
    id("com.diffplug.spotless") version "6.7.2"
    id("com.github.kt3k.coveralls") version "2.12.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
    id("com.palantir.git-version") version "0.13.0" apply false
    id("kr.motd.sphinx") version "2.10.1"
}

group = "tokyo.northside"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains:annotations:23.0.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.3")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.13.3")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.anarres.lzo:lzo-core:1.0.6")
    implementation("com.github.takawitter:trie4j:0.9.8")
    // use Caffeine cache for java 8 (3.x requires Java11)
    implementation("com.github.ben-manes.caffeine:caffeine:2.9.3")
    testImplementation("org.codehaus.groovy:groovy-all:3.0.11")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("org.jsoup:jsoup:1.15.2")
    testImplementation("org.apache.tika:tika-core:2.4.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:1.7.36")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

jacoco {
    toolVersion="0.8.6"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

coveralls {
    jacocoReportPath = "build/reports/jacoco/test/jacocoTestReport.xml"
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
    options.compilerArgs.add("-Xlint:unchecked")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

// we handle cases without .git directory
val home = System.getProperty("user.home")
val javaHome = System.getProperty("java.home")
val versionProperties = project.file("src/main/resources/version.properties")
val dotgit = project.file(".git")

if (dotgit.exists()) {
    apply(plugin = "com.palantir.git-version")
    val versionDetails: groovy.lang.Closure<com.palantir.gradle.gitversion.VersionDetails> by extra
    val details = versionDetails()
    val baseVersion = details.lastTag.substring(1)
    if (details.isCleanTag) {  // release version
        version = baseVersion
    } else {  // snapshot version
        version = baseVersion + "-" + details.commitDistance + "-" + details.gitHash + "-SNAPSHOT"
    }

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom {
                    name.set("URL protocol handler")
                    description.set("MDict parser for java")
                    url.set("https://codeberg.org/miurahr/mdict4j")
                    licenses {
                        license {
                            name.set("The GNU General Public License, Version 3")
                            url.set("https://www.gnu.org/licenses/licenses/gpl-3.html")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("miurahr")
                            name.set("Hiroshi Miura")
                            email.set("miurahr@linux.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://codeberg.org/miurahr/mdict4j.git")
                        developerConnection.set("scm:git:git://codeberg.org/miurahr/mdict4j.git")
                        url.set("https://codeberg.org/miurahr/mdict4j")
                    }
                }
            }
        }
    }

    val signKey = listOf("signingKey", "signing.keyId", "signing.gnupg.keyName").find {project.hasProperty(it)}
    tasks.withType<Sign> {
        onlyIf { details.isCleanTag && (signKey != null) }
    }

    signing {
        when (signKey) {
            "signingKey" -> {
                val signingKey: String? by project
                val signingPassword: String? by project
                useInMemoryPgpKeys(signingKey, signingPassword)
            }
            "signing.keyId" -> {
                // default signatory - do nothing()
                // please set
                // signing.keyId = 0xAAAAAA
                // signing.password = "signature passphrase"
                // secretKeyRingFile = "secring.gpg"
                // e.g. gpg --export-secret-keys > secring.gpg
            }
            "signing.gnupg.keyName" -> {
                useGpgCmd()
            }
        }
        sign(publishing.publications["mavenJava"])
    }

    nexusPublishing {
        repositories{
            sonatype()
        }
    }
} else if (versionProperties.exists()) {
    version = Properties().apply { load(FileInputStream(versionProperties)) }.getProperty("version")
}

tasks.register("writeVersionFile") {
    val folder = project.file("src/main/resources")
    if (!folder.exists()) {
        folder.mkdirs()
    }
    versionProperties.delete()
    versionProperties.appendText("version=" + project.version)
}

tasks.getByName("jar") {
    dependsOn("writeVersionFile")
}

tasks.sphinx {
    sourceDirectory {"docs"}
}