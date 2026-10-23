# Implementation Plan: Fix GitHub Action Release Build Failure

Resolve the CI release APK failure (`:app:lintVitalRelease` error) by removing the invalid inline `registerForActivityResult` call in `MainActivity.kt` and configuring Gradle lint options to prevent CI build termination.

---

## User Decisions & Clarifications
1. **Notification Permission Strategy**: Rely on Jetpack Compose's idiomatic `rememberLauncherForActivityResult` in `HomeScreen.kt` and remove the redundant, invalid inline `registerForActivityResult` call from `MainActivity.kt`.
2. **Gradle Release Lint Configuration**: Configure `lint { checkReleaseBuilds = false; abortOnError = false }` in `app/build.gradle.kts` so that non-critical lint warnings do not abort release APK builds in GitHub Actions CI.

---

## Proposed Changes

### 1. Remove Invalid Activity Result Registration
#### `app/src/main/java/com/example/MainActivity.kt`
- Remove lines 122–133:
  ```kotlin
  // Remove redundant inline registration causing InvalidFragmentVersionForActivityResult:
  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(...)
      if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
          registerForActivityResult(...) { }.launch(...)
      }
  }
  ```
- **Rationale**: Calling `registerForActivityResult` dynamically inside `onCreate()` violates Android's activity result lifecycle contract and triggers Lint's fatal `InvalidFragmentVersionForActivityResult` check. Notification permission is already requested cleanly via Jetpack Compose in `HomeScreen.kt` (`rememberLauncherForActivityResult`).

---

### 2. Configure Gradle Lint for Release Builds
#### `app/build.gradle.kts`
- Add a `lint` configuration block inside the `android { ... }` block:
  ```kotlin
  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
  ```
- **Rationale**: Ensures that `:app:lintVitalRelease` does not block GitHub Action runners during `assembleRelease`, while continuing to support standard compilation checks and local tests.

---

## Verification Plan

1. **Compilation**: Run `compile_applet` to verify code compiles cleanly.
2. **Unit Tests**: Run `gradle :app:testDebugUnitTest` to ensure all tests pass.
3. **Release Lint / Task Verification**: Run `gradle :app:lintVitalRelease` or `gradle :app:assembleRelease --dry-run` to verify that `lintVitalRelease` passes without errors.
