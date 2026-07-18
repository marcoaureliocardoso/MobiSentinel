# MobiSentinel

MobiSentinel é um aplicativo Android nativo que diagnostica separadamente a conectividade por Wi‑Fi e por dados móveis. Mudanças persistentes são confirmadas após um intervalo configurável, exibidas na interface e na notificação do serviço e, quando habilitado, narradas em português pelo Text-to-Speech do Android e, opcionalmente, sinalizadas por vibração. Um horário silencioso local pode descartar voz e vibração automáticas sem interromper o diagnóstico.

O aplicativo funciona inteiramente no aparelho. Não possui conta, backend, analytics, anúncios ou histórico e não envia pings, requisições ou outros pacotes próprios para servidores externos. Consulte a [política de privacidade](PRIVACY.md) e a [política de segurança](SECURITY.md).

## Instalar a versão de produção

1. Abra a página de [Releases do GitHub](https://github.com/marcoaureliocardoso/MobiSentinel/releases) e escolha a versão estável mais recente, sem o rótulo **Pre-release**.
2. Baixe exatamente `MobiSentinel-X.Y.Z.apk` e `MobiSentinel-X.Y.Z.apk.sha256`.
3. Confira o SHA-256 antes de instalar:

```powershell
$apk = '.\MobiSentinel-X.Y.Z.apk'
$expected = (Get-Content "$apk.sha256" -Raw).Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)[0]
$actual = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw 'O checksum do APK não confere' }
```

4. Se desejar validar também a assinatura, execute o `apksigner` do Android SDK e compare a linha `certificate SHA-256 digest` com [signing/release-certificate.sha256](signing/release-certificate.sha256):

```powershell
$apksigner = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Filter apksigner.bat -Recurse |
  Sort-Object FullName -Descending | Select-Object -First 1
& $apksigner.FullName verify --verbose --print-certs .\MobiSentinel-X.Y.Z.apk
```

5. Abra o APK no Android. O sistema pode pedir autorização para instalar aplicativos dessa origem; conceda-a somente ao aplicativo usado para abrir o arquivo.

O identificador permanente do aplicativo é `br.com.marcocardoso.mobisentinel`. Versões experimentais antigas usavam outra identidade, portanto podem coexistir com a versão de produção e não são atualizadas no lugar dela. Depois de confirmar que não há preferências antigas que deseje consultar, desinstale manualmente o aplicativo experimental para evitar confusão entre os dois ícones.

## Uso

1. Abra o aplicativo e toque em **Ativar monitoramento**.
2. No Android 13 ou superior, conceda a permissão de notificações. Ela permite mostrar o estado e o controle **Parar**; o monitoramento pode continuar mesmo se a permissão for negada.
3. Abra **Configurações** para selecionar, de forma independente, voz e vibração para Wi‑Fi e dados móveis, ajustar os intervalos de confirmação de perda e recuperação e configurar o horário silencioso global. Os seletores hápticos e o horário silencioso começam desligados; a faixa inicial é `22:00–07:00`.
4. Para encerrar o monitoramento e impedir que ele volte após reiniciar o aparelho, use **Parar** na notificação persistente. Ele pode ser reativado pela tela principal.

## Como o diagnóstico funciona

- `ConnectivityManager.NetworkCallback` observa o Wi‑Fi passivamente.
- Para manter o diagnóstico celular independente do Wi‑Fi, o aplicativo solicita temporariamente uma rede móvel ao iniciar, após eventos relevantes e 60 segundos depois da verificação anterior.
- A sonda celular aguarda por até 15 segundos a capacidade `NET_CAPABILITY_VALIDATED` concedida pelo próprio Android. Callbacks celulares passivos apenas disparam uma nova verificação e nunca definem o estado diretamente.
- No Android 12/API 31 ou superior, mudanças na opção de dados móveis também disparam uma sonda por `TelephonyCallback`. No Android 8–11/API 26–30, permanecem os gatilhos inicial, periódico, passivo e de modo avião, sem solicitar `READ_PHONE_STATE`.
- Modo avião é somente um gatilho: Wi‑Fi e celular são reavaliados separadamente, sem inferência automática de desconexão.
- A máquina de transições elimina oscilações usando atrasos independentes para perda e recuperação.
- `MonitoringService` mantém o trabalho como foreground service. `BootReceiver` o retoma após o primeiro desbloqueio somente quando a ativação persistida continua ligada.

Não há sonda para `8.8.8.8`, `1.1.1.1` ou qualquer outro host. Uma sonda externa poderá ser avaliada no futuro, mas não faz parte do comportamento ou das permissões atuais.

## Alertas e horário silencioso

A narração e a vibração são decididas separadamente para Wi‑Fi e dados móveis. A vibração automática ocorre somente quando uma transição confirmada cruza entre `CONNECTED` e um estado sem internet; `CONNECTED_NO_INTERNET` conta como sem internet. Mudanças entre estados que já estão no mesmo lado dessa fronteira não vibram.

- perda: duas vibrações de `120 ms`, separadas por uma pausa de `120 ms`;
- recuperação: uma vibração de `350 ms`.

O teste manual de vibração executa os dois padrões, perda e recuperação, mesmo que os seletores por transporte estejam desligados ou o horário silencioso esteja ativo. Ele serve para verificar a integração com o aparelho; a intensidade percebida depende do hardware e do Android.

No horário silencioso, o início é inclusivo e o fim é exclusivo. A faixa pode cruzar a meia-noite — por exemplo, `22:00–07:00` cobre `22:00`, mas não `07:00`. Voz e vibração automáticas ocorridas nessa faixa são descartadas, não adiadas. Monitoramento, interface e notificação continuam funcionando normalmente.

Fora da faixa silenciosa interna, o aplicativo tenta vibrar quando o padrão está habilitado. O modo **Não perturbe** do Android e regras do fabricante ainda podem bloquear o efeito: o MobiSentinel não solicita acesso à política DND, não tenta contorná-la e não promete bypass.

## Permissões

| Permissão | Motivo |
| --- | --- |
| `ACCESS_NETWORK_STATE` | Observar disponibilidade, transporte e validação de internet sem sondagens externas. |
| `CHANGE_NETWORK_STATE` | Solicitar temporariamente uma rede celular para validação independente do Wi‑Fi. |
| `POST_NOTIFICATIONS` | Exibir, no Android 13+, o estado persistente e a ação **Parar**. |
| `FOREGROUND_SERVICE` | Manter o monitoramento contínuo em um serviço visível ao usuário. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Declarar o caso de uso contínuo de alertas de conectividade no Android atual. |
| `RECEIVE_BOOT_COMPLETED` | Retomar o serviço depois do boot somente se o usuário o havia ativado. |
| `VIBRATE` | Executar os padrões configurados e o teste manual; é uma permissão normal, sem diálogo de runtime. |

O aplicativo não declara `INTERNET` e não solicita `ACCESS_NOTIFICATION_POLICY`, localização, microfone, telefone, SMS, contatos ou armazenamento. Não envia sondas externas nem coleta dados.

## Desenvolvimento

Requisitos:

- Windows com PowerShell 7;
- JDK 17 ou superior;
- Android Studio compatível com AGP 8.13.2;
- Android SDK Platform 36 e Build Tools compatíveis;
- Android 8.0/API 26 ou superior para execução.

O projeto usa `compileSdk` e `targetSdk` 36, `minSdk` 26 e Java/Kotlin 17. Ajuste `local.properties` quando o SDK não estiver no caminho padrão.

Comandos principais:

```powershell
.\gradlew.bat -p buildSrc test
.\gradlew.bat testDebugUnitTest testReleaseUnitTest
.\gradlew.bat lintDebug lintRelease assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\scripts\tests\privacy-manifest-test.ps1
.\scripts\tests\create-production-signing-test.ps1
.\scripts\tests\configure-production-environment-test.ps1
.\scripts\tests\verify-release-apk-test.ps1
.\scripts\tests\release-workflow-test.ps1
.\scripts\tests\cross-platform-script-test.ps1
```

Os testes JVM cobrem a política de alertas, horário silencioso, controlador háptico e integração com o engine. O gate de manifesto confirma `VIBRATE` e a ausência de `INTERNET` e `ACCESS_NOTIFICATION_POLICY`; o gate instrumentado obrigatório roda a interface e o DataStore em emulador Android 35. A percepção tátil em aparelho físico é uma validação opcional e não substitui esses gates.

Para abrir um build debug em um alvo ADB:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -n br.com.marcocardoso.mobisentinel/.MainActivity
```

`assembleRelease` falha intencionalmente sem as quatro variáveis `ANDROID_SIGNING_*`. Chaves reais não devem ser colocadas no repositório ou em `gradle.properties`; consulte o [runbook de produção](docs/releasing/production-release.md).

## Versionamento e releases

O MobiSentinel segue SemVer estrito `X.Y.Z`. O `versionCode` Android é `major × 1.000.000 + minor × 1.000 + patch`; cada componente deve permanecer entre 0 e 999.

Conventional Commits determinam a próxima versão: `feat:` gera minor, `fix:` gera patch e `!` ou `BREAKING CHANGE:` gera major. Commits isolados de documentação, testes, build e manutenção não publicam uma versão.

Release Please mantém uma pull request com a próxima versão e o `CHANGELOG.md`. O merge dessa PR é sempre manual. Depois do merge:

1. a GitHub Release nasce como pré-release;
2. testes JVM, lint e testes instrumentados no emulador Android 35 são executados;
3. o APK release é assinado pelo ambiente protegido `production`;
4. pacote, versão, ausência de `debuggable` e certificado são verificados;
5. somente `MobiSentinel-X.Y.Z.apk` e seu `.sha256` são anexados;
6. a release é promovida para estável apenas se todos os gates passarem.

Qualquer falha mantém a versão como pré-release. Publicação no Google Play está fora deste ciclo e poderá ser adicionada futuramente preservando a mesma chave e identidade do aplicativo.

## Evidência e riscos aceitos

A [matriz de validação](docs/testing/manual-test-matrix.md) registra os testes executados no Moto G54 5G e no emulador. A narração ligada e o silêncio com a narração desligada foram confirmados manualmente. Teste em aparelho físico não é gate obrigatório para a distribuição atual; o gate instrumentado obrigatório roda no GitHub Actions com Android 35.

Quatro cenários permanecem como riscos conhecidos e aceitos nesta etapa:

- funcionamento quando não existe mecanismo TTS instalado;
- Wi‑Fi associado sem internet ou atrás de portal cativo;
- execução prolongada sob restrições agressivas de bateria do fabricante;
- compatibilidade prática em aparelhos físicos Android 8–11/API 26–30.

Esses cenários podem ser revalidados depois da release e não impedem a publicação atual. A voz e o idioma continuam dependendo do mecanismo Text-to-Speech instalado, e fabricantes podem impor restrições adicionais de bateria e inicialização automática.

Documentação de projeto: [design do MVP](docs/superpowers/specs/2026-07-16-mobisentinel-design.md), [validação celular ativa](docs/superpowers/specs/2026-07-16-cellular-active-validation-design.md), [design da release assinada](docs/superpowers/specs/2026-07-17-production-github-release-design.md), [design de alertas hápticos e horário silencioso](docs/superpowers/specs/2026-07-18-haptic-alerts-quiet-hours-design.md), [plano de implementação da feature](docs/superpowers/plans/2026-07-18-haptic-alerts-quiet-hours.md) e [runbook de produção](docs/releasing/production-release.md).
