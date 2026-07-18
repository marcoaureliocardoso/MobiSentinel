# Matriz de validação do MobiSentinel

Data da execução física mais recente: 17 de julho de 2026

Ambiente físico de referência: Moto G54 5G, Android 15/API 35, dois SIMs ativos, operadoras exibidas pelo sistema NuCel e Vivo, build debug anterior à identidade definitiva de produção.

Ambiente automatizado obrigatório: emulador Android/API 35 com Google APIs no GitHub Actions.

Nenhum número de telefone, ICCID, IMSI, endereço IP, BSSID, MAC ou serial do aparelho é registrado neste documento.

## Critérios

- **PASSOU:** comportamento observado e evidência registrada no ambiente indicado.
- **PASSOU COM RESSALVA:** o comportamento principal passou, mas uma condição específica não pôde ser observada diretamente.
- **NÃO EXECUTADO — RISCO ACEITO:** não existe evidência prática desse cenário; a lacuna foi aceita para a distribuição atual.
- **GATE AUTOMATIZADO:** precisa passar no GitHub Actions antes da promoção de uma release.

Teste em dispositivo físico não é obrigatório para a release atual. A evidência do Moto permanece como referência de comportamento real.

## Resultados no Moto G54 5G

| Cenário | Resultado | Evidência e observações |
| --- | --- | --- |
| Instalação limpa e abertura | PASSOU | `installDebug` concluiu com `BUILD SUCCESSFUL`; somente os dados de QA do aplicativo foram limpos; a árvore de UI exibiu **Ativar monitoramento**. |
| Ativação, serviço e notificação | PASSOU | A tela mostrou **Monitoramento ativo** e ambos os cartões foram validados. `dumpsys activity services` confirmou o foreground service; a notificação ID `1001` ficou ativa com a ação **Parar**. |
| Perda e recuperação de Wi‑Fi | PASSOU | O bloco real do Android desligou o Wi‑Fi; o app mostrou `Wi-Fi: desconectado` mantendo `Dados móveis: conectado com internet`. Ao religar, o Wi‑Fi voltou a `conectado com internet` em 11,6 s. |
| Dados móveis com Wi‑Fi desligado | PASSOU | O bloco **Dados móveis** confirmou **Desativados**. O app confirmou a perda celular em 41,6 s com Wi‑Fi desconectado e a recuperação em 15,4 s após o bloco voltar à operadora. |
| Dados móveis com Wi‑Fi ligado | PASSOU | O Wi‑Fi permaneceu `conectado com internet`; a perda celular foi confirmada em 67,8 s e a recuperação em 9,6 s. Não houve crash. |
| Modo avião real | PASSOU | O bloco **Modo avião** confirmou **Ativado**. Após 9,5 s, ambos os cartões ainda estavam conectados, comprovando ausência de inferência imediata. Em 22,2 s somente os dados móveis foram confirmados como desconectados; o Wi‑Fi permaneceu validado. |
| Wi‑Fi durante modo avião | PASSOU | Com modo avião ainda ativo, desligar o Wi‑Fi produziu ambos desconectados em 8,7 s; religá-lo produziu Wi‑Fi com internet e celular desconectado em 10,6 s. |
| Saída do modo avião | PASSOU | O bloco confirmou **Desativado** e o app mostrou Wi‑Fi e dados móveis com internet em 15,7 s. |
| Três ciclos periódicos estáveis | PASSOU | Após três janelas superiores a 60 s, ambos os cartões permaneceram conectados. Em cada amostra havia exatamente duas callbacks permanentes `LISTEN`, zero `REQUEST` celular temporária remanescente e zero crash. |
| Parada e limpeza da sonda | PASSOU COM RESSALVA | Após acionar **Parar** e aguardar 18 s: serviço ausente, zero notificação ativa e zero callback/solicitação do app. A corrida durante registro é validada por teste JVM determinístico. |
| Tela apagada e segundo plano | PASSOU | Com `mWakefulness=Dozing`, os dados móveis foram desligados pelo comando oficial `cmd phone data disable`. Após 75 s, ao acordar, Wi‑Fi continuava com internet e dados móveis estavam desconectados. A recuperação com a tela novamente apagada ocorreu em 30 s, sem crash. |
| Reinício com monitoramento habilitado | PASSOU | Após o boot e o primeiro desbloqueio, o foreground service e a notificação retornaram. A preferência continuou **Monitoramento ativo**. |
| Parada explícita | PASSOU | **Parar monitoramento** removeu serviço e notificação e persistiu **Monitoramento inativo**. |
| Reinício após parada | PASSOU | Após novo boot e desbloqueio, serviço e notificação continuaram ausentes e a interface permaneceu **Monitoramento inativo**. |
| Narração habilitada e desabilitada | PASSOU | O responsável confirmou manualmente a narração quando ligada e o silêncio quando desligada. A automação cobre mensagens, fila e seleção independente por transporte. |
| Rede sem internet/portal cativo | NÃO EXECUTADO — RISCO ACEITO | Não havia rede controlada confiável. O comportamento depende de `NET_CAPABILITY_VALIDATED` concedida pelo Android. |
| Mecanismo TTS indisponível | NÃO EXECUTADO — RISCO ACEITO | O aparelho possuía TTS. O estado de indisponibilidade tem cobertura de interface, mas não evidência em aparelho sem voz pronta. |
| Restrição prolongada de bateria do fabricante | NÃO EXECUTADO — RISCO ACEITO | Tela apagada e reinícios passaram; uma janela prolongada sob políticas agressivas permanece validação opcional pós-release. |
| Android 8–11 físico | NÃO EXECUTADO — RISCO ACEITO | A política API 26–30 é coberta por teste e não pede `READ_PHONE_STATE`; a evidência em aparelho físico antigo permanece opcional. |

## Gates automatizados

O CI executa:

```powershell
.\gradlew.bat -p buildSrc test
.\gradlew.bat testDebugUnitTest testReleaseUnitTest lintDebug lintRelease assembleDebug
.\scripts\tests\verify-release-apk-test.ps1
.\scripts\tests\release-workflow-test.ps1
.\gradlew.bat connectedDebugAndroidTest
```

Os testes instrumentados são obrigatórios em emulador Android 35 no GitHub Actions. O workflow de release repete o gate do emulador no tag publicado antes de disponibilizar secrets, assinar o APK ou promover a versão.

O verificador constrói um APK release com chave temporária e cobre: metadados válidos, divergência de versão, sintaxe da tag, faixa de componentes, certificado incorreto e rejeição de APK depurável. Na publicação, ele compara o certificado do APK com `signing/release-certificate.sha256`.

A cobertura funcional inclui máquina de estados/debounce, observação por transporte, política e sonda celular ativa, timeout, execução única, coalescimento, cancelamento durante registro, compatibilidade API 26–30/API 31+, receiver de modo avião, preferências, mensagens em português, fila TTS, coordenação do monitoramento, notificação, ViewModel, telas Compose e decisão de restart.

## Estado final do aparelho de referência

Ao encerrar a execução física:

- modo avião desligado;
- Wi‑Fi habilitado;
- dados móveis habilitados e operadora ativa;
- MobiSentinel instalado e **Monitoramento inativo**;
- nenhum serviço ou notificação do MobiSentinel ativo.

## Validações opcionais pós-release

1. Repetir o cenário sem mecanismo TTS instalado.
2. Validar Wi‑Fi sem internet e portal cativo em rede controlada.
3. Executar teste prolongado sob políticas de bateria dos fabricantes-alvo.
4. Executar a compatibilidade em pelo menos um aparelho Android 8–11/API 26–30.

Esses itens são riscos aceitos, não gates da distribuição atual. Qualquer regressão confirmada deve abrir uma correção versionada e pode justificar retornar a release afetada para pré-release.
