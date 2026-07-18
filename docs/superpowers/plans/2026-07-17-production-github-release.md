# Signed GitHub Production Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish MobiSentinel 1.0.0 on GitHub as a stable, cryptographically verified APK signed by GitHub Actions under the permanent Android identity `br.com.marcocardoso.mobisentinel`.

**Architecture:** Keep Release Please as the human-reviewed version/tag controller, but leave each generated GitHub Release in prerelease state until unit, lint, emulator, signed-build, metadata, certificate, and checksum gates pass. Read production signing material only from a scoped GitHub `production` environment, reconstruct the PKCS#12 file in the runner temporary directory, and pin the public certificate fingerprint in Git while backing up the recoverable private material in the user's personal Google Drive.

**Tech Stack:** Android/Kotlin, Gradle 8.13/AGP 8.13.2, PowerShell 7, Android `aapt`/`apksigner`/`keytool`, GitHub Actions, Release Please, GitHub CLI, Google Drive.

## Global Constraints

- Permanent Android application ID and namespace: `br.com.marcocardoso.mobisentinel`.
- First stable version: `1.0.0`; do not edit `0.1.1` directly on the implementation branch—Release Please performs the version bump after a breaking Conventional Commit.
- Minimum/target/compile SDK remain API 26/36/36; Java and Kotlin remain version 17.
- GitHub is the only production distribution channel in this plan; do not add AAB or Play publication.
- Production APK name: `MobiSentinel-X.Y.Z.apk`; checksum name: `MobiSentinel-X.Y.Z.apk.sha256`.
- Production signing uses the GitHub environment `production` and secrets `ANDROID_SIGNING_KEY_BASE64`, `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS`, and `ANDROID_SIGNING_KEY_PASSWORD`.
- The runner-only environment variable `ANDROID_SIGNING_STORE_FILE` points to the reconstructed PKCS#12 file.
- No production signing value, keystore, or recovery file may enter Git, logs, caches, or workflow artifacts.
- Public certificate SHA-256 fingerprint is stored at `signing/release-certificate.sha256` as exactly 64 lowercase hexadecimal characters plus a newline.
- Keep `isMinifyEnabled = false` for the first production release.
- `connectedDebugAndroidTest` on a GitHub-hosted API 35 x86_64 emulator is a required automated gate; no physical-device test is required.
- The four deferred scenarios remain visible risks, not passing tests: absent TTS engine, captive portal/no-internet Wi-Fi, prolonged aggressive battery restrictions, and physical Android 8–11.
- Preserve historical documents; add supersession notes instead of rewriting historical commands as if they had always used the new package.

---

## File Structure

### New files

- `buildSrc/src/main/kotlin/com/mobisentinel/signing/ReleaseSigningEnvironment.kt`: validates signing environment variables and identifies release artifact tasks.
- `buildSrc/src/test/kotlin/com/mobisentinel/signing/ReleaseSigningEnvironmentTest.kt`: tests complete, missing, partial, and debug-only configurations.
- `scripts/create-production-signing.ps1`: creates the recoverable PKCS#12 bundle without printing credentials.
- `scripts/configure-production-environment.ps1`: creates the GitHub environment and streams signing values to GitHub Secrets over stdin.
- `scripts/tests/create-production-signing-test.ps1`: verifies generated keystore, recovery data, and fingerprint.
- `signing/release-certificate.sha256`: public production certificate fingerprint generated from the real certificate.
- `SECURITY.md`: vulnerability reporting, official fingerprint, signing custody, and incident handling.
- `PRIVACY.md`: local-only data handling statement.
- `docs/releasing/production-release.md`: operational release/recovery/runbook.

### Modified files

- `app/build.gradle.kts`: permanent package/namespace and environment-backed release signing.
- Every Kotlin file under `app/src/main`, `app/src/test`, and `app/src/androidTest`: package/import migration.
- `app/src/main/java/.../service/MonitoringService.kt`: permanent action strings.
- `app/src/test/java/.../ProjectSmokeTest.kt`: permanent application ID assertion.
- `.gitignore`: private signing file patterns.
- `scripts/verify-release-apk.ps1`: release/debuggable/signature/fingerprint validation.
- `scripts/tests/verify-release-apk-test.ps1`: ephemeral-key positive and negative verifier tests.
- `.github/workflows/ci.yml`: release lint/unit coverage and mandatory API 35 emulator job.
- `.github/workflows/release-please.yml`: signed build, emulator gate, two-asset publication, and final stable promotion.
- `README.md`, `docs/testing/manual-test-matrix.md`: production instructions and accepted-risk status.
- `docs/superpowers/specs/2026-07-16-release-automation-design.md` and `docs/superpowers/plans/2026-07-16-release-automation.md`: historical supersession notes.

---

### Task 1: Migrate the permanent Android identity

**Files:**
- Modify: `app/src/test/java/com/mobisentinel/app/ProjectSmokeTest.kt`
- Modify: `app/build.gradle.kts`
- Move/modify: `app/src/main/java/com/mobisentinel/app/**`
- Move/modify: `app/src/test/java/com/mobisentinel/app/**`
- Move/modify: `app/src/androidTest/java/com/mobisentinel/app/**`
- Modify: `app/src/main/java/br/com/marcocardoso/mobisentinel/service/MonitoringService.kt`

**Interfaces:**
- Consumes: current Android application source and tests under `com.mobisentinel.app`.
- Produces: `BuildConfig.APPLICATION_ID == "br.com.marcocardoso.mobisentinel"` and Kotlin packages rooted at `br.com.marcocardoso.mobisentinel`.

- [ ] **Step 1: Change the smoke test first**

Move the test to `app/src/test/java/br/com/marcocardoso/mobisentinel/ProjectSmokeTest.kt` and use:

```kotlin
package br.com.marcocardoso.mobisentinel

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectSmokeTest {
    @Test
    fun packageNameIsStable() {
        assertEquals("br.com.marcocardoso.mobisentinel", BuildConfig.APPLICATION_ID)
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "br.com.marcocardoso.mobisentinel.ProjectSmokeTest" --console=plain
```

Expected: compilation fails because the production namespace and sources still use `com.mobisentinel.app`.

- [ ] **Step 3: Change Gradle identity and all non-historical source references**

In `app/build.gradle.kts` set:

```kotlin
android {
    namespace = "br.com.marcocardoso.mobisentinel"

    defaultConfig {
        applicationId = "br.com.marcocardoso.mobisentinel"
    }
}
```

Mechanically replace `com.mobisentinel.app` with `br.com.marcocardoso.mobisentinel` in `app/src/main`, `app/src/test`, and `app/src/androidTest`, then move each source root from `java/com/mobisentinel/app` to `java/br/com/marcocardoso/mobisentinel`. Preserve subpackages exactly.

The service action constants must become:

```kotlin
const val ACTION_STOP = "br.com.marcocardoso.mobisentinel.action.STOP"
const val ACTION_TEST_VOICE = "br.com.marcocardoso.mobisentinel.action.TEST_VOICE"
```

- [ ] **Step 4: Prove the new identity and absence of live old references**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
rg -n "com\.mobisentinel\.app" app scripts .github README.md docs\testing
```

Expected: Gradle succeeds; the search returns only scripts/docs not yet migrated in later tasks and no result under `app`.

- [ ] **Step 5: Commit the breaking identity change**

```powershell
git add app
git commit -m "feat!: adopt permanent Android application identity" -m "BREAKING CHANGE: installs now use br.com.marcocardoso.mobisentinel and do not update com.mobisentinel.app."
```

---

### Task 2: Add testable environment-backed release signing

**Files:**
- Create: `buildSrc/src/main/kotlin/com/mobisentinel/signing/ReleaseSigningEnvironment.kt`
- Create: `buildSrc/src/test/kotlin/com/mobisentinel/signing/ReleaseSigningEnvironmentTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `ReleaseSigningEnvironment.resolve(environment: Map<String, String>, taskNames: List<String>): ReleaseSigningEnvironment?`.
- Consumes later: four secret-derived environment values plus runner-only `ANDROID_SIGNING_STORE_FILE`.

- [ ] **Step 1: Write signing environment tests**

Create the test with these cases:

```kotlin
package com.mobisentinel.signing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseSigningEnvironmentTest {
    private val complete = mapOf(
        "ANDROID_SIGNING_STORE_FILE" to "C:/tmp/release.p12",
        "ANDROID_SIGNING_STORE_PASSWORD" to "store-secret",
        "ANDROID_SIGNING_KEY_ALIAS" to "mobisentinel",
        "ANDROID_SIGNING_KEY_PASSWORD" to "key-secret",
    )

    @Test
    fun `returns credentials when all values exist`() {
        val result = ReleaseSigningEnvironment.resolve(complete, listOf("assembleRelease"))
        assertEquals("mobisentinel", result?.keyAlias)
    }

    @Test
    fun `allows unsigned non artifact release tasks`() {
        assertNull(ReleaseSigningEnvironment.resolve(emptyMap(), listOf("testReleaseUnitTest", "lintRelease")))
    }

    @Test
    fun `rejects release artifact without credentials`() {
        val error = assertFailsWith<IllegalStateException> {
            ReleaseSigningEnvironment.resolve(emptyMap(), listOf(":app:assembleRelease"))
        }
        assertTrue(error.message.orEmpty().contains("ANDROID_SIGNING_STORE_FILE"))
    }

    @Test
    fun `rejects partial configuration without exposing values`() {
        val error = assertFailsWith<IllegalStateException> {
            ReleaseSigningEnvironment.resolve(
                mapOf("ANDROID_SIGNING_STORE_PASSWORD" to "must-not-appear"),
                listOf("assembleDebug"),
            )
        }
        assertTrue(error.message.orEmpty().contains("ANDROID_SIGNING_KEY_ALIAS"))
        assertTrue(!error.message.orEmpty().contains("must-not-appear"))
    }
}
```

- [ ] **Step 2: Run tests and confirm RED**

```powershell
.\gradlew.bat -p buildSrc test --tests "com.mobisentinel.signing.ReleaseSigningEnvironmentTest" --console=plain
```

Expected: FAIL because `ReleaseSigningEnvironment` does not exist.

- [ ] **Step 3: Implement minimal signing resolution**

Create:

```kotlin
package com.mobisentinel.signing

data class ReleaseSigningEnvironment(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    companion object {
        private val required = listOf(
            "ANDROID_SIGNING_STORE_FILE",
            "ANDROID_SIGNING_STORE_PASSWORD",
            "ANDROID_SIGNING_KEY_ALIAS",
            "ANDROID_SIGNING_KEY_PASSWORD",
        )

        fun resolve(
            environment: Map<String, String>,
            taskNames: List<String>,
        ): ReleaseSigningEnvironment? {
            val values = required.associateWith { environment[it].orEmpty().trim() }
            val configured = values.filterValues { it.isNotEmpty() }
            val artifactRequested = taskNames.any { task ->
                task.substringAfterLast(':').matches(
                    Regex("(?i)(assemble|bundle|package).*release"),
                )
            }

            if (configured.isEmpty()) {
                if (artifactRequested) {
                    error("Release signing requires: ${required.joinToString()}")
                }
                return null
            }

            val missing = values.filterValues { it.isEmpty() }.keys
            if (missing.isNotEmpty()) {
                error("Incomplete release signing configuration. Missing: ${missing.joinToString()}")
            }

            return ReleaseSigningEnvironment(
                storeFile = values.getValue("ANDROID_SIGNING_STORE_FILE"),
                storePassword = values.getValue("ANDROID_SIGNING_STORE_PASSWORD"),
                keyAlias = values.getValue("ANDROID_SIGNING_KEY_ALIAS"),
                keyPassword = values.getValue("ANDROID_SIGNING_KEY_PASSWORD"),
            )
        }
    }
}
```

- [ ] **Step 4: Connect the Gradle release signing config**

At the top of `app/build.gradle.kts` import the class and resolve it:

```kotlin
import com.mobisentinel.signing.ReleaseSigningEnvironment

val releaseSigning = ReleaseSigningEnvironment.resolve(
    environment = System.getenv(),
    taskNames = gradle.startParameter.taskNames,
)
```

Inside `android {}` before `buildTypes`, add:

```kotlin
val productionSigningConfig = releaseSigning?.let { credentials ->
    val signingFile = file(credentials.storeFile)
    require(signingFile.isFile) {
        "ANDROID_SIGNING_STORE_FILE does not point to a readable file"
    }
    signingConfigs.create("production") {
        storeFile = signingFile
        storePassword = credentials.storePassword
        keyAlias = credentials.keyAlias
        keyPassword = credentials.keyPassword
    }
}
```

And set only the release build:

```kotlin
buildTypes {
    release {
        signingConfig = productionSigningConfig
        isMinifyEnabled = false
    }
}
```

- [ ] **Step 5: Verify GREEN and required failure behavior**

```powershell
.\gradlew.bat -p buildSrc test --console=plain
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug --console=plain
.\gradlew.bat assembleRelease --console=plain
```

Expected: first two commands succeed; the final command fails and lists missing variable names without values.

- [ ] **Step 6: Commit**

```powershell
git add buildSrc app/build.gradle.kts
git commit -m "build: require environment-backed production signing"
```

---

### Task 3: Create safe, reproducible signing tooling

**Files:**
- Modify: `.gitignore`
- Create: `scripts/create-production-signing.ps1`
- Create: `scripts/configure-production-environment.ps1`
- Create: `scripts/tests/create-production-signing-test.ps1`

**Interfaces:**
- Produces in an external directory: `mobisentinel-production.p12`, `mobisentinel-production-recovery.env`, `release-certificate.sha256`, and `README.txt`.
- Consumes later: generated key bundle for Drive/GitHub and fingerprint for Git.

- [ ] **Step 1: Ignore private signing formats before generating anything**

Append exactly:

```gitignore
*.jks
*.keystore
*.p12
*-recovery.env
```

- [ ] **Step 2: Write the signing generator test**

The test must create a unique directory below `$env:TEMP`, invoke the generator, and assert:

```powershell
$expected = @(
    'mobisentinel-production.p12',
    'mobisentinel-production-recovery.env',
    'release-certificate.sha256',
    'README.txt'
)
$expected | ForEach-Object {
    if (-not (Test-Path -LiteralPath (Join-Path $temporary $_))) {
        throw "Missing generated file: $_"
    }
}

$fingerprint = (Get-Content (Join-Path $temporary 'release-certificate.sha256') -Raw).Trim()
if ($fingerprint -notmatch '^[0-9a-f]{64}$') {
    throw 'Fingerprint must contain 64 lowercase hexadecimal characters'
}
```

Parse the recovery file, call `keytool -list` with `-storepass:env`, export the certificate to a temporary DER file, and assert that `Get-FileHash -Algorithm SHA256` equals the fingerprint. Always delete the temporary directory in `finally`.

- [ ] **Step 3: Run the test and confirm RED**

```powershell
.\scripts\tests\create-production-signing-test.ps1
```

Expected: FAIL because the generator does not exist.

- [ ] **Step 4: Implement `create-production-signing.ps1`**

The script must:

```powershell
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$resolvedOutput = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $resolvedOutput -Force | Out-Null
$keystore = Join-Path $resolvedOutput 'mobisentinel-production.p12'
if (Test-Path -LiteralPath $keystore) { throw "Refusing to replace '$keystore'" }

$passwordBytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(36)
$password = [Convert]::ToBase64String($passwordBytes).TrimEnd('=')
$env:MOBISENTINEL_GENERATED_STORE_PASSWORD = $password
$env:MOBISENTINEL_GENERATED_KEY_PASSWORD = $password
```

Invoke `keytool -genkeypair` with store type PKCS12, alias `mobisentinel`, RSA 4096, validity 36500, subject `CN=MobiSentinel Release,O=Marco Cardoso,C=BR`, and `-storepass:env`/`-keypass:env`. Export the certificate to DER, hash it, write the lowercase fingerprint, and write recovery entries with the four GitHub secret names except Base64. Do not print any password. Clear the two temporary process environment variables and delete the DER file in `finally`.

- [ ] **Step 5: Implement GitHub environment configuration without command-line secrets**

`scripts/configure-production-environment.ps1` accepts `-Repository owner/name` and `-SigningDirectory`. It parses the recovery file, computes Base64 from the PKCS#12 bytes, creates `production` with:

```powershell
gh api --method PUT "repos/$Repository/environments/production"
```

Then stream each value through stdin, never `--body`:

```powershell
$base64 | gh secret set ANDROID_SIGNING_KEY_BASE64 --env production --repo $Repository
$storePassword | gh secret set ANDROID_SIGNING_STORE_PASSWORD --env production --repo $Repository
$alias | gh secret set ANDROID_SIGNING_KEY_ALIAS --env production --repo $Repository
$keyPassword | gh secret set ANDROID_SIGNING_KEY_PASSWORD --env production --repo $Repository
```

Finally list only secret names through the GitHub API and fail unless all four are present.

- [ ] **Step 6: Verify GREEN and secret scanning**

```powershell
.\scripts\tests\create-production-signing-test.ps1
git check-ignore signing-test.p12 signing-test-recovery.env
git diff --check
```

Expected: test passes; both sample private names are ignored; diff check succeeds.

- [ ] **Step 7: Commit tooling**

```powershell
git add .gitignore scripts/create-production-signing.ps1 scripts/configure-production-environment.ps1 scripts/tests/create-production-signing-test.ps1
git commit -m "build: add recoverable signing key tooling"
```

---

### Task 4: Generate the real key, pin its fingerprint, configure recovery and GitHub

**Files:**
- Create: `signing/release-certificate.sha256`
- External: `C:\tmp\MobiSentinel-production-signing\*`
- External: Google Drive personal folder `MobiSentinel/production-signing/`
- External: GitHub environment `production`

**Interfaces:**
- Consumes: Task 3 scripts.
- Produces: recoverable Drive backup, four GitHub Secrets, and public pinned fingerprint.

- [ ] **Step 1: Generate once outside the repository**

```powershell
.\scripts\create-production-signing.ps1 -OutputDirectory C:\tmp\MobiSentinel-production-signing
```

Expected: exactly four files, no secret values printed.

- [ ] **Step 2: Copy only the public fingerprint into Git**

Create `signing/release-certificate.sha256` with the exact content of the generated fingerprint file. Verify:

```powershell
$value = (Get-Content signing\release-certificate.sha256 -Raw).Trim()
if ($value -notmatch '^[0-9a-f]{64}$') { throw 'Invalid committed fingerprint' }
git status --short
```

Expected: only the public fingerprint is untracked; no `.p12` or recovery file appears.

- [ ] **Step 3: Configure the GitHub environment and secrets**

```powershell
.\scripts\configure-production-environment.ps1 `
  -Repository marcoaureliocardoso/MobiSentinel `
  -SigningDirectory C:\tmp\MobiSentinel-production-signing
```

Expected: environment exists and the API returns the four secret names, never values.

- [ ] **Step 4: Upload the four recovery files to Google Drive**

Use the connected Google Drive workflow when available; otherwise use the authenticated Chrome session. Ground the existing personal folder named exactly `MobiSentinel`, create or reuse a child folder `production-signing`, upload all four files without changing sharing, then list/read metadata back and verify exact filenames and sizes greater than zero. Never create a public link.

- [ ] **Step 5: Commit only the fingerprint**

```powershell
git add signing/release-certificate.sha256
git commit -m "build: pin production signing certificate"
```

Keep the local recovery directory until the first signed release is verified; remove it only after Drive readback and release completion.

---

### Task 5: Harden the APK verifier with signature and debuggable checks

**Files:**
- Modify: `scripts/verify-release-apk.ps1`
- Modify: `scripts/tests/verify-release-apk-test.ps1`

**Interfaces:**
- Produces: the concrete invocation shape `verify-release-apk.ps1 -Tag v1.0.0 -ApkPath dist/MobiSentinel-1.0.0.apk -CertificateSha256Path signing/release-certificate.sha256`.
- Consumes: `aapt`, `apksigner`, package ID, SemVer mapping, and certificate fingerprint.

- [ ] **Step 1: Rewrite verifier tests around an ephemeral signed release**

The test script must create its own temporary signing bundle through `create-production-signing.ps1`, parse recovery values into the four `ANDROID_SIGNING_*` variables, run:

```powershell
.\gradlew.bat assembleDebug assembleRelease --console=plain
```

Then assert these cases:

```powershell
Assert-Succeeds -Tag $validTag -ApkPath $releaseApk -Fingerprint $temporaryFingerprint
Assert-Fails -Tag $mismatchTag -ApkPath $releaseApk -Fingerprint $temporaryFingerprint -Pattern 'Expected versionName'
Assert-Fails -Tag $versionName -ApkPath $releaseApk -Fingerprint $temporaryFingerprint -Pattern 'must use vX\.Y\.Z'
Assert-Fails -Tag 'v0.1000.0' -ApkPath $releaseApk -Fingerprint $temporaryFingerprint -Pattern '0\.\.999'
Assert-Fails -Tag $validTag -ApkPath $releaseApk -Fingerprint $wrongFingerprint -Pattern 'certificate SHA-256'
Assert-Fails -Tag $validTag -ApkPath $debugApk -Fingerprint $temporaryFingerprint -Pattern 'debuggable'
```

Always restore prior environment values and delete the ephemeral bundle in `finally`.

- [ ] **Step 2: Run tests and confirm RED**

```powershell
.\scripts\tests\verify-release-apk-test.ps1
```

Expected: FAIL because the current verifier accepts debug APKs and has no fingerprint parameter.

- [ ] **Step 3: Extend the verifier**

Add the mandatory parameter:

```powershell
[Parameter(Mandatory)]
[string]$CertificateSha256Path
```

Change the expected package to `br.com.marcocardoso.mobisentinel`. Resolve `apksigner` from the newest Android Build Tools directory, reject badging containing `application-debuggable`, validate the fingerprint file with `^[0-9a-f]{64}$`, and execute:

```powershell
$signature = & $apksigner verify --verbose --print-certs --min-sdk-version 26 $resolvedApk 2>&1
if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $($signature -join ' ')" }
$digestLine = $signature | Where-Object { $_ -match 'certificate SHA-256 digest:' } | Select-Object -First 1
$actualDigest = ([regex]::Match($digestLine, '([0-9a-fA-F]{64})').Groups[1].Value).ToLowerInvariant()
if ($actualDigest -ne $expectedDigest) {
    throw "Expected certificate SHA-256 $expectedDigest, found $actualDigest"
}
```

Success output must contain package, versionName, versionCode, and certificate SHA-256 but no secrets.

- [ ] **Step 4: Verify GREEN**

```powershell
.\scripts\tests\verify-release-apk-test.ps1
```

Expected: all six positive/negative categories pass.

- [ ] **Step 5: Commit**

```powershell
git add scripts/verify-release-apk.ps1 scripts/tests/verify-release-apk-test.ps1
git commit -m "build: verify production APK signature"
```

---

### Task 6: Add mandatory emulator coverage to GitHub CI

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Produces: required jobs `Validate` and `Instrumented tests (API 35)`.
- Consumes: `connectedDebugAndroidTest`, no production secrets.

- [ ] **Step 1: Add release checks to the existing validate job**

Change the Gradle step to:

```yaml
- name: Test, lint, and assemble development artifacts
  run: ./gradlew testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug
```

Keep the verifier test step, which now generates an ephemeral signed release.

- [ ] **Step 2: Add the emulator job pinned by full SHA**

Append:

```yaml
  instrumented-tests:
    name: Instrumented tests (API 35)
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - name: Check out repository
        uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
      - name: Set up Java 17
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: "17"
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0
      - name: Make Gradle wrapper executable
        run: chmod +x gradlew
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Run instrumented tests
        uses: reactivecircus/android-emulator-runner@e89f39f1abbbd05b1113a29cf4db69e7540cae5a # v2.37.0
        with:
          api-level: 35
          target: google_apis
          arch: x86_64
  profile: pixel_7_pro
          emulator-options: -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim
          disable-animations: true
          script: ./gradlew connectedDebugAndroidTest --console=plain
```

- [ ] **Step 3: Validate YAML and action pinning**

Use PowerShell/YAML parsing available in the workspace or Ruby's standard YAML parser, then run:

```powershell
rg -n "uses: .+@v[0-9]" .github\workflows
rg -n "connectedDebugAndroidTest|e89f39f1abbbd05b1113a29cf4db69e7540cae5a" .github\workflows\ci.yml
```

Expected: YAML parses; no unpinned tag-only Action reference; both required strings exist.

- [ ] **Step 4: Commit**

```powershell
git add .github/workflows/ci.yml
git commit -m "ci: run instrumented tests on API 35"
```

---

### Task 7: Convert the release workflow to fail-safe signed production publication

**Files:**
- Modify: `.github/workflows/release-please.yml`
- Keep: `release-please-config.json` with `prerelease: true` as the staging safety state.

**Interfaces:**
- Consumes: Release Please tag/version, GitHub `production` secrets, pinned certificate fingerprint.
- Produces: prerelease while gates run; exact two production assets; stable promotion only at the end.

- [ ] **Step 1: Replace the debug warning with staging notes**

Rename the notes job/step and prepend:

```text
## Release de produção em validação

Esta versão permanece como pré-release até a conclusão dos gates automatizados, da assinatura e da verificação dos artefatos.
Não instale antes da promoção para release estável.
```

Keep `GH_REPO: ${{ github.repository }}` and `--prerelease`.

- [ ] **Step 2: Add an emulator gate inside the release workflow**

Add `release-emulator-tests`, conditional on `release_created == 'true'`, checking out the released tag and using the same pinned API 35 emulator configuration from Task 6. This duplication is intentional: stable promotion must not depend on timing or lookup of another workflow run.

- [ ] **Step 3: Restore the keystore only inside the signed build job**

Set `environment: production` on `build-release-artifacts`. Add a PowerShell step with only the Base64 secret in its environment:

```yaml
- name: Restore production keystore
  id: signing
  shell: pwsh
  env:
    SIGNING_KEY_BASE64: ${{ secrets.ANDROID_SIGNING_KEY_BASE64 }}
  run: |
    if ([string]::IsNullOrWhiteSpace($env:SIGNING_KEY_BASE64)) { throw 'ANDROID_SIGNING_KEY_BASE64 is missing' }
    $path = Join-Path $env:RUNNER_TEMP 'mobisentinel-production.p12'
    [IO.File]::WriteAllBytes($path, [Convert]::FromBase64String($env:SIGNING_KEY_BASE64))
    "path=$path" >> $env:GITHUB_OUTPUT
```

- [ ] **Step 4: Build and verify the production APK**

The build step receives only path/password/alias/password:

```yaml
- name: Test, lint, and build signed release APK
  env:
    ANDROID_SIGNING_STORE_FILE: ${{ steps.signing.outputs.path }}
    ANDROID_SIGNING_STORE_PASSWORD: ${{ secrets.ANDROID_SIGNING_STORE_PASSWORD }}
    ANDROID_SIGNING_KEY_ALIAS: ${{ secrets.ANDROID_SIGNING_KEY_ALIAS }}
    ANDROID_SIGNING_KEY_PASSWORD: ${{ secrets.ANDROID_SIGNING_KEY_PASSWORD }}
  run: |
    ./gradlew -p buildSrc test
    ./gradlew testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleRelease
```

Package and verify:

```powershell
$source = 'app/build/outputs/apk/release/app-release.apk'
pwsh -File scripts/verify-release-apk.ps1 `
  -Tag $env:TAG `
  -ApkPath $source `
  -CertificateSha256Path signing/release-certificate.sha256
New-Item -ItemType Directory -Path dist -Force | Out-Null
$name = "MobiSentinel-$env:VERSION.apk"
Copy-Item -LiteralPath $source -Destination (Join-Path dist $name)
$hash = (Get-FileHash -LiteralPath (Join-Path dist $name) -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $name" | Set-Content -LiteralPath (Join-Path dist "$name.sha256") -Encoding ascii
```

Add an `if: always()` cleanup step that removes only `${{ steps.signing.outputs.path }}` when it exists.

- [ ] **Step 5: Publish exact assets and promote only after all jobs**

Rename asset references to `MobiSentinel-$VERSION.apk`. Add a final `promote-stable-release` job needing release-please, staging notes, release emulator tests, build, and publish. It must retrieve asset names, compare the sorted set to the exact expected two names, then replace the staging header with:

```text
## APK de produção assinado

O APK foi construído como variante release, assinado pelo certificado oficial do MobiSentinel e aprovado pelos gates automatizados.
Confira o SHA-256 anexado e a impressão digital pública em signing/release-certificate.sha256.
```

Finally execute:

```bash
gh release edit "$TAG" --prerelease=false --latest --notes-file release-notes.md
```

- [ ] **Step 6: Add static workflow assertions**

Run a PowerShell assertion block that requires:

```powershell
$workflow = Get-Content .github\workflows\release-please.yml -Raw
@(
  'environment: production',
  'assembleRelease',
  'CertificateSha256Path',
  'connectedDebugAndroidTest',
  'MobiSentinel-$VERSION.apk',
  '--prerelease=false',
  'GH_REPO: ${{ github.repository }}'
) | ForEach-Object { if (-not $workflow.Contains($_)) { throw "Missing workflow requirement: $_" } }
if ($workflow.Contains('debug.apk')) { throw 'Production workflow must not publish debug APK names' }
```

- [ ] **Step 7: Commit**

```powershell
git add .github/workflows/release-please.yml
git commit -m "ci: publish signed production releases"
```

---

### Task 8: Exhaustively update public and operational documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/testing/manual-test-matrix.md`
- Create: `SECURITY.md`
- Create: `PRIVACY.md`
- Create: `docs/releasing/production-release.md`
- Modify: `docs/superpowers/specs/2026-07-16-release-automation-design.md`
- Modify: `docs/superpowers/plans/2026-07-16-release-automation.md`

**Interfaces:**
- Produces: consistent user installation, integrity, privacy, security, release, recovery, and accepted-risk documentation.

- [ ] **Step 1: Update README from debug-only to dual historical/production semantics**

Document:

- the repository Releases page and exact stable asset names, without fabricating a version-specific URL before 1.0.0 exists;
- permanent package and the fact that old debug installs do not update in place;
- checksum verification with `Get-FileHash`;
- certificate verification with `apksigner verify --print-certs` and comparison to `signing/release-certificate.sha256`;
- no required physical-device gate, required GitHub API 35 emulator gate;
- four accepted risks and optional post-release validation;
- GitHub now, Play later with package/certificate continuity.

Remove claims that every attached APK is debug or that physical gates are mandatory.

- [ ] **Step 2: Update the manual matrix without falsifying evidence**

Change narration enabled/disabled to `PASSOU — CONFIRMAÇÃO MANUAL DO USUÁRIO`, with date and scope. Change the four deferred rows to `RISCO ACEITO — NÃO BLOQUEIA 1.0.0`. Add a CI row for API 35 emulator that remains `PENDENTE` until the GitHub run succeeds. State that prior Moto results are historical evidence and future physical execution is optional.

- [ ] **Step 3: Add SECURITY.md**

Include supported versions, private reporting through GitHub Security Advisories, official fingerprint location, a warning never to share Drive recovery files, and incident steps: freeze releases, return affected release to prerelease, preserve evidence, inspect workflow history, and issue a higher-version remediation when the key remains trustworthy.

- [ ] **Step 4: Add PRIVACY.md**

State exactly: no account, backend, analytics, ads, history, location, contacts, phone identifiers, or external probes; Android network capabilities are processed on-device; DataStore holds activation/voice/debounce preferences; TTS text is sent only to the installed Android speech engine; no user data is collected or shared by the developer.

- [ ] **Step 5: Add the production runbook**

`docs/releasing/production-release.md` must cover prerequisites, GitHub environment/secret names, Drive backup filenames, restoring secrets with the committed script, release PR review, prerelease staging, emulator/build/verifier gates, exact promotion conditions, download/checksum/certificate verification, idempotent rerun, rollback to prerelease, and future Play continuity.

Do not put real passwords, Base64, or the private key in documentation.

- [ ] **Step 6: Mark historical automation documents as superseded**

Add a note immediately below each title:

```markdown
> Historical document: the debug-only release policy described here was superseded on 17 July 2026 by [Signed GitHub Production Release](../specs/2026-07-17-production-github-release-design.md). Historical package names and commands below are preserved as implementation evidence.
```

Adjust the relative link appropriately in the spec file.

- [ ] **Step 7: Run exhaustive documentation consistency searches**

```powershell
rg -n "não destinado à produção|gates físicos.*obrigatórios|MobiSentinel-.*-debug\.apk|com\.mobisentinel\.app" README.md SECURITY.md PRIVACY.md docs\testing docs\releasing .github scripts app
$redFlags = @('T' + 'BD', 'TO' + 'DO', 'FIX' + 'ME', 'PLACE' + 'HOLDER')
$redFlags | ForEach-Object { rg -n $_ README.md SECURITY.md PRIVACY.md docs\testing docs\releasing }
git diff --check
```

Expected: no obsolete production claim or live old package reference; historical documents are excluded from the first search by path; no placeholder; formatting clean.

- [ ] **Step 8: Commit**

```powershell
git add README.md SECURITY.md PRIVACY.md docs
git commit -m "docs: document signed production distribution"
```

---

### Task 9: Run the complete local verification matrix

**Files:** no source changes unless a failing gate exposes a defect.

**Interfaces:**
- Consumes: all implementation tasks and the real local keystore.
- Produces: fresh evidence before PR creation.

- [ ] **Step 1: Restore signing environment from the external recovery bundle**

Parse `C:\tmp\MobiSentinel-production-signing\mobisentinel-production-recovery.env` into process environment variables and set `ANDROID_SIGNING_STORE_FILE` to the local `.p12`. Do not print values.

- [ ] **Step 2: Run clean build/test/lint gates**

```powershell
.\gradlew.bat -p buildSrc clean test --console=plain
.\gradlew.bat clean testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug assembleRelease --console=plain
.\scripts\tests\create-production-signing-test.ps1
.\scripts\tests\verify-release-apk-test.ps1
.\scripts\verify-release-apk.ps1 `
  -Tag v0.1.1 `
  -ApkPath app\build\outputs\apk\release\app-release.apk `
  -CertificateSha256Path signing\release-certificate.sha256
```

Expected: all commands succeed, and the real release verifier reports package `br.com.marcocardoso.mobisentinel`, version `0.1.1`, code `1001`, and the pinned fingerprint. The implementation branch remains 0.1.1 until Release Please.

- [ ] **Step 3: Run local connected tests only if an emulator is already available**

This is diagnostic, not a substitute for GitHub:

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

If no emulator is available, record that and rely on the mandatory GitHub job; do not create a physical-device gate.

- [ ] **Step 4: Check repository hygiene**

```powershell
git status --short
git diff --check origin/master...HEAD
git ls-files | rg "\.(p12|jks|keystore)$|-recovery\.env$"
git grep -n -I "ANDROID_SIGNING_.*=" -- ':!docs/superpowers/plans/2026-07-17-production-github-release.md'
```

Expected: no private file tracked, no secret assignment, no uncommitted source change.

---

### Task 10: Review and publish the implementation PR

**Files:** all branch changes.

- [ ] **Step 1: Perform a code review against the approved spec**

Inspect `git diff --stat origin/master...HEAD`, every workflow permission, every secret boundary, package migration, verifier order, cleanup, docs, and accepted risks. Fix all P0–P2 findings and rerun Task 9 after any fix.

- [ ] **Step 2: Enable private vulnerability reporting and verify it**

```powershell
gh api --method PUT repos/marcoaureliocardoso/MobiSentinel/private-vulnerability-reporting
$enabled = gh api repos/marcoaureliocardoso/MobiSentinel/private-vulnerability-reporting --jq .enabled
if ($enabled -ne 'true') { throw 'Private vulnerability reporting is not enabled' }
```

Expected: PUT returns HTTP 204 and readback is `true`, so the channel documented in `SECURITY.md` exists.

- [ ] **Step 3: Push the branch and create a ready PR**

```powershell
git push -u origin codex/production-release
gh pr create `
  --base master `
  --head codex/production-release `
  --title "feat!: publish signed production APKs" `
  --body "Migrates to br.com.marcocardoso.mobisentinel, adds recoverable production signing, pins the certificate, gates releases on API 35 emulator tests, publishes verified signed APKs, and documents privacy/security/recovery and accepted risks."
```

- [ ] **Step 4: Wait for and inspect both CI jobs**

```powershell
gh pr checks --watch
```

Expected: `Validate` and `Instrumented tests (API 35)` both succeed. Inspect logs rather than relying only on the summary.

- [ ] **Step 5: Stop at implementation merge approval**

Present PR URL, review findings, CI URLs, package/fingerprint, Drive backup verification, GitHub secret names, and remaining accepted risks. Merge only after explicit approval.

---

### Task 11: Review and publish Release Please 1.0.0

**Files:** Release Please PR changes `.release-please-manifest.json`, `app/build.gradle.kts`, and `CHANGELOG.md`.

- [ ] **Step 1: After implementation merge, inspect the generated release PR**

Verify version `1.0.0`, `versionCode 1000000`, breaking package migration, signed distribution entry, and no secret content. Confirm both CI jobs pass on the release branch.

- [ ] **Step 2: Stop at release merge approval**

Present the exact changelog/version diff and CI evidence. Merge only after explicit approval.

- [ ] **Step 3: Observe the production release workflow**

Verify staging notes, release emulator job, signed build, certificate verification, exact two assets, cleanup, and final promotion. A failure must leave v1.0.0 as prerelease.

- [ ] **Step 4: Download and independently verify published files**

Download to a clean temporary directory, compare the checksum file, run `verify-release-apk.ps1 -Tag v1.0.0`, and confirm the public release is stable—not draft/prerelease—with only:

```text
MobiSentinel-1.0.0.apk
MobiSentinel-1.0.0.apk.sha256
```

- [ ] **Step 5: Finish key custody cleanup**

Re-list the Google Drive backup and GitHub secret names, then safely delete only the verified local directory `C:\tmp\MobiSentinel-production-signing`. Do not delete Drive or GitHub copies.
