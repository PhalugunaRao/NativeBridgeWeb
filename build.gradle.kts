import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

group = "com.phalu.nativebridgeweb"
version = "1.0.0"

val sdkModules = listOf(
    "webview-core",
    "webview-jsbridge",
    "webview-permissions",
    "webview-security",
)

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")

        extensions.configure<LibraryExtension>("android") {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        afterEvaluate {
            extensions.configure<PublishingExtension>("publishing") {
                publications {
                    create<MavenPublication>("release") {
                        from(components["release"])
                        groupId = rootProject.group.toString()
                        artifactId = project.name
                        version = rootProject.version.toString()

                        pom {
                            name.set(project.name)
                            description.set("NativeBridgeWeb Android WebView SDK module")
                            url.set("https://github.com/PhalugunaRao/NativeBridgeWeb")
                            licenses {
                                license {
                                    name.set("Apache License 2.0")
                                    url.set("https://www.apache.org/licenses/LICENSE-2.0")
                                }
                            }
                            developers {
                                developer {
                                    id.set("PhalugunaRao")
                                    name.set("Phaluguna Rao")
                                }
                            }
                            scm {
                                connection.set("scm:git:git://github.com/PhalugunaRao/NativeBridgeWeb.git")
                                developerConnection.set("scm:git:ssh://github.com/PhalugunaRao/NativeBridgeWeb.git")
                                url.set("https://github.com/PhalugunaRao/NativeBridgeWeb")
                            }
                        }
                    }
                }

                repositories {
                    maven {
                        name = "localSdk"
                        url = rootProject.layout.buildDirectory.dir("repository").get().asFile.toURI()
                    }
                }
            }

            val extractReleaseClassesJar = tasks.register<Copy>("extractReleaseClassesJar") {
                dependsOn("bundleReleaseAar")
                val aarFile = layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")
                from(zipTree(aarFile)) {
                    include("classes.jar")
                    rename("classes.jar", "${project.name}-${rootProject.version}.jar")
                }
                into(rootProject.layout.buildDirectory.dir("distributions/jars"))
            }

            tasks.register<Jar>("releaseClassesJar") {
                dependsOn(extractReleaseClassesJar)
                archiveBaseName.set(project.name)
                archiveVersion.set(rootProject.version.toString())
                from(zipTree(rootProject.layout.buildDirectory.file("distributions/jars/${project.name}-${rootProject.version}.jar")))
                destinationDirectory.set(layout.buildDirectory.dir("libs"))
            }
        }
    }
}

tasks.register<Copy>("collectSdkAars") {
    dependsOn(sdkModules.map { ":$it:bundleReleaseAar" })
    sdkModules.forEach { moduleName ->
        from(project(":$moduleName").layout.buildDirectory.file("outputs/aar/$moduleName-release.aar"))
    }
    into(layout.buildDirectory.dir("distributions/aars"))
}

tasks.register("collectSdkJars") {
    dependsOn(sdkModules.map { ":$it:extractReleaseClassesJar" })
}

tasks.register("publishSdkToLocalRepository") {
    dependsOn(sdkModules.map { ":$it:publishReleasePublicationToLocalSdkRepository" })
}

tasks.register<Zip>("packageSdkRelease") {
    dependsOn("collectSdkAars", "collectSdkJars", "publishSdkToLocalRepository")
    archiveBaseName.set("NativeBridgeWeb")
    archiveVersion.set(rootProject.version.toString())
    archiveClassifier.set("android-sdk")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    from(layout.buildDirectory.dir("distributions/aars")) {
        into("aars")
    }
    from(layout.buildDirectory.dir("distributions/jars")) {
        into("jars")
    }
    from(layout.buildDirectory.dir("repository")) {
        into("maven-repository")
    }
    from("README.md") {
        into("docs")
    }
}
