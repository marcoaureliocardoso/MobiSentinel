# Matriz de validação manual do MobiSentinel

Data da execução: 16 de julho de 2026

Atualização da validação celular ativa: 17 de julho de 2026

Ambiente principal: AVD `Codex_API_35`, Android/API 35, serial `emulator-5554`, build debug

ADB: `C:\Users\Marco\AppData\Local\Android\Sdk\platform-tools\adb.exe`

## Critérios

- **PASSOU:** comportamento observado e evidência registrada neste ambiente.
- **NÃO EXECUTADO:** o ambiente não permite uma validação confiável; o item permanece aberto.
- **GATE DE LIBERAÇÃO:** precisa passar em aparelho/ambiente representativo antes de produção.

## Resultados manuais

| Cenário | Resultado | Evidência e observações |
| --- | --- | --- |
| Instalação e abertura do debug | PASSOU | `installDebug` concluiu com `BUILD SUCCESSFUL`; `am start -n com.mobisentinel.app/.MainActivity` retornou `Starting: Intent`; a árvore de UI exibiu a tela principal e **Ativar monitoramento**. |
| Ativação e permissão de notificação | PASSOU | Após ativar e conceder a permissão, a tela exibiu **Monitoramento ativo**. |
| Foreground service | PASSOU | `dumpsys activity services` mostrou `MonitoringService`, `isForeground=true`, `foregroundId=1001` e tipo `0x40000000` (`specialUse`). |
| Notificação persistente | PASSOU | `dumpsys notification --noredact` mostrou ID `1001`, canal `mobisentinel_monitoring`, título **MobiSentinel monitorando conexões**, flags de foreground/ongoing/no-clear, importância baixa, sem som/vibração e ação **Parar**. |
| Perda de Wi‑Fi | PASSOU | Depois de `svc wifi disable` e do intervalo padrão, a árvore de UI mostrou `Wi-Fi: desconectado`; a notificação mostrou `Wi-Fi: desconectado`. |
| Recuperação de Wi‑Fi | PASSOU COM RESSALVA DO AVD | Depois de `svc wifi enable`, `dumpsys wifi` confirmou associação ao `AndroidWifi` e rede validada; a UI e a notificação retornaram a **com internet**. A reassociação/validação do AVD acrescentou latência além dos 2 s configurados. |
| Debounce visual de Wi‑Fi | PASSOU | A perda só apareceu após aproximadamente 5 s estáveis. A recuperação só foi confirmada depois que o Android informou a rede novamente associada e validada. |
| Narração de perda/recuperação de Wi‑Fi | NÃO EXECUTADO | O AVD headless não permite comprovar áudio. Mensagens exatas, fila, supressão por transporte e configuração de narração são cobertas por testes JVM; a escuta manual permanece aberta. |
| Narração desligada mantém atualização visual e fica silenciosa | NÃO EXECUTADO | A atualização visual e a política de supressão têm cobertura automatizada, mas silêncio audível deve ser confirmado em dispositivo interativo. |
| Rede conectada sem internet/portal cativo | NÃO EXECUTADO | O AVD não tinha uma rede controlada com portal cativo ou sem acesso. Validar que o estado permanece **Conectado sem internet** até o Android conceder `NET_CAPABILITY_VALIDATED`. |
| Modo avião | REVALIDAÇÃO NECESSÁRIA | A evidência anterior pressupunha incorretamente que o evento deveria desconectar os dois transportes. O novo comportamento exige que o broadcast não altere estado diretamente: Wi‑Fi é recomposto pelas redes atuais, pode permanecer ou voltar conectado, e celular segue somente o resultado da sonda ativa. |
| Reinício com monitoramento habilitado | PASSOU | Após `adb reboot`, `sys.boot_completed=1`; o serviço voltou por `BOOT_COMPLETED` com `isForeground=true`, ID `1001` e canal correto. |
| Ação **Parar** | PASSOU | O botão da notificação foi localizado pela árvore de acessibilidade (`android:id/action0`) e tocado. Em seguida, `dumpsys activity services com.mobisentinel.app` retornou `(nothing)` e a notificação deixou de existir. |
| Reinício após **Parar** | PASSOU | Um segundo reboot concluiu com `sys.boot_completed=1`; o dump continuou mostrando `(nothing)`, comprovando que a preferência desabilitada impede o restart. |
| Mecanismo TTS indisponível | NÃO EXECUTADO | O AVD tinha mecanismo TTS. Validar a indicação de indisponibilidade, o atalho para configurações de voz e a ausência de falha em aparelho sem voz pronta. A tela desse estado possui cobertura Compose automatizada. |
| Perda e recuperação de dados móveis físicos com Wi‑Fi ligado | GATE DE LIBERAÇÃO — NÃO EXECUTADO | Obrigatório executar em aparelho físico com SIM/eSIM, mantendo Wi‑Fi validado enquanto os dados móveis são desligados e religados. A conectividade celular emulada não é evidência de produção. |
| Comportamento sob políticas de bateria do fabricante | GATE DE LIBERAÇÃO — NÃO EXECUTADO | Validar em pelo menos os fabricantes Android-alvo, incluindo boot, tela bloqueada e execução prolongada. |

## Verificação automatizada

O gate final executa, em uma única invocação limpa:

```powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Atualização de 17 de julho de 2026: como não havia alvo listado por `adb devices`, foi executado o gate limpo sem dispositivo, `clean testDebugUnitTest lintDebug assembleDebug`. O resultado foi `BUILD SUCCESSFUL` em 40 s, com 59 de 62 tarefas executadas. Passaram 64 testes JVM em 11 suítes, com zero falhas, erros ou itens ignorados; `lintDebug` foi aprovado e `assembleDebug` gerou `app/build/outputs/apk/debug/app-debug.apk` com 12.578.225 bytes. `connectedDebugAndroidTest` permanece **NÃO EXECUTADO** nesta atualização.

A cobertura funcional automatizada inclui máquina de estados/debounce, observação agregada por transporte, política e sonda celular ativa, timeout, execução única, coalescimento, cancelamento, preferências, mensagens em português, fila TTS, coordenação do monitoramento, política de permissão da notificação, texto da notificação, ViewModel, telas Compose e decisão de restart.

## Checklist no aparelho físico

Registre antes da execução: modelo do aparelho, versão do Android, operadora, presença de SIM/eSIM e data. Não registre número de telefone, ICCID, IMSI ou outro identificador pessoal.

1. Instalar o debug ou candidato de release em um aparelho com SIM/eSIM e voz pt-BR instalada.
2. Iniciar com Wi‑Fi validado e dados móveis validados; confirmar os dois cartões e a notificação.
3. Desligar os dados móveis mantendo Wi‑Fi ligado; após a sonda e o debounce, confirmar somente a perda celular e exatamente um aviso “Dados móveis desconectados.”
4. Religar os dados móveis mantendo Wi‑Fi ligado; confirmar recuperação celular e exatamente um aviso de restabelecimento.
5. Ativar modo avião; confirmar que o evento não altera imediatamente nenhum estado e não possui fala própria.
6. Religar o Wi‑Fi mantendo modo avião ativo; confirmar que Wi‑Fi valida independentemente e não é reportado desconectado por causa do modo avião.
7. Confirmar que o cartão celular durante modo avião segue somente o resultado da sonda celular.
8. Aguardar pelo menos três ciclos inalterados de 60 segundos; não pode haver atualização nem narração duplicada.
9. Encerrar o monitoramento durante uma sonda; não pode haver atualização, callback ou fala tardia.
10. Conectar a uma rede sem internet ou portal cativo e observar o estado **Conectado sem internet**.
11. Remover/desativar temporariamente o mecanismo TTS e verificar o fluxo de indisponibilidade.
12. Reiniciar com monitoramento ligado; depois usar **Parar**, reiniciar e confirmar que ele permanece desligado.
13. Repetir os casos críticos com tela bloqueada e sob o perfil de bateria padrão do fabricante.

| Campo de evidência celular | Valor |
| --- | --- |
| Modelo do aparelho | NÃO EXECUTADO |
| Versão do Android | NÃO EXECUTADO |
| Operadora | NÃO EXECUTADO |
| SIM/eSIM presente | NÃO EXECUTADO |
| Data da execução | NÃO EXECUTADO |
| Resultado das etapas 2–9 | NÃO EXECUTADO |
