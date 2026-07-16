# MobiSentinel

MobiSentinel é um aplicativo Android nativo que acompanha, de forma independente, a conectividade por Wi‑Fi e por dados móveis. Mudanças persistentes são confirmadas após um intervalo configurável, exibidas na interface e na notificação do serviço e, quando habilitado, narradas em português pelo Text-to-Speech do Android.

O MVP funciona inteiramente no aparelho: não possui conta, backend, analytics, histórico e não faz pings periódicos.

## Requisitos

- Windows com PowerShell.
- JDK 17 ou superior. O projeto compila para Java/Kotlin 17; o ambiente de desenvolvimento validado usa JDK 21.
- Android Studio Quail 1 ou uma instalação compatível com AGP 8.13.2.
- Android SDK em `C:\Users\Marco\AppData\Local\Android\Sdk`, ou `local.properties` ajustado para outro caminho.
- Android SDK Platform 36 e Build Tools compatíveis.
- Emulador ou aparelho com Android 8.0/API 26 ou superior. A validação registrada usou o AVD `Codex_API_35`, API 35.

O aplicativo usa `compileSdk` e `targetSdk` 36, com `minSdk` 26.

## Compilar, testar e instalar

Execute os comandos a partir da raiz do repositório:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
.\gradlew.bat installDebug
```

O gate completo usado no handoff é:

```powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Para abrir o aplicativo diretamente no emulador validado:

```powershell
$adb = 'C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb -s emulator-5554 shell am start -n com.mobisentinel.app/.MainActivity
```

## Uso

1. Abra o aplicativo e toque em **Ativar monitoramento**.
2. No Android 13 ou superior, conceda a permissão de notificações. Ela permite mostrar o estado e o controle **Parar**; o monitoramento pode continuar mesmo se a permissão for negada.
3. Abra **Configurações** para ligar ou desligar a narração de cada transporte e ajustar os intervalos de confirmação de perda e recuperação.
4. Para encerrar o monitoramento e impedir que ele volte após reiniciar o aparelho, use **Parar** na notificação persistente. Ele pode ser reativado pela tela principal.

## Arquitetura

- A interface Jetpack Compose e o `MainViewModel` apresentam o snapshot confirmado e persistem as preferências.
- `ConnectivityManager.NetworkCallback` observa Wi‑Fi e celular separadamente; o estado de internet depende da validação fornecida pelo Android.
- A máquina de transições elimina oscilações usando atrasos independentes para perda e recuperação.
- `MonitoringEngine` coordena observação, estado confirmado, preferências e narração.
- `TextToSpeech` e uma fila por transporte serializam os anúncios em português.
- `MonitoringService` mantém o trabalho contínuo como foreground service e atualiza a notificação de ID `1001` no canal `mobisentinel_monitoring`.
- Preferences DataStore guarda ativação, opções de voz e atrasos. `BootReceiver` retoma o serviço apenas quando a ativação persistida continua ligada.

A especificação e o plano detalhados estão em [docs/superpowers/specs/2026-07-16-mobisentinel-design.md](docs/superpowers/specs/2026-07-16-mobisentinel-design.md), [docs/superpowers/plans/2026-07-16-mobisentinel-mvp.md](docs/superpowers/plans/2026-07-16-mobisentinel-mvp.md), [especificação de automação de releases](docs/superpowers/specs/2026-07-16-release-automation-design.md) e [plano de automação de releases](docs/superpowers/plans/2026-07-16-release-automation.md).

## Permissões

| Permissão | Motivo |
| --- | --- |
| `ACCESS_NETWORK_STATE` | Observar disponibilidade, transporte e validação de internet sem fazer sondagens externas. |
| `POST_NOTIFICATIONS` | Exibir, no Android 13+, o estado persistente e a ação **Parar**. |
| `FOREGROUND_SERVICE` | Manter o monitoramento contínuo em um serviço visível ao usuário. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Declarar o caso de uso contínuo de alertas de conectividade no Android atual. |
| `RECEIVE_BOOT_COMPLETED` | Retomar o serviço depois do boot somente se o usuário o havia ativado. |

O MVP não solicita localização, microfone, telefone, SMS, contatos ou armazenamento.

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

## Limitações conhecidas e gates de liberação

- A validação completa de perda e recuperação de dados móveis exige um aparelho físico com SIM/eSIM e é um gate obrigatório antes de uma versão de produção.
- A narração audível, a ausência de voz com a opção desligada e o fluxo sem mecanismo TTS precisam ser confirmados em dispositivo interativo; o AVD headless não oferece evidência de áudio confiável.
- Portais cativos e redes sem internet dependem do momento em que o Android marca a rede como validada e ainda exigem um ambiente de rede dedicado.
- Após reativar o Wi‑Fi, associação e validação do próprio AVD podem acrescentar vários segundos ao intervalo configurado. O temporizador do aplicativo começa quando o Android entrega o novo estado.
- O modelo celular do emulador não substitui uma rede móvel física.
- Fabricantes podem impor restrições adicionais de bateria e inicialização automática. O comportamento deve ser revalidado nos aparelhos-alvo.
- A voz e o idioma disponíveis dependem do mecanismo Text-to-Speech instalado no Android.
- Uma publicação futura no Google Play requer declaração e revisão do foreground service `specialUse`. Publicação não faz parte deste MVP.

Os resultados reproduzíveis e os itens ainda abertos estão em [docs/testing/manual-test-matrix.md](docs/testing/manual-test-matrix.md).
