# Design de alertas por vibração e horário silencioso

## Contexto

O MobiSentinel já confirma separadamente transições de Wi-Fi e dados móveis, atualiza a interface e a notificação persistente e, quando configurado, narra eventos em português. Esta evolução adiciona uma alternativa háptica opcional e uma faixa diária de "não perturbe" controlada pelo próprio aplicativo.

O diagnóstico, os estados de conectividade e os atrasos de confirmação existentes não mudam. Voz e vibração continuam sendo efeitos de uma transição já confirmada.

## Objetivos

- oferecer padrões de vibração distintos para perda e restabelecimento de internet;
- permitir ativação independente da vibração para Wi-Fi e dados móveis;
- manter todas as opções de vibração desativadas por padrão;
- permitir uma faixa silenciosa diária única, aplicável a voz e vibração;
- manter monitoramento, interface e notificação funcionando durante a faixa silenciosa;
- funcionar do Android 8/API 26 em diante sem acesso invasivo à política de "Não perturbe" do sistema;
- disponibilizar um teste manual dos dois padrões de vibração.

## Fora de escopo

- alterar a detecção ou a confirmação de conectividade;
- criar histórico ou reproduzir alertas silenciados posteriormente;
- configurar dias da semana ou múltiplas faixas por dia;
- modificar automaticamente o "Não perturbe" do Android;
- solicitar acesso à política de notificações do sistema;
- publicar notificações separadas para cada transição;
- garantir vibração quando o sistema operacional ou o fabricante a bloquear.

## Semântica das transições

Para a vibração, os três estados existentes são agrupados em duas classes:

- disponível: `CONNECTED`;
- sem internet: `DISCONNECTED` ou `CONNECTED_NO_INTERNET`.

Os padrões são:

- disponível -> sem internet: duas vibrações de 120 ms, separadas por 120 ms;
- sem internet -> disponível: uma vibração de 350 ms;
- mudança entre os dois estados sem internet: nenhuma vibração.

As durações usam a amplitude padrão do dispositivo. Uma vibração nunca se repete automaticamente.

A narração mantém as mensagens atuais para cada estado confirmado. Durante a faixa silenciosa, toda narração automática é suprimida, inclusive mudanças entre os dois estados sem internet.

## Preferências persistidas

`MonitoringSettings` será ampliado com:

- `vibrateWifi: Boolean = false`;
- `vibrateCellular: Boolean = false`;
- `quietHoursEnabled: Boolean = false`;
- `quietStartMinuteOfDay: Int = 22 * 60`;
- `quietEndMinuteOfDay: Int = 7 * 60`.

Minutos do dia aceitam valores de `0` a `1439`. Início e fim iguais são inválidos. A validação deve existir no modelo e nos métodos de escrita do repositório. Ao ler dados persistidos, se qualquer valor estiver fora do intervalo ou se início e fim forem iguais, o par completo deve ser normalizado para os defaults seguros `22:00–07:00`.

Desativar a faixa silenciosa preserva os horários escolhidos. Reativá-la restaura a mesma faixa.

Helpers do modelo expõem, sem dependência Android:

- se a narração está habilitada para um transporte;
- se a vibração está habilitada para um transporte;
- se uma hora local está dentro da faixa silenciosa.

## Regra de horário silencioso

A faixa é diária, usa o horário e o fuso atuais do aparelho e é avaliada no instante em que a transição é confirmada.

- com a função desativada, nenhum horário é silencioso;
- para `início < fim`, a faixa é `hora >= início && hora < fim`;
- para `início > fim`, a faixa atravessa a meia-noite e é `hora >= início || hora < fim`;
- o início é inclusivo e o fim é exclusivo;
- alertas suprimidos são descartados, não enfileirados.

Mudanças manuais de hora ou fuso passam a valer na próxima transição, sem agendamento ou alarme próprio. Por isso, a feature não requer `AlarmManager` nem trabalho periódico adicional.

## Arquitetura

### Política de alertas

Uma política Kotlin pura recebe:

- `ConfirmedTransition`;
- snapshot de `MonitoringSettings`;
- minuto local atual.

Ela retorna uma decisão independente para cada efeito:

- `narrate: Boolean`;
- `hapticPattern: LOSS | RECOVERY | null`.

Se a faixa silenciosa estiver ativa, a decisão retorna voz desligada e nenhum padrão háptico. Fora dela, a narração segue o seletor do transporte e a vibração segue seu seletor e a semântica de cruzamento entre disponível/sem internet.

Essa política não chama APIs Android e concentra todas as regras combinatórias para testes determinísticos.

### Orquestração

`MonitoringEngine` continua recebendo a transição do `TransitionCoordinator`. Para cada transição:

1. obtém a decisão da política usando as configurações correntes e um provedor de horário local injetável;
2. envia a mensagem atual ao `SpeechController` quando `narrate` for verdadeiro;
3. envia o padrão ao novo `HapticController` quando houver `hapticPattern`.

Falha ou indisponibilidade de um controlador não impede o outro, nem afeta a atualização do estado confirmado.

O teste manual de voz existente e o novo teste manual de vibração são ações explícitas do usuário e não passam pela política de horário silencioso.

### Controlador háptico

Uma interface pequena separa domínio e Android:

- informa disponibilidade do vibrador;
- reproduz perda;
- reproduz restabelecimento;
- executa a sequência de teste;
- encerra qualquer trabalho pendente ao fechar.

A implementação Android usa:

- `VibratorManager` no Android 12/API 31 ou superior;
- `Vibrator` no Android 8-11/API 26-30;
- `VibrationEffect` em todas as versões suportadas;
- atributos de uso de notificação apropriados para execução pelo foreground service;
- `android.permission.VIBRATE`, uma permissão normal sem diálogo de runtime.

O teste manual reproduz o padrão de perda, aguarda 800 ms após seu término e reproduz o padrão de restabelecimento. A sequência roda fora da thread principal e uma nova solicitação manual substitui a anterior.

Ausência de hardware, efeito não suportado, `SecurityException` ou falha do serviço de sistema gera no-op seguro. O monitoramento nunca deve parar por erro háptico.

## Relação com o "Não perturbe" do Android

O MobiSentinel não consulta o modo silencioso ou a política de interrupções do Android antes de solicitar a vibração. Portanto, fora da faixa silenciosa interna, o app sempre tenta executar o padrão habilitado.

Entretanto, um aplicativo comum não pode garantir bypass do "Não perturbe" do sistema. O Android documenta que `FLAG_BYPASS_INTERRUPTION_POLICY` é ignorado para apps não privilegiados. Esta versão não pedirá `ACCESS_NOTIFICATION_POLICY`, não criará canal prioritário dedicado e não classificará eventos de conectividade como alarmes.

Referências oficiais:

- <https://developer.android.com/reference/android/os/VibrationAttributes#FLAG_BYPASS_INTERRUPTION_POLICY>
- <https://developer.android.com/reference/android/os/Vibrator>

## Interface de configurações

A tela passa a ter seções claras.

### Narração

- `Narrar eventos de Wi-Fi`;
- `Narrar eventos de dados móveis`.

### Vibração

- `Vibrar em eventos de Wi-Fi`;
- `Vibrar em eventos de dados móveis`;
- botão `Testar vibração`.

O botão de teste ignora a faixa silenciosa e os dois seletores, permitindo avaliar os padrões antes de ativá-los. Em aparelho sem vibrador, o botão fica desativado e a tela explica a indisponibilidade; os seletores permanecem persistíveis para restauração ou troca de aparelho.

### Não perturbe

- seletor `Ativar horário silencioso`;
- linha `Início`, abrindo seletor de horário;
- linha `Fim`, abrindo seletor de horário;
- texto: `Durante este horário, narração e vibração são silenciadas. O monitoramento continua ativo.`

Os horários continuam visíveis quando a função está desligada. Ao tentar escolher início igual ao fim, a tela não persiste o valor e apresenta uma mensagem clara de validação.

## Fluxo de dados

1. A tela chama novos métodos do `MainViewModel`.
2. O view model delega ao `SettingsRepository`.
3. O DataStore persiste booleanos e minutos do dia.
4. O fluxo de configurações atualiza `MonitoringEngine` e a interface.
5. Uma transição confirmada consulta a política com o snapshot corrente.
6. A decisão aciona voz e vibração independentemente.

Não há banco novo, migração destrutiva ou serviço adicional. Preferências ausentes recebem os defaults definidos neste documento.

## Tratamento de concorrência e ciclo de vida

- efeitos automáticos são curtos e não bloqueiam a coleta de rede;
- o controlador háptico não mantém fila de eventos automáticos;
- uma nova vibração automática pode substituir uma ainda ativa, evitando sobreposição indefinida;
- a sequência manual tem job próprio cancelável;
- `MonitoringEngine.stop()` fecha voz e vibração;
- o estado das preferências continua sendo lido por snapshot volátil, como no fluxo atual.

## Estratégia de testes

### Testes JVM

- defaults e invariantes das novas preferências;
- seleção independente por transporte;
- faixa diurna, noturna, início inclusivo e fim exclusivo;
- função silenciosa desativada;
- rejeição de início igual ao fim;
- perda somente ao sair de `CONNECTED`;
- recuperação somente ao voltar para `CONNECTED`;
- nenhuma vibração entre `DISCONNECTED` e `CONNECTED_NO_INTERNET`;
- voz e vibração suprimidas juntas na faixa silenciosa;
- decisões independentes fora da faixa;
- falha háptica sem interromper narração ou estado;
- sequência manual cancelável e fora da política silenciosa;
- view model persistindo cada nova opção.

### Testes instrumentados

- defaults e persistência no DataStore;
- novos seletores na tela de configurações;
- seleção de início e fim;
- mensagem ao rejeitar horários iguais;
- botão de teste habilitado/desabilitado conforme disponibilidade;
- manifesto contendo `VIBRATE`;
- suíte existente completa no emulador Android 35.

Testes automatizados usam fakes para observar padrões e horários; não tratam o motor físico do emulador como evidência tátil. Um teste manual opcional em aparelho pode confirmar percepção e duração dos padrões, mas não será gate obrigatório da release.

## Documentação e release

README, política de privacidade e matriz manual devem registrar:

- nova permissão `VIBRATE` e sua finalidade;
- defaults desativados;
- efeito do horário silencioso;
- limitação de melhor esforço diante do DND do Android/fabricante;
- ausência de coleta ou transmissão de dados;
- padrões e teste manual.

A mudança é uma feature compatível e deve gerar incremento SemVer minor por Conventional Commit.
