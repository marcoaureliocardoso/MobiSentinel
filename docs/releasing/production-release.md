# Runbook de release de produção

Este documento descreve a distribuição inicial pelo GitHub. Google Play fica para uma etapa futura, preservando o pacote `br.com.marcocardoso.mobisentinel` e a mesma chave de assinatura.

## Pré-requisitos

- acesso de manutenção ao repositório `marcoaureliocardoso/MobiSentinel`;
- GitHub Actions habilitado;
- ambiente GitHub `production` com os quatro secrets abaixo;
- fingerprint público versionado em `signing/release-certificate.sha256`;
- backup pessoal do material de recuperação no Google Drive;
- CI da branch de implementação aprovado.

Secrets obrigatórios do ambiente `production`:

- `ANDROID_SIGNING_KEY_BASE64`;
- `ANDROID_SIGNING_STORE_PASSWORD`;
- `ANDROID_SIGNING_KEY_ALIAS`;
- `ANDROID_SIGNING_KEY_PASSWORD`.

Arquivos do backup `Meu Drive/MobiSentinel/production-signing`:

- `mobisentinel-production.p12`;
- `mobisentinel-production-recovery.env`;
- `release-certificate.sha256`;
- `README.txt`.

Não altere o compartilhamento da pasta e não exponha conteúdo, senha ou Base64 em terminal, issue, pull request ou logs.

## Restaurar os secrets

Em uma estação confiável, monte ou baixe a pasta de backup e execute:

```powershell
.\scripts\configure-production-environment.ps1 `
  -Repository marcoaureliocardoso/MobiSentinel `
  -SigningDirectory 'C:\caminho\privado\production-signing'
```

O script cria/reutiliza o ambiente `production`, envia os quatro valores pelo stdin do `gh` e confirma apenas os nomes existentes. Depois, compare o arquivo de fingerprint do backup com `signing/release-certificate.sha256`. Nunca imprima os valores dos secrets.

## Criar uma release

1. Faça merge da implementação somente depois de todos os checks da PR passarem.
2. Aguarde o Release Please criar ou atualizar sua PR de versão.
3. Revise nessa PR:
   - versão SemVer e `versionCode`;
   - `CHANGELOG.md` e mudança incompatível da identidade do pacote;
   - ausência de chave, senha, Base64, APK ou arquivo de recuperação;
   - os jobs **Validate** e **Android 35 emulator**.
4. Faça o merge manual da PR de release. Não crie a tag manualmente.

Release Please cria a tag e uma GitHub Release como pré-release. Essa condição é o estado seguro enquanto o pipeline trabalha.

## Gates de promoção

O workflow `.github/workflows/release-please.yml` precisa concluir, nesta ordem lógica:

1. marcar as notas como **Release em validação automatizada**;
2. executar `connectedDebugAndroidTest` no emulador Android 35;
3. entrar no ambiente protegido `production`;
4. reconstruir o PKCS#12 apenas em `$RUNNER_TEMP`;
5. executar testes, lint e builds das variantes debug e release;
6. verificar pacote, versão, `versionCode`, ausência de `debuggable`, assinatura e certificado;
7. apagar a cópia da chave do runner com `if: always()`;
8. anexar exatamente o APK e seu checksum;
9. reler os assets e exigir o conjunto exato antes de usar `--prerelease=false --latest`.

O job de build só recebe secrets depois que o emulador passa. Qualquer falha mantém a release como pré-release e impede a promoção.

## Resultado esperado

Uma release estável contém somente:

```text
MobiSentinel-X.Y.Z.apk
MobiSentinel-X.Y.Z.apk.sha256
```

O APK deve apresentar:

- package `br.com.marcocardoso.mobisentinel`;
- `versionName` igual à tag sem `v`;
- `versionCode = major × 1.000.000 + minor × 1.000 + patch`;
- nenhum atributo `application-debuggable`;
- um único signatário com SHA-256 igual ao arquivo versionado.

## Verificação independente do download

Baixe os dois assets para uma pasta limpa e execute:

```powershell
$apk = '.\MobiSentinel-X.Y.Z.apk'
$expected = (Get-Content "$apk.sha256" -Raw).Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)[0]
$actual = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw 'Checksum divergente' }

.\scripts\verify-release-apk.ps1 `
  -Tag vX.Y.Z `
  -ApkPath $apk `
  -CertificateSha256Path .\signing\release-certificate.sha256
```

Confirme também que a release não está como draft/prerelease e que não há assets extras.

## Reexecução e falhas parciais

Os uploads usam `--clobber`, então um rerun substitui os dois nomes esperados. A promoção continua bloqueada se existir qualquer terceiro asset; remova um asset inesperado somente após investigar sua origem e preserve a evidência necessária.

Se o workflow falhar:

- não edite a tag nem aumente a versão para contornar o erro;
- corrija o workflow/código em nova PR;
- execute novamente o job apropriado ou publique uma versão superior quando a tag já representar conteúdo imutável;
- mantenha a release como pré-release até todos os gates passarem.

## Retornar uma release para pré-release

Quando um problema for descoberto após a promoção:

```powershell
gh release edit vX.Y.Z --repo marcoaureliocardoso/MobiSentinel --prerelease --latest=false
```

Não apague tag, release, logs ou assets antes de preservar a evidência. Corrija com uma versão superior. Para suspeita envolvendo a chave, siga [SECURITY.md](../../SECURITY.md).

## Continuidade futura no Google Play

O primeiro upload na Play deverá usar o mesmo package e certificado. Não gere uma nova chave por conveniência. Antes da publicação, revise as exigências vigentes de App Signing, foreground service `specialUse`, política de privacidade e testes de faixa fechada.

Depois que a primeira release assinada for verificada e o backup do Drive for relido, a cópia local temporária poderá ser removida com cuidado. As cópias do Drive e os secrets do GitHub devem permanecer.
