# AI Studio SuperApp

Android application built with Kotlin, Jetpack Compose, Room, DataStore, Ktor and Coil.

## GitHub Actions build

This repository includes `.github/workflows/build-apk.yml`.
Every push to `main` or `master`, every pull request, and every manual workflow run builds a Debug APK.

To download the APK on GitHub:

1. Open the repository.
2. Open **Actions**.
3. Open the latest **Build Android APK** run.
4. Wait for the `Build Debug APK` job to turn green.
5. At the bottom of the run page, download the `AIStudioSuperApp-debug-apk` artifact.

## Important Gradle note

The source archive that this GitHub-ready version was produced from did not include
`gradle/wrapper/gradle-wrapper.jar`. For that reason the GitHub workflow installs Gradle 9.5.0
with the official Gradle GitHub Action and invokes `gradle` directly. This prevents the common
`GradleWrapperMain` / missing wrapper JAR failure on GitHub Actions.

If you want to regenerate the complete Gradle Wrapper locally after installing Gradle 9.5.0, run:

```bash
gradle wrapper --gradle-version 9.5.0
```

Then commit the generated `gradle/wrapper/gradle-wrapper.jar` together with the wrapper scripts.

## Toolchain

- Android Gradle Plugin: 9.3.2
- Kotlin: 2.4.10
- Gradle: 9.5.0
- JDK: 17
- compileSdk: 37
- targetSdk: 36
- minSdk: 26
- Compose BOM: 2026.08.00

## Local Android Studio

Open the repository root (the folder containing `settings.gradle.kts`) in Android Studio.
If Android Studio reports a missing Gradle Wrapper JAR, regenerate the wrapper using the command above,
or configure Android Studio to use a locally installed Gradle 9.5.0.

## Security

API keys and signing keystores should never be committed to GitHub. Local signing files and
`keystore.properties` are ignored by `.gitignore`.
