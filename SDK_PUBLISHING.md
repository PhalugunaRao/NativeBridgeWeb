# NativeBridgeWeb SDK Publishing Guide

This project is an Android SDK, so the recommended artifact for app developers is **AAR**. The build also creates extracted **JAR** files for teams that specifically need class-only artifacts.

## Version Coordinates

Current SDK version:

```text
com.phalu.nativebridgeweb:1.0.0
```

Modules:

```text
com.phalu.nativebridgeweb:webview-core:1.0.0
com.phalu.nativebridgeweb:webview-jsbridge:1.0.0
com.phalu.nativebridgeweb:webview-permissions:1.0.0
com.phalu.nativebridgeweb:webview-security:1.0.0
```

## Build All Release Artifacts

```bash
./gradlew packageSdkRelease
```

Output:

```text
build/distributions/NativeBridgeWeb-1.0.0-android-sdk.zip
```

The zip contains:

```text
aars/
  webview-core-release.aar
  webview-jsbridge-release.aar
  webview-permissions-release.aar
  webview-security-release.aar

jars/
  webview-core-1.0.0.jar
  webview-jsbridge-1.0.0.jar
  webview-permissions-1.0.0.jar
  webview-security-1.0.0.jar

maven-repository/
  com/phalu/nativebridgeweb/...
```

## Create Only AAR Files

```bash
./gradlew collectSdkAars
```

Output:

```text
build/distributions/aars/
```

## Create Only JAR Files

```bash
./gradlew collectSdkJars
```

Output:

```text
build/distributions/jars/
```

Important: JAR files contain compiled classes only. They do not include Android manifests, resources, consumer ProGuard rules, or dependency metadata. Use AAR or Maven publishing for real Android app integration.

## Publish To Local Maven Repository

```bash
./gradlew publishSdkToLocalRepository
```

Output:

```text
build/repository/
```

Consumer app setup:

```kotlin
repositories {
    maven {
        url = uri("/path/to/NativeBridgeWeb/build/repository")
    }
    google()
    mavenCentral()
}

dependencies {
    implementation("com.phalu.nativebridgeweb:webview-core:1.0.0")
    implementation("com.phalu.nativebridgeweb:webview-jsbridge:1.0.0")
    implementation("com.phalu.nativebridgeweb:webview-permissions:1.0.0")
    implementation("com.phalu.nativebridgeweb:webview-security:1.0.0")
}
```

## Publish To Maven Local

Each module also supports the standard Maven Local task:

```bash
./gradlew publishToMavenLocal
```

Consumer app setup:

```kotlin
repositories {
    mavenLocal()
    google()
    mavenCentral()
}
```

## Recommended User Access Flow

For a small private team:

1. Run `./gradlew packageSdkRelease`.
2. Share `build/distributions/NativeBridgeWeb-1.0.0-android-sdk.zip`.
3. Ask users to consume the Maven repository folder from the zip.

For production distribution:

1. Publish the generated Maven artifacts to GitHub Packages, Nexus, Artifactory, or Maven Central.
2. Give users dependency coordinates instead of raw files.
3. Keep semantic versions such as `1.0.0`, `1.0.1`, `1.1.0`.

## GitHub Packages Plan

Add a Maven repository named `githubPackages` later when credentials are ready:

```kotlin
repositories {
    maven {
        name = "githubPackages"
        url = uri("https://maven.pkg.github.com/PhalugunaRao/NativeBridgeWeb")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
                ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull
                ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

Then publish:

```bash
./gradlew publishReleasePublicationToGithubPackagesRepository
```
