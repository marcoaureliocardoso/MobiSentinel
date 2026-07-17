# Correções pós-revisão da validação celular ativa

Data: 17 de julho de 2026

## Objetivo

Corrigir os três problemas encontrados na revisão da PR #5 e comprovar, no Moto G54 5G, que Wi-Fi e dados móveis são diagnosticados independentemente. O aplicativo continua sem solicitar permissões de telefone, localização ou internet.

## Decisões

### Compatibilidade sem permissão de telefone

O `TelephonyCallback.UserMobileDataStateListener` permanece somente no Android 12/API 31 ou superior. Android 8 a 11/API 26–30 usam a sonda inicial, a repetição periódica, os callbacks passivos de conectividade e o evento de modo avião.

O aplicativo não declara nem solicita `READ_PHONE_STATE`. Uma falha ao registrar um gatilho complementar não pode cancelar os callbacks de Wi-Fi, o observador celular ou a sonda periódica.

### Modo avião como gatilho do sistema

O receptor dinâmico de `Intent.ACTION_AIRPLANE_MODE_CHANGED` será registrado com `ContextCompat.RECEIVER_EXPORTED`, necessário para receber todos os broadcasts relevantes do sistema. O receptor continuará aceitando apenas a action de modo avião.

O broadcast nunca define um transporte como desconectado. Ele recompõe o Wi-Fi a partir das redes atuais e dispara uma sonda celular; somente esses resultados entram no debounce e podem gerar mudança visual, notificação ou narração.

### Cancelamento atômico da sonda

O ciclo de registro da solicitação celular terá um estado sincronizado que distingue registro em andamento, registro concluído, liberação solicitada e liberação concluída.

Se a coroutine for cancelada antes ou durante `requestNetwork`, a liberação ficará pendente e será executada assim que o registro terminar. Se o registro falhar, o resultado continuará sendo `Failure`. Nenhum caminho pode liberar duas vezes ou deixar uma solicitação ativa após cancelamento, timeout, sucesso ou indisponibilidade.

## Componentes afetados

- `AndroidNetworkObserver.kt`: política por versão, flag do receptor e remoção do listener legado protegido.
- `AndroidCellularValidationProbe.kt`: coordenação atômica entre registro e cancelamento.
- testes JVM de política de plataforma e da corrida de cancelamento.
- documentação de arquitetura, README e matriz manual com evidências físicas atualizadas.

## Estratégia TDD

1. Criar teste falho que exige listener de dados móveis somente em API 31+.
2. Criar teste falho que exige `RECEIVER_EXPORTED` para modo avião.
3. Criar teste falho no qual a coroutine é cancelada dentro de `request`, antes de o fake considerar o registro concluído; a expectativa é uma única liberação posterior ao registro.
4. Implementar cada correção separadamente e executar seu teste específico antes de avançar.
5. Executar todos os testes JVM, lint, build, verificação do APK e testes instrumentados.

## Validação no Moto G54 5G

Com o aparelho conectado por USB e sem registrar identificadores pessoais:

1. instalação limpa, abertura, permissão de notificação e serviço em primeiro plano;
2. estado inicial com Wi-Fi e dados móveis validados;
3. perda e recuperação de dados móveis com Wi-Fi ligado;
4. perda e recuperação de dados móveis com Wi-Fi desligado;
5. modo avião pelo botão real dos Controles Rápidos, sem inferência direta;
6. Wi-Fi religado e validado enquanto o modo avião permanece ativo;
7. estado celular seguindo a sonda durante o modo avião;
8. três ciclos periódicos de 60 segundos sem mudança ou narração duplicada;
9. encerramento durante sonda sem callback, estado ou fala tardia;
10. funcionamento com tela bloqueada;
11. reinício com monitoramento habilitado;
12. ação `Parar`, seguida de reinício sem retomada indevida;
13. inspeção da notificação persistente e do buffer de crashes.

O estado inicial do aparelho será registrado e restaurado ao final. Portal cativo/rede sem internet, ausência real de mecanismo TTS, políticas de bateria prolongadas e execução física em Android 8–11 serão marcados como não executados se o ambiente não permitir reprodução segura e confiável. A lógica correspondente continuará coberta por testes automatizados quando possível.

## Critérios de aceite

- Nenhuma permissão de telefone é adicionada.
- Android 8–11 não tenta registrar `LISTEN_USER_MOBILE_DATA_STATE`.
- O evento real de modo avião dispara a reavaliação no Moto G54 5G.
- Desligar dados móveis com Wi-Fi ligado confirma somente a perda celular.
- Toda solicitação celular é liberada exatamente uma vez após qualquer término ou cancelamento.
- Suites automatizadas e cenários físicos executáveis terminam sem crash.
- Estados de rede do aparelho são restaurados ao final do teste.
