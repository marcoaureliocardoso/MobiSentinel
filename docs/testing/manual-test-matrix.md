# Matriz de validação manual do MobiSentinel

Data da execução mais recente: 17 de julho de 2026

Ambiente físico principal: Moto G54 5G, Android 15/API 35, dois SIMs ativos, operadoras exibidas pelo sistema NuCel e Vivo, build debug

Ambiente complementar: AVD `Codex_API_35`, Android/API 35

ADB: `C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe`

Nenhum número de telefone, ICCID, IMSI, endereço IP, BSSID, MAC ou serial do aparelho é registrado neste documento.

## Critérios

- **PASSOU:** comportamento observado e evidência registrada no ambiente indicado.
- **PASSOU COM RESSALVA:** o comportamento principal passou, mas uma condição específica não pôde ser observada diretamente.
- **NÃO EXECUTADO:** não havia ambiente seguro e confiável para produzir evidência.
- **GATE DE LIBERAÇÃO:** precisa passar em ambiente representativo antes de produção.

## Resultados no Moto G54 5G

| Cenário | Resultado | Evidência e observações |
| --- | --- | --- |
| Instalação limpa e abertura | PASSOU | `installDebug` concluiu com `BUILD SUCCESSFUL`; somente os dados de QA de `com.mobisentinel.app` foram limpos; a árvore de UI exibiu **Ativar monitoramento**. |
| Ativação, serviço e notificação | PASSOU | A tela mostrou **Monitoramento ativo** e ambos os cartões foram validados. `dumpsys activity services` confirmou o foreground service; a notificação ID `1001` ficou ativa com a ação **Parar**. |
| Perda e recuperação de Wi‑Fi | PASSOU | O bloco real do Android desligou o Wi‑Fi; o app mostrou `Wi-Fi: desconectado` mantendo `Dados móveis: conectado com internet`. Ao religar, o Wi‑Fi voltou a `conectado com internet` em 11,6 s. |
| Dados móveis com Wi‑Fi desligado | PASSOU | O bloco **Dados móveis** confirmou **Desativados**. O app confirmou a perda celular em 41,6 s com Wi‑Fi desconectado e a recuperação em 15,4 s após o bloco voltar a **Vivo**. |
| Dados móveis com Wi‑Fi ligado | PASSOU | O Wi‑Fi permaneceu `conectado com internet`; a perda celular foi confirmada em 67,8 s e a recuperação em 9,6 s. Não houve crash. |
| Modo avião real | PASSOU | O bloco **Modo avião** confirmou **Ativado**. Após 9,5 s, ambos os cartões ainda estavam conectados, comprovando ausência de inferência imediata. Em 22,2 s somente os dados móveis foram confirmados como desconectados; o Wi‑Fi permaneceu validado. |
| Wi‑Fi durante modo avião | PASSOU | Com modo avião ainda ativo, desligar o Wi‑Fi produziu ambos desconectados em 8,7 s; religá-lo produziu Wi‑Fi com internet e celular desconectado em 10,6 s. O ajuste `airplane_mode_on` permaneceu `1`. |
| Saída do modo avião | PASSOU | O bloco confirmou **Desativado** e o app mostrou Wi‑Fi e dados móveis com internet em 15,7 s. |
| Três ciclos periódicos estáveis | PASSOU | Após três janelas superiores a 60 s, ambos os cartões permaneceram conectados. Em cada amostra havia exatamente duas callbacks permanentes `LISTEN`, zero `REQUEST` celular temporária remanescente e zero crash. |
| Parada e limpeza da sonda | PASSOU COM RESSALVA | A ação **Parar** foi localizada e acionada pela árvore de acessibilidade. Após 18 s: serviço ausente, zero notificação ativa e zero callback/solicitação do app. Neste aparelho, quando celular e modo avião já estavam indisponíveis, o Android respondeu `onUnavailable` rápido demais para capturar fisicamente `REQUEST=1`; a corrida durante registro é validada por teste JVM determinístico. |
| Tela apagada e segundo plano | PASSOU | Com `mWakefulness=Dozing`, os dados móveis foram desligados pelo comando oficial `cmd phone data disable`. Após 75 s, ao acordar, Wi‑Fi continuava com internet e dados móveis estavam desconectados. A recuperação com a tela novamente apagada ocorreu em 30 s, sem crash. |
| Reinício com monitoramento habilitado | PASSOU | Após o boot e o primeiro desbloqueio, o log registrou foreground service autorizado pelo motivo `BOOT_COMPLETED`; serviço e notificação retornaram. A preferência continuou **Monitoramento ativo**. |
| Parada explícita | PASSOU | **Parar monitoramento** removeu serviço e notificação e persistiu **Monitoramento inativo**. |
| Reinício após parada | PASSOU | Após o segundo boot e desbloqueio, serviço e notificação continuaram ausentes e a interface permaneceu **Monitoramento inativo**. |
| Narração audível | NÃO EXECUTADO | Os testes automatizados cobrem mensagens, fila e seleção por transporte. A execução por ADB não consegue comprovar audição; a validação audível de Wi‑Fi informada pelo usuário permanece evidência externa, e a escuta celular ainda deve ser feita interativamente. |
| Rede sem internet/portal cativo | NÃO EXECUTADO | Não havia rede controlada confiável. Validar que o estado permanece **Conectado sem internet** até o Android conceder `NET_CAPABILITY_VALIDATED`. |
| Mecanismo TTS indisponível | NÃO EXECUTADO | O aparelho possuía TTS. O estado de indisponibilidade tem cobertura Compose, mas deve ser testado em aparelho sem voz pronta. |
| Restrição prolongada de bateria do fabricante | GATE DE LIBERAÇÃO — NÃO EXECUTADO | Tela apagada e dois reboots passaram, mas uma execução prolongada sob políticas agressivas de bateria exige janela dedicada. |
| Android 8–11 físico | GATE DE LIBERAÇÃO — NÃO EXECUTADO | A política API 26–30 é coberta por teste e não pede `READ_PHONE_STATE`; ainda requer aparelho físico antigo para evidência de fabricante/plataforma. |

## Verificação automatizada

O gate limpo executado com o Moto conectado foi:

```powershell
.\gradlew.bat -p buildSrc test --console=plain
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug connectedDebugAndroidTest --console=plain
.\scripts\tests\verify-release-apk-test.ps1
```

Resultado: `BUILD SUCCESSFUL`, 67 testes JVM em 12 suítes com zero falhas, lint aprovado, APK debug gerado e 13 testes instrumentados concluídos no Moto G54 5G. O verificador do APK passou nos casos válido, divergência, sintaxe e faixa de componentes.

A cobertura inclui máquina de estados/debounce, observação agregada por transporte, política e sonda celular ativa, timeout, execução única, coalescimento, cancelamento durante registro, compatibilidade API 26–30/API 31+, receiver de modo avião, preferências, mensagens em português, fila TTS, coordenação do monitoramento, notificação, ViewModel, telas Compose e decisão de restart.

## Estado final do aparelho

Ao encerrar a execução:

- modo avião desligado;
- Wi‑Fi habilitado;
- dados móveis habilitados e bloco exibindo a operadora ativa;
- ajustes globais `airplane_mode_on/wifi_on/mobile_data` em `0/1/1`;
- MobiSentinel instalado e **Monitoramento inativo**;
- nenhum serviço ou notificação do MobiSentinel ativo.

## Checklist restante antes de produção

1. Confirmar audivelmente narração celular, silêncio quando desabilitada e comportamento sem mecanismo TTS.
2. Validar Wi‑Fi sem internet e portal cativo em rede controlada.
3. Executar teste prolongado sob políticas de bateria dos fabricantes-alvo.
4. Executar a compatibilidade em pelo menos um aparelho Android 8–11/API 26–30.
