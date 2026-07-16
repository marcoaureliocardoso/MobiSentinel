# Release Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatizar versionamento SemVer, changelog, CI e publicação de um APK debug verificável em GitHub Releases, começando explicitamente em 0.1.0.

**Architecture:** Uma classe pequena em `buildSrc` valida a versão e deriva o `versionCode`; Release Please atualiza a versão anotada no Gradle e gera a PR de changelog. O CI valida todo push/PR, enquanto o workflow de release separa controle da release, dispatch de CI, notas, build somente-leitura e upload em cinco jobs com permissões mínimas. Um script PowerShell inspeciona os metadados reais do APK antes de gerar seu SHA-256.

**Tech Stack:** Kotlin/JVM, Gradle 8.13, Android Gradle Plugin 8.13.2, Java 17, PowerShell 7, GitHub Actions, Release Please 5.0.0 e GitHub CLI.

## Global Constraints

- `versionName` deve usar SemVer estrito `X.Y.Z`, sem prefixo, sufixo, metadados ou zeros à esquerda.
- Cada componente de versão deve estar entre 0 e 999.
- `versionCode = major × 1.000.000 + minor × 1.000 + patch`; 0.1.0 deve produzir 1000.
- A primeira release deve ser exatamente 0.1.0, com tag `v0.1.0`.
- `feat:` incrementa minor, `fix:` incrementa patch e breaking change incrementa major; `docs:`, `test:`, `build:` e `chore:` isolados não publicam versão.
- Releases permanecem marcadas como pré-lançamento enquanto os gates físicos de `docs/testing/manual-test-matrix.md` estiverem abertos.
- O único binário publicado é `MobiSentinel-X.Y.Z-debug.apk`, explicitamente não produtivo, acompanhado de `.sha256`.
- Nenhuma chave Android, credencial de produção, APK release ou publicação em loja entra neste plano.
- A PR de release nunca é mesclada automaticamente.
- Todas as ações externas ficam fixadas por SHA completo; as versões abaixo foram confirmadas nos repositórios oficiais em 16 de julho de 2026.
- O CI usa Java 17 e executa testes de `buildSrc`, `testDebugUnitTest`, `lintDebug` e `assembleDebug`.

## File and Responsibility Map

- Create `buildSrc/build.gradle.kts`: configura o módulo isolado que contém e testa a regra de versão.
- Create `buildSrc/src/main/kotlin/com/mobisentinel/versioning/AppVersion.kt`: valida SemVer e calcula `versionCode`.
- Create `buildSrc/src/test/kotlin/com/mobisentinel/versioning/AppVersionTest.kt`: cobre sintaxe, limites e fórmula.
- Modify `app/build.gradle.kts`: declara a versão anotada que Release Please atualiza e aplica o modelo validado.
- Create `scripts/verify-release-apk.ps1`: compara tag, package, `versionName` e `versionCode` com os metadados empacotados.
- Create `release-please-config.json`: define bootstrap 0.1.0, release simples, tag com `v`, pré-lançamento e arquivo extra.
- Create `.release-please-manifest.json`: inicia vazio e passa a ser mantido pela PR de release.
- Create `.github/workflows/ci.yml`: valida PRs, pushes na `master` e dispatches da PR de release.
- Create `.github/workflows/release-please.yml`: mantém a PR, cria a release, constrói/verifica o APK e publica os anexos.
- Modify `README.md`: documenta SemVer, Conventional Commits, natureza debug, gates e verificação de checksum.
- Generate later `CHANGELOG.md`: Release Please cria este arquivo na PR inicial; não o criar manualmente na branch de implementação.

---

### Task 1: Tested Android Version Model

**Files:**

- Create: `buildSrc/build.gradle.kts`
- Create: `buildSrc/src/test/kotlin/com/mobisentinel/versioning/AppVersionTest.kt`
- Create: `buildSrc/src/main/kotlin/com/mobisentinel/versioning/AppVersion.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**

- Produces: `AppVersion.parse(value: String): AppVersion`.
- Produces: `AppVersion.name: String`, `AppVersion.major: Int`, `minor: Int`, `patch: Int` and `versionCode: Int`.
- Consumed by: `app/build.gradle.kts` and the Release Please generic updater annotation.

- [ ] **Step 1: Configure the isolated build logic test project**

Create `buildSrc/build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit"))
}
```

- [ ] **Step 2: Write the failing version tests**

Create `buildSrc/src/test/kotlin/com/mobisentinel/versioning/AppVersionTest.kt`:

```kotlin
package com.mobisentinel.versioning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppVersionTest {
    @Test
    fun `parses 0 1 0 and derives Android version code 1000`() {
        val version = AppVersion.parse("0.1.0")

        assertEquals("0.1.0", version.name)
        assertEquals(0, version.major)
        assertEquals(1, version.minor)
        assertEquals(0, version.patch)
        assertEquals(1_000, version.versionCode)
    }

    @Test
    fun `preserves ordering across patch minor and major`() {
        assertEquals(999, AppVersion.parse("0.0.999").versionCode)
        assertEquals(1_000, AppVersion.parse("0.1.0").versionCode)
        assertEquals(1_000_000, AppVersion.parse("1.0.0").versionCode)
        assertEquals(999_999_999, AppVersion.parse("999.999.999").versionCode)
    }

    @Test
    fun `rejects non stable or non canonical SemVer`() {
        val invalid = listOf(
            "1",
            "1.2",
            "v1.2.3",
            "1.2.3-beta",
            "1.2.3+4",
            "01.2.3",
            "1.02.3",
            "1.2.03",
            "-1.2.3",
        )

        invalid.forEach { value ->
            val error = assertFailsWith<IllegalArgumentException> {
                AppVersion.parse(value)
            }
            assertTrue(error.message.orEmpty().contains("X.Y.Z"), value)
        }
    }

    @Test
    fun `rejects components greater than 999`() {
        listOf("1000.0.0", "0.1000.0", "0.0.1000").forEach { value ->
            val error = assertFailsWith<IllegalArgumentException> {
                AppVersion.parse(value)
            }
            assertTrue(error.message.orEmpty().contains("0..999"), value)
        }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run:

```powershell
.\gradlew.bat -p buildSrc test
```

Expected: `FAILED` during Kotlin test compilation with unresolved reference `AppVersion`.

- [ ] **Step 4: Implement the minimal version model**

Create `buildSrc/src/main/kotlin/com/mobisentinel/versioning/AppVersion.kt`:

```kotlin
package com.mobisentinel.versioning

data class AppVersion private constructor(
    val name: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val versionCode: Int = major * 1_000_000 + minor * 1_000 + patch

    companion object {
        private val stableSemVer =
            Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$""")

        fun parse(value: String): AppVersion {
            val match = stableSemVer.matchEntire(value)
                ?: throw IllegalArgumentException(
                    "Version '$value' must use stable SemVer X.Y.Z without prefixes or suffixes",
                )
            val components = match.groupValues.drop(1).map { component ->
                component.toLongOrNull()
                    ?: throw IllegalArgumentException("Version '$value' must use SemVer X.Y.Z")
            }
            if (components.any { it !in 0L..999L }) {
                throw IllegalArgumentException(
                    "Version '$value' must keep every component in 0..999",
                )
            }

            return AppVersion(
                name = value,
                major = components[0].toInt(),
                minor = components[1].toInt(),
                patch = components[2].toInt(),
            )
        }
    }
}
```

- [ ] **Step 5: Run the focused test to verify it passes**

Run:

```powershell
.\gradlew.bat -p buildSrc test
```

Expected: `BUILD SUCCESSFUL`; four tests pass.

- [ ] **Step 6: Wire the validated version into the Android module**

Add this import before `plugins` in `app/build.gradle.kts`:

```kotlin
import com.mobisentinel.versioning.AppVersion
```

Add this single source of version immediately after the `plugins` block:

```kotlin
val appVersion = AppVersion.parse("0.1.0") // x-release-please-version
```

Replace the two hard-coded fields in `defaultConfig` with:

```kotlin
versionCode = appVersion.versionCode
versionName = appVersion.name
```

- [ ] **Step 7: Verify configuration and assembly**

Run:

```powershell
.\gradlew.bat -p buildSrc test
.\gradlew.bat assembleDebug
```

Expected: both invocations end in `BUILD SUCCESSFUL`; `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 8: Commit the version model**

```powershell
git add buildSrc app/build.gradle.kts
git commit -m "build: derive Android version code from SemVer"
```

Expected: one commit containing only the tested version model and Gradle wiring.

### Task 2: APK Metadata Verifier

**Files:**

- Create: `scripts/verify-release-apk.ps1`
- Create: `scripts/tests/verify-release-apk-test.ps1`

**Interfaces:**

- Consumes: `-Tag vX.Y.Z`, `-ApkPath <path>` and Android SDK `aapt`.
- Produces: exit code 0 and a `Verified ...` line only when tag, package, `versionName`, and `versionCode` agree.
- Tested by: `scripts/tests/verify-release-apk-test.ps1` against the assembled APK.

- [ ] **Step 1: Demonstrate the verifier is absent**

Run:

```powershell
if (Test-Path scripts\verify-release-apk.ps1) { throw 'Verifier already exists' }
```

Expected: command exits successfully because the file does not exist.

- [ ] **Step 2: Implement the verifier**

Create `scripts/verify-release-apk.ps1`:

```powershell
[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$Tag,

    [Parameter(Mandatory)]
    [string]$ApkPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$tagMatch = [regex]::Match(
    $Tag,
    '^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$'
)
if (-not $tagMatch.Success) {
    throw "Tag '$Tag' must use vX.Y.Z with canonical numeric components"
}

$components = 1..3 | ForEach-Object { [long]$tagMatch.Groups[$_].Value }
if ($components | Where-Object { $_ -gt 999 }) {
    throw "Tag '$Tag' must keep every component in 0..999"
}

$versionName = $Tag.Substring(1)
$expectedVersionCode = [int](
    $components[0] * 1000000 + $components[1] * 1000 + $components[2]
)
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path

function Resolve-AndroidSdk {
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }

    $repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $localProperties = Join-Path $repositoryRoot 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_.StartsWith('sdk.dir=') } |
            Select-Object -First 1
        if ($sdkLine) {
            return $sdkLine.Substring('sdk.dir='.Length).
                Replace('\:', ':').
                Replace('\\', '\')
        }
    }

    throw 'Android SDK was not found through ANDROID_HOME or local.properties'
}

function Resolve-Aapt {
    $command = Get-Command aapt, aapt.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($command) {
        return $command.Source
    }
    $buildTools = Join-Path (Resolve-AndroidSdk) 'build-tools'
    $directories = Get-ChildItem -LiteralPath $buildTools -Directory |
        Sort-Object {
            [version](($_.Name -split '-')[0])
        } -Descending
    foreach ($directory in $directories) {
        foreach ($name in @('aapt', 'aapt.exe')) {
            $candidate = Join-Path $directory.FullName $name
            if (Test-Path -LiteralPath $candidate) {
                return $candidate
            }
        }
    }
    throw "aapt was not found under '$buildTools'"
}

$aapt = Resolve-Aapt
$badging = & $aapt dump badging $resolvedApk
if ($LASTEXITCODE -ne 0) {
    throw "aapt failed to inspect '$resolvedApk'"
}

$packageLine = $badging | Select-Object -First 1
$packageMatch = [regex]::Match(
    $packageLine,
    "^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'"
)
if (-not $packageMatch.Success) {
    throw "Unable to parse APK package metadata: $packageLine"
}

$actualPackage = $packageMatch.Groups[1].Value
$actualVersionCode = $packageMatch.Groups[2].Value
$actualVersionName = $packageMatch.Groups[3].Value

if ($actualPackage -ne 'com.mobisentinel.app') {
    throw "Expected package com.mobisentinel.app, found $actualPackage"
}
if ($actualVersionName -ne $versionName) {
    throw "Expected versionName $versionName, found $actualVersionName"
}
if ($actualVersionCode -ne $expectedVersionCode.ToString()) {
    throw "Expected versionCode $expectedVersionCode, found $actualVersionCode"
}

Write-Output (
    "Verified package={0} versionName={1} versionCode={2}" -f
        $actualPackage,
        $actualVersionName,
        $actualVersionCode
)
```

- [ ] **Step 3: Verify the failure path with a mismatched tag**

Run after Task 1 has assembled the APK:

```powershell
pwsh -File scripts\verify-release-apk.ps1 `
    -Tag v0.1.1 `
    -ApkPath app\build\outputs\apk\debug\app-debug.apk
if ($LASTEXITCODE -eq 0) { throw 'Mismatched tag was accepted' }
```

Expected: non-zero exit with `Expected versionName 0.1.1, found 0.1.0`.

- [ ] **Step 4: Verify the success path**

Run:

```powershell
pwsh -File scripts\verify-release-apk.ps1 `
    -Tag v0.1.0 `
    -ApkPath app\build\outputs\apk\debug\app-debug.apk
```

Expected:

```text
Verified package=com.mobisentinel.app versionName=0.1.0 versionCode=1000
```

- [ ] **Step 5: Commit the verifier**

```powershell
git add scripts/verify-release-apk.ps1
git commit -m "build: verify release APK metadata"
```

### Task 3: Release Please Bootstrap Configuration

**Files:**

- Create: `release-please-config.json`
- Create: `.release-please-manifest.json`
- Test: `app/build.gradle.kts`

**Interfaces:**

- Consumes: Conventional Commits on `master` and the `x-release-please-version` annotation.
- Produces: an initial 0.1.0 release PR, then SemVer-derived release PRs with `CHANGELOG.md` and updated Gradle version.

- [ ] **Step 1: Confirm the bootstrap files are absent**

Run:

```powershell
if (Test-Path release-please-config.json) { throw 'Config already exists' }
if (Test-Path .release-please-manifest.json) { throw 'Manifest already exists' }
```

Expected: success.

- [ ] **Step 2: Create the Release Please configuration**

Create `release-please-config.json`:

```json
{
  "$schema": "https://raw.githubusercontent.com/googleapis/release-please/main/schemas/config.json",
  "packages": {
    ".": {
      "release-type": "simple",
      "package-name": "MobiSentinel",
      "initial-version": "0.1.0",
      "include-v-in-tag": true,
      "include-component-in-tag": false,
      "prerelease": true,
      "extra-files": [
        {
          "type": "generic",
          "path": "app/build.gradle.kts"
        }
      ]
    }
  }
}
```

Create `.release-please-manifest.json`:

```json
{}
```

`initial-version` is used instead of the deprecated `release-as`. Release Please will add `".": "0.1.0"` to the manifest inside the initial release PR.

- [ ] **Step 3: Validate JSON and bootstrap invariants**

Run:

```powershell
$config = Get-Content release-please-config.json -Raw | ConvertFrom-Json
$manifest = Get-Content .release-please-manifest.json -Raw | ConvertFrom-Json
$package = $config.packages.'.'
if ($package.'initial-version' -ne '0.1.0') { throw 'Wrong initial version' }
if (-not $package.prerelease) { throw 'Release must be a prerelease' }
if (-not $package.'include-v-in-tag') { throw 'Tag must include v' }
if ($package.'extra-files'[0].path -ne 'app/build.gradle.kts') {
    throw 'Gradle version file is not configured'
}
if ((rg -n "x-release-please-version" app/build.gradle.kts).Count -ne 1) {
    throw 'Expected exactly one version annotation'
}
```

Expected: success with no validation error.

- [ ] **Step 4: Commit the bootstrap configuration**

```powershell
git add release-please-config.json .release-please-manifest.json
git commit -m "build: configure Release Please"
```

### Task 4: Continuous Integration Workflow

**Files:**

- Create: `.github/workflows/ci.yml`

**Interfaces:**

- Consumes: pushes and PRs targeting `master`, plus explicit `workflow_dispatch` from Release Please.
- Produces: one `CI / Validate` check bound to the tested commit.

- [ ] **Step 1: Create the pinned CI workflow**

Create `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches:
      - master
  pull_request:
    branches:
      - master
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  validate:
    name: Validate
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0

      - name: Validate Gradle wrapper
        uses: gradle/actions/wrapper-validation@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0

      - name: Set up Java 17
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0

      - name: Make Gradle wrapper executable
        run: chmod +x gradlew

      - name: Test version build logic
        run: ./gradlew -p buildSrc test

      - name: Test, lint, and assemble debug APK
        run: ./gradlew testDebugUnitTest lintDebug assembleDebug

      - name: Test release APK verifier
        shell: pwsh
        run: ./scripts/tests/verify-release-apk-test.ps1
```

- [ ] **Step 2: Prove every external action is SHA-pinned**

Run:

```powershell
$floating = rg --pcre2 -n 'uses:\s+[^@\s]+@(?![0-9a-f]{40}(?:\s|$))' .github/workflows
if ($LASTEXITCODE -eq 0) { throw "Floating action reference found:`n$floating" }
```

Expected: `rg` finds no floating reference.

- [ ] **Step 3: Run the local equivalents**

Run:

```powershell
.\gradlew.bat -p buildSrc test
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Expected: both invocations end in `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit CI**

```powershell
git add .github/workflows/ci.yml
git commit -m "ci: validate Android builds"
```

### Task 5: Release, Build, and Artifact Publication Workflow

**Files:**

- Create: `.github/workflows/release-please.yml`
- Consume: `scripts/verify-release-apk.ps1`

**Interfaces:**

- `release-please` job produces `prs_created`, `pr`, `release_created`, `tag_name`, and `version` and performs no follow-up mutation after the Release Please action.
- `dispatch-release-pr-ci` consumes `pr` and has only `actions: write`.
- `prepare-release-notes` consumes the release outputs and adds the non-production warning with only `contents: write`.
- `build-release-artifacts` consumes the tag/version with read-only repository access and produces workflow artifact `mobisentinel-release-X.Y.Z`.
- `publish-release-artifacts` consumes only the verified workflow artifact and uploads two known filenames with `contents: write`.

- [ ] **Step 1: Create the five-job release workflow**

Create `.github/workflows/release-please.yml`:

```yaml
name: Release Please

on:
  push:
    branches:
      - master

permissions: {}

jobs:
  release-please:
    name: Prepare release
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    outputs:
      prs_created: ${{ steps.release.outputs.prs_created }}
      pr: ${{ steps.release.outputs.pr }}
      release_created: ${{ steps.release.outputs.release_created }}
      tag_name: ${{ steps.release.outputs.tag_name }}
      version: ${{ steps.release.outputs.version }}
    steps:
      - name: Create or update release pull request
        id: release
        uses: googleapis/release-please-action@45996ed1f6d02564a971a2fa1b5860e934307cf7 # v5.0.0
        with:
          token: ${{ github.token }}
          config-file: release-please-config.json
          manifest-file: .release-please-manifest.json

  dispatch-release-pr-ci:
    name: Dispatch release PR CI
    needs: release-please
    if: ${{ needs.release-please.outputs.prs_created == 'true' }}
    runs-on: ubuntu-latest
    permissions:
      actions: write
    steps:
      - name: Dispatch CI for release pull request
        env:
          GH_TOKEN: ${{ github.token }}
          RELEASE_PR: ${{ needs.release-please.outputs.pr }}
        run: |
          release_branch="$(jq -r '.headBranchName // empty' <<<"$RELEASE_PR")"
          if [[ -z "$release_branch" ]]; then
            echo "Release Please did not return headBranchName" >&2
            exit 1
          fi
          gh workflow run ci.yml --ref "$release_branch"

  prepare-release-notes:
    name: Add release safety warning
    needs: release-please
    if: ${{ needs.release-please.outputs.release_created == 'true' }}
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - name: Add non-production warning to release notes
        env:
          GH_TOKEN: ${{ github.token }}
          TAG: ${{ needs.release-please.outputs.tag_name }}
        run: |
          release_body="$(gh release view "$TAG" --json body --jq .body)"
          {
            printf '%s\n\n' '## ⚠️ APK de depuração — não destinado à produção'
            printf '%s\n' 'O APK anexado usa assinatura debug e não deve ser publicado em loja ou tratado como build de produção.'
            printf '%s\n' 'Os gates físicos descritos em docs/testing/manual-test-matrix.md permanecem obrigatórios.'
            printf '%s\n' 'Confira a integridade do arquivo usando o SHA-256 anexado.'
            printf '\n%s\n' "$release_body"
          } > release-notes.md
          gh release edit "$TAG" --prerelease --notes-file release-notes.md

  build-release-artifacts:
    name: Build verified debug APK
    needs: release-please
    if: ${{ needs.release-please.outputs.release_created == 'true' }}
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - name: Check out released tag
        uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0 # v7.0.0
        with:
          ref: ${{ needs.release-please.outputs.tag_name }}

      - name: Validate Gradle wrapper
        uses: gradle/actions/wrapper-validation@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0

      - name: Set up Java 17
        uses: actions/setup-java@03ad4de0992f5dab5e18fcb136590ce7c4a0ac95 # v5.6.0
        with:
          distribution: temurin
          java-version: "17"

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92 # v6.2.0

      - name: Make Gradle wrapper executable
        run: chmod +x gradlew

      - name: Test version build logic
        run: ./gradlew -p buildSrc test

      - name: Repeat release gates
        run: ./gradlew testDebugUnitTest lintDebug assembleDebug

      - name: Verify and package debug APK
        shell: pwsh
        env:
          TAG: ${{ needs.release-please.outputs.tag_name }}
          VERSION: ${{ needs.release-please.outputs.version }}
        run: |
          $source = 'app/build/outputs/apk/debug/app-debug.apk'
          pwsh -File scripts/verify-release-apk.ps1 -Tag $env:TAG -ApkPath $source

          New-Item -ItemType Directory -Path dist -Force | Out-Null
          $name = "MobiSentinel-$env:VERSION-debug.apk"
          Copy-Item -LiteralPath $source -Destination (Join-Path dist $name)

          Push-Location dist
          $hash = (Get-FileHash -LiteralPath $name -Algorithm SHA256).Hash.ToLowerInvariant()
          "$hash  $name" | Set-Content -LiteralPath "$name.sha256" -Encoding ascii
          Pop-Location

      - name: Store verified workflow artifact
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: mobisentinel-release-${{ needs.release-please.outputs.version }}
          path: dist/*
          if-no-files-found: error
          retention-days: 7

  publish-release-artifacts:
    name: Attach verified artifacts
    needs:
      - release-please
      - prepare-release-notes
      - build-release-artifacts
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - name: Download verified workflow artifact
        uses: actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c # v8.0.1
        with:
          name: mobisentinel-release-${{ needs.release-please.outputs.version }}
          path: dist

      - name: Attach or replace expected release assets
        env:
          GH_TOKEN: ${{ github.token }}
          TAG: ${{ needs.release-please.outputs.tag_name }}
          VERSION: ${{ needs.release-please.outputs.version }}
        run: |
          apk="dist/MobiSentinel-$VERSION-debug.apk"
          checksum="$apk.sha256"
          test -f "$apk"
          test -f "$checksum"
          gh release upload "$TAG" "$apk" "$checksum" --clobber
```

- [ ] **Step 2: Check permission separation and pinned actions**

Run:

```powershell
$workflow = Get-Content .github\workflows\release-please.yml -Raw
foreach ($required in @(
    'actions: write',
    'pull-requests: write',
    'contents: read',
    'contents: write',
    'gh release upload',
    '--clobber'
)) {
    if (-not $workflow.Contains($required)) { throw "Missing: $required" }
}
$floating = rg --pcre2 -n 'uses:\s+[^@\s]+@(?![0-9a-f]{40}(?:\s|$))' .github/workflows
if ($LASTEXITCODE -eq 0) { throw "Floating action reference found:`n$floating" }
```

Expected: success. Only `Prepare release` can create PRs/tags, `Dispatch release PR CI` has only Actions write access, `Add release safety warning` only edits release metadata, `Attach verified artifacts` only uploads assets, and the build job has `contents: read`.

If a post-release job fails, re-run only failed jobs with `gh run rerun <run-id> --failed` or the equivalent GitHub UI action. The successful `Prepare release` job is retained, so the same tag/release is reused; no tag or release is deleted, and `--clobber` replaces only the two expected assets.

- [ ] **Step 3: Run the release build path locally**

Run:

```powershell
.\gradlew.bat -p buildSrc test
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
pwsh -File scripts\verify-release-apk.ps1 `
    -Tag v0.1.0 `
    -ApkPath app\build\outputs\apk\debug\app-debug.apk
```

Expected: both Gradle invocations succeed and the verifier reports package `com.mobisentinel.app`, version 0.1.0, code 1000.

- [ ] **Step 4: Commit release automation**

```powershell
git add .github/workflows/release-please.yml
git commit -m "ci: publish verified debug prereleases"
```

### Task 6: Versioning and Release Documentation

**Files:**

- Modify: `README.md`
- Reference: `docs/testing/manual-test-matrix.md`
- Reference: `docs/superpowers/specs/2026-07-16-release-automation-design.md`
- Reference: `docs/superpowers/plans/2026-07-16-release-automation.md`

**Interfaces:**

- Produces: the user-facing warning, version policy, checksum command, and contribution convention.

- [ ] **Step 1: Add the release policy before “Limitações conhecidas e gates de liberação”**

Insert this exact section in `README.md`:

```markdown
## Versionamento e releases

O MobiSentinel segue SemVer no formato `X.Y.Z`. O `versionCode` Android é derivado automaticamente por `major × 1.000.000 + minor × 1.000 + patch`; assim, a versão 0.1.0 usa o código 1000. Cada componente deve permanecer entre 0 e 999.

As mudanças usam Conventional Commits: `feat:` gera incremento minor, `fix:` gera patch e uma mudança incompatível indicada por `!` ou `BREAKING CHANGE:` gera major. Commits isolados de documentação, testes, build e manutenção não publicam uma nova versão.

Release Please mantém uma pull request com a próxima versão e o `CHANGELOG.md`. Essa pull request é sempre revisada e mesclada manualmente. Seu merge cria a tag `vX.Y.Z` e uma GitHub Release marcada como pré-lançamento enquanto houver gates físicos abertos.

### APK de depuração

O arquivo `MobiSentinel-X.Y.Z-debug.apk` anexado às releases usa assinatura debug. Ele serve para avaliação técnica e **não é adequado para produção, publicação em loja ou distribuição como build final**.

Cada APK possui um arquivo `.sha256`. No PowerShell, confira o hash baixado com:

```powershell
(Get-FileHash .\MobiSentinel-X.Y.Z-debug.apk -Algorithm SHA256).Hash.ToLowerInvariant()
Get-Content .\MobiSentinel-X.Y.Z-debug.apk.sha256
```

Os valores devem ser iguais. A aprovação para produção exige concluir os gates da [matriz de validação manual](docs/testing/manual-test-matrix.md), incluindo dados móveis físicos, áudio TTS e políticas de bateria dos fabricantes-alvo.
```

- [ ] **Step 2: Add the release design and plan links**

Extend the existing architecture-document sentence so it also links:

```markdown
[especificação de automação de releases](docs/superpowers/specs/2026-07-16-release-automation-design.md) e [plano de automação de releases](docs/superpowers/plans/2026-07-16-release-automation.md)
```

- [ ] **Step 3: Check documentation language and broken local links**

Run:

```powershell
rg -n "assinatura debug|não é adequado para produção|sha256|Conventional Commits|Release Please" README.md
foreach ($path in @(
    'docs/testing/manual-test-matrix.md',
    'docs/superpowers/specs/2026-07-16-release-automation-design.md',
    'docs/superpowers/plans/2026-07-16-release-automation.md'
)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing linked file: $path" }
}
```

Expected: every required warning/policy is found and all three linked files exist.

- [ ] **Step 4: Commit the documentation**

```powershell
git add README.md
git commit -m "docs: document versioning and debug releases"
```

### Task 7: Full Local Verification

**Files:**

- Verify: all files changed in Tasks 1–6.

**Interfaces:**

- Produces: fresh evidence that the implementation branch is internally consistent before GitHub mutation.

- [ ] **Step 1: Run clean version-model tests**

Run:

```powershell
.\gradlew.bat -p buildSrc clean test
```

Expected: `BUILD SUCCESSFUL`; four version tests pass.

- [ ] **Step 2: Run the clean Android gate**

Run:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug
```

Expected: `BUILD SUCCESSFUL`; JVM tests, lint, and APK assembly all pass.

- [ ] **Step 3: Inspect the clean APK**

Run:

```powershell
pwsh -File scripts\verify-release-apk.ps1 `
    -Tag v0.1.0 `
    -ApkPath app\build\outputs\apk\debug\app-debug.apk
```

Expected:

```text
Verified package=com.mobisentinel.app versionName=0.1.0 versionCode=1000
```

- [ ] **Step 4: Validate configuration and supply-chain invariants**

Run:

```powershell
Get-Content release-please-config.json -Raw | ConvertFrom-Json | Out-Null
Get-Content .release-please-manifest.json -Raw | ConvertFrom-Json | Out-Null
$floating = rg --pcre2 -n 'uses:\s+[^@\s]+@(?![0-9a-f]{40}(?:\s|$))' .github/workflows
if ($LASTEXITCODE -eq 0) { throw "Floating action reference found:`n$floating" }
git diff --check
git status --short
```

Expected: JSON parses; no floating action; `git diff --check` is silent; `git status --short` is empty.

### Task 8: Publish the Automation Branch and Bootstrap Release Please

**Files:**

- Remote state: repository Actions permission, implementation PR, Actions runs, generated release PR.

**Interfaces:**

- Produces: merged automation on `master` and an open, unmerged Release Please PR for 0.1.0.

- [ ] **Step 1: Preserve read-only defaults while allowing the bot to create the release PR**

Current state on 16 July 2026 is `default_workflow_permissions=read` and `can_approve_pull_request_reviews=false`. Run:

```powershell
gh api --method PUT `
    repos/marcoaureliocardoso/MobiSentinel/actions/permissions/workflow `
    -f default_workflow_permissions=read `
    -F can_approve_pull_request_reviews=true
```

Expected: HTTP 204. This does not let the workflow merge its own PR; job-level permissions remain explicit.

- [ ] **Step 2: Push the implementation branch and open a PR**

From the isolated worktree/branch created at execution time:

```powershell
git push -u origin codex/release-automation
gh pr create `
    --base master `
    --head codex/release-automation `
    --title "ci: automate versioned debug prereleases" `
    --body "Adds tested SemVer-to-versionCode mapping, CI, Release Please, verified debug APK publication, SHA-256 checksums, and release safety documentation."
```

Expected: GitHub returns the implementation PR URL.

- [ ] **Step 3: Wait for and inspect CI**

Run:

```powershell
gh pr checks --watch
```

Expected: `CI / Validate` succeeds. Do not merge on a failure; inspect the run, fix locally, repeat Tasks 7.1–7.4, commit, and push.

- [ ] **Step 4: Merge the automation PR after its checks pass**

Run:

```powershell
gh pr merge --squash --delete-branch
```

Expected: implementation PR is merged into `master` and the push starts `Release Please`.

- [ ] **Step 5: Watch Release Please and inspect the generated PR**

Run:

```powershell
$runId = gh run list --workflow release-please.yml --limit 1 --json databaseId --jq '.[0].databaseId'
gh run watch $runId --exit-status
$releasePr = gh pr list --label 'autorelease: pending' --state open --json number,url --jq '.[0]'
$releasePr
```

Expected: workflow succeeds and `$releasePr` identifies one open PR.

Inspect it:

```powershell
$releasePrNumber = $releasePr | ConvertFrom-Json | Select-Object -ExpandProperty number
gh pr diff $releasePrNumber --name-only
gh pr checks $releasePrNumber
```

Expected files include `.release-please-manifest.json` and `CHANGELOG.md`; CI succeeds for the release branch through the explicit `workflow_dispatch`. Because the source already declares 0.1.0, `app/build.gradle.kts` may be unchanged in this first PR; confirm that its single annotated value remains 0.1.0.

- [ ] **Step 6: Stop at the human release gate**

Present the PR URL, proposed 0.1.0 changelog, version diff, and CI result to the user. Do not merge the Release Please PR until the user explicitly approves that generated release content.

### Task 9: Publish and Verify v0.1.0 After Explicit Approval

**Files:**

- Remote state: Release Please PR, tag `v0.1.0`, GitHub prerelease, two assets.

**Interfaces:**

- Produces: the first auditable prerelease with source, verified debug APK, and SHA-256.

- [ ] **Step 1: Merge the approved Release Please PR**

Run only after explicit approval:

```powershell
gh pr merge $releasePrNumber --squash --delete-branch
```

Expected: merge succeeds; the resulting push to `master` restarts `Release Please` and creates `v0.1.0`.

- [ ] **Step 2: Watch the publication workflow**

Run:

```powershell
$runId = gh run list --workflow release-please.yml --limit 1 --json databaseId --jq '.[0].databaseId'
gh run watch $runId --exit-status
```

Expected: `Prepare release`, `Dispatch release PR CI` when applicable, `Add release safety warning`, `Build verified debug APK`, and `Attach verified artifacts` all succeed.

- [ ] **Step 3: Verify tag, prerelease state, warning, and assets**

Run:

```powershell
gh release view v0.1.0 --json tagName,isPrerelease,body,assets,url
```

Expected:

- `tagName` is `v0.1.0`;
- `isPrerelease` is `true`;
- body contains `APK de depuração` and `não destinado à produção`;
- assets are exactly `MobiSentinel-0.1.0-debug.apk` and `MobiSentinel-0.1.0-debug.apk.sha256`.

- [ ] **Step 4: Download and independently verify the checksum**

Run:

```powershell
$destination = 'C:\tmp\mobisentinel-v0.1.0'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
gh release download v0.1.0 `
    --pattern 'MobiSentinel-0.1.0-debug.apk*' `
    --dir $destination `
    --clobber

$apk = Join-Path $destination 'MobiSentinel-0.1.0-debug.apk'
$checksumFile = "$apk.sha256"
$actual = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$expected = (Get-Content -LiteralPath $checksumFile).Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)[0]
if ($actual -ne $expected) { throw "Checksum mismatch: $actual != $expected" }
pwsh -File scripts\verify-release-apk.ps1 -Tag v0.1.0 -ApkPath $apk
```

Expected: hashes match and APK metadata verifies as package `com.mobisentinel.app`, version 0.1.0, code 1000.

- [ ] **Step 5: Record final evidence**

Report the release URL, workflow run URL, tag, prerelease status, asset names, SHA-256, and remaining physical gates. Do not claim production readiness.
