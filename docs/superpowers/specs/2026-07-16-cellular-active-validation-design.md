# Validação celular ativa e independente

## Contexto

A versão 0.1.0 observa Wi-Fi e rede móvel por callbacks passivos do
`ConnectivityManager`. Essa estratégia funciona para Wi-Fi, mas não garante que
uma rede celular permaneça ativa quando o Wi-Fi é a rede preferida. Em aparelho
físico, com Wi-Fi ligado, desligar os dados móveis não produziu uma transição
celular nem aviso narrado. O pipeline posterior ao observador — debounce,
estado, notificação e voz — funciona quando recebe uma transição celular.

A correção deve verificar a rede móvel independentemente do Wi-Fi sem manter o
rádio celular solicitado continuamente e sem depender de servidores externos.

## Objetivos

- Solicitar temporariamente uma rede celular mesmo quando o Wi-Fi estiver
  conectado.
- Usar `NET_CAPABILITY_VALIDATED` como evidência de acesso à internet nesta
  versão.
- Executar a verificação ao iniciar, a cada 60 segundos e imediatamente após
  eventos relevantes.
- Preservar os estados, o debounce e as mensagens narradas existentes.
- Tratar modo avião apenas como gatilho para verificações independentes, nunca
  como prova direta de desconexão.
- Manter a implementação substituível por uma futura sonda HTTPS ou TCP
  vinculada à rede celular.

## Fora de escopo

- Ping ICMP, requisição HTTPS ou qualquer tráfego da aplicação para servidores
  externos.
- Medição de velocidade, latência, intensidade de sinal ou consumo de dados.
- Manter uma solicitação celular durante toda a execução do serviço.
- Criar um estado ou uma mensagem narrada específica para modo avião.
- Identificar se a indisponibilidade foi causada por falta de sinal, operadora,
  SIM, política do sistema ou chave de dados móveis.

## Alternativas consideradas

### Sonda temporária periódica e orientada a eventos — escolhida

Equilibra precisão e consumo. A rede celular é solicitada apenas durante uma
verificação e o callback é liberado em todos os caminhos. Uma sonda é executada
inicialmente, a cada 60 segundos após o término da anterior e após eventos
relevantes.

### Solicitação celular contínua

Oferece callbacks mais rápidos, mas mantém o sistema tentando sustentar uma
rede celular mesmo com Wi-Fi ativo. O custo potencial de bateria, rádio e dados
não é adequado para esta etapa.

### Observação passiva com estado da chave de dados móveis

Tem baixo consumo, mas não comprova que existe uma rede celular com internet e
repete a limitação encontrada na versão 0.1.0.

## Arquitetura

### Contrato da sonda

Um contrato `CellularValidationProbe` isola a política de verificação do motor
de monitoramento. Ele produz exatamente um resultado por execução:

- `Validated`: uma rede celular apresentou `NET_CAPABILITY_VALIDATED`.
- `Unvalidated`: uma rede celular foi obtida, mas não validou dentro do prazo.
- `Unavailable`: nenhuma rede celular foi obtida dentro do prazo ou o Android
  informou indisponibilidade.
- `Failure`: a verificação não pôde ser executada por erro interno ou de API.

`Failure` representa saúde do mecanismo, não conectividade, e por isso não deve
ser convertido em estado desconectado.

### Implementação Android

A implementação Android cria um `NetworkRequest` com
`TRANSPORT_CELLULAR` e `NET_CAPABILITY_INTERNET`. Ela não solicita
`NET_CAPABILITY_VALIDATED`, pois essa capacidade é mutável; aguarda sua presença
em `onCapabilitiesChanged`.

Cada execução possui timeout próprio de 15 segundos:

1. Registrar `requestNetwork` com um callback exclusivo.
2. Registrar que uma rede foi obtida em `onAvailable`.
3. Concluir como `Validated` ao receber a capacidade validada.
4. Concluir como `Unavailable` em `onUnavailable` ou timeout sem rede.
5. Concluir como `Unvalidated` em timeout após obter uma rede não validada.
6. Concluir como `Failure` se a chamada de plataforma falhar.
7. Liberar o callback exatamente uma vez em sucesso, timeout, erro ou
   cancelamento.

A aplicação não envia pacotes próprios nesta versão. A validação é realizada
pelo Android.

### Agendamento e agrupamento

Um coordenador de sondas possui no máximo uma execução ativa:

- executa imediatamente ao iniciar o serviço;
- agenda a próxima execução periódica para 60 segundos após o término da
  execução atual;
- aceita gatilhos imediatos de eventos celulares e modo avião;
- agrupa gatilhos simultâneos;
- se um gatilho chegar durante uma execução, registra uma única repetição para
  depois que ela terminar;
- ao parar o serviço, cancela temporizador, execução e callback.

O intervalo não é configurável nesta etapa. Isso evita ampliar a interface sem
evidência de necessidade.

### Fontes de eventos

Os callbacks passivos de rede móvel deixam de produzir diretamente o estado
celular confirmado. `onAvailable`, `onCapabilitiesChanged` e `onLost` apenas
solicitam uma nova sonda. Assim, a desmontagem da rede após liberar uma sonda
temporária não é interpretada como perda confirmada.

Em Android 9 ou superior, alterações da chave de dados móveis também disparam
uma sonda imediata:

- API 31 ou superior: `TelephonyCallback.UserMobileDataStateListener`;
- API 28 a 30: `PhoneStateListener.LISTEN_USER_MOBILE_DATA_STATE`.

Android 8 e 8.1 continuam cobertos pela sonda inicial, pelo ciclo de 60
segundos, pelos callbacks de conectividade e pelo evento de modo avião.

O evento de modo avião nunca produz um estado. Ele solicita:

- reavaliação independente do Wi-Fi pelas redes e capacidades de Wi-Fi;
- nova sonda celular ativa.

A reavaliação do Wi-Fi usa a mesma trava do observador, recompõe o agregado a
partir das redes Wi-Fi e capacidades atuais e emite somente se o resultado
mudar. Ela não espera passivamente por outro callback e não consulta dados da
sonda celular.

Ativar ou desativar modo avião não presume perda nem recuperação. Se o Wi-Fi
permanecer ou voltar a ficar validado, ele continua conectado sem aviso falso.

## Mapeamento para estados existentes

O Wi-Fi mantém o comportamento atual. Para celular:

- `Validated` vira `CONNECTED`;
- `Unvalidated` vira `CONNECTED_NO_INTERNET`;
- `Unavailable` vira `DISCONNECTED`;
- `Failure` preserva o estado anterior.

Durante uma sonda periódica, tela e notificação preservam o último estado
confirmado. `Verificando` aparece somente antes do primeiro resultado. Uma
falha na primeira sonda mantém esse estado até uma execução posterior produzir
evidência de conectividade.

Cada novo candidato continua passando pelo `TransitionCoordinator` e pelos
tempos configurados pelo usuário. O primeiro resultado é linha de base e não é
narrado. Transições posteriores usam as mensagens existentes:

- “Dados móveis desconectados.”
- “Dados móveis conectados, mas sem acesso à internet.”
- “Acesso à internet por dados móveis restabelecido.”

Modo avião não possui mensagem própria. Se cada verificação confirmar perdas
reais nos dois transportes, os avisos de Wi-Fi e celular são produzidos
separadamente e seguem a fila existente.

## Permissões e privacidade

O manifesto passa a declarar `CHANGE_NETWORK_STATE`, necessário para solicitar
uma rede celular de fundo. `ACCESS_NETWORK_STATE` já existe.

Não será adicionada a permissão `INTERNET`, pois a aplicação não enviará tráfego
próprio nesta versão. Nenhum host, endereço IP, identificador da operadora ou
dado de conectividade será enviado para terceiros.

## Evolução para sondas externas

Uma futura implementação poderá usar o mesmo contrato para abrir conexões HTTPS
ou TCP vinculadas ao objeto `Network` celular. Ping ICMP para endereços como
`8.8.8.8` ou `1.1.1.1` não será a opção preferencial: ICMP pode ser bloqueado e
uma resposta não comprova DNS ou HTTPS. Uma sonda externa futura deverá definir
destinos, política de privacidade, timeout, quorum e limite de frequência em uma
especificação própria.

## Tratamento de falhas

- Exceções de registro, permissão ou plataforma são isoladas como `Failure`.
- `Failure` não altera estado e não produz narração.
- Timeout com rede obtida e não validada representa ausência de internet.
- Timeout sem rede representa indisponibilidade celular.
- Cancelar ou parar o serviço não publica um resultado tardio.
- Callbacks de uma execução concluída são ignorados.
- O callback é liberado exatamente uma vez, inclusive em condições de corrida.

## Estratégia de testes

### Testes unitários

- Mapear todos os resultados da sonda para os estados celulares corretos.
- Preservar o estado anterior em `Failure`.
- Executar sonda inicial e periódica.
- Agendar a repetição 60 segundos após o término da sonda anterior.
- Garantir no máximo uma execução ativa.
- Agrupar eventos simultâneos e executar no máximo uma repetição pendente.
- Cancelar execução e temporizador ao parar.
- Cobrir sucesso validado, rede não validada, indisponibilidade, timeout,
  exceção e cancelamento.
- Verificar liberação única do callback em todos os caminhos.
- Confirmar que eventos passivos celulares apenas disparam sondas.
- Confirmar que liberar a rede solicitada não produz falsa desconexão.
- Confirmar que modo avião apenas dispara reavaliações independentes.
- Confirmar que Wi-Fi validado permanece conectado durante modo avião.
- Reexecutar os testes existentes de debounce, mensagens e fila de voz.

Todos os comportamentos novos seguem TDD: o teste deve falhar pelo motivo
esperado antes da implementação mínima correspondente.

### Testes Android e físicos

O emulador pode comprovar ciclo de vida, modo avião e independência do Wi-Fi,
mas não aprova conectividade celular real.

O gate físico exige aparelho com SIM ou eSIM e deve verificar:

1. Wi-Fi ligado e validado, dados móveis ligados e sonda celular validada.
2. Desligar dados móveis mantendo Wi-Fi ligado; somente a verificação celular
   deve confirmar a perda e narrá-la após o debounce.
3. Religar dados móveis mantendo Wi-Fi ligado; a sonda deve confirmar a
   recuperação e narrá-la após o debounce.
4. Ativar modo avião; nenhum estado pode ser inferido do evento.
5. Religar Wi-Fi durante modo avião; Wi-Fi deve validar independentemente e não
   pode ser reportado como desconectado por causa do modo avião.
6. Confirmar o resultado celular produzido pela sonda durante modo avião.
7. Aguardar múltiplos ciclos de 60 segundos sem mudança; não pode haver avisos
   duplicados.
8. Encerrar o monitoramento durante uma sonda; não pode haver callback tardio,
   atualização ou fala.

## Critérios de aceitação

- O problema físico relatado com Wi-Fi ligado é reproduzido por teste antes da
  correção e passa depois dela.
- Wi-Fi e celular continuam independentes em tela, notificação e voz.
- O estado celular vem da sonda ativa, não da existência passiva de uma rede
  mantida pelo Android.
- Modo avião nunca determina diretamente o estado de nenhum transporte.
- A sonda respeita timeout de 15 segundos, período de 60 segundos, execução
  única e liberação idempotente.
- Não há tráfego da aplicação para servidores externos.
- Testes unitários, lint, build debug e gates físicos documentados passam antes
  de publicar uma nova versão.

## Documentação afetada

A implementação deve atualizar a especificação original e a matriz manual para
remover a afirmação de que modo avião leva automaticamente os dois transportes
ao estado desconectado. A evidência do novo gate físico deve registrar modelo do
aparelho, versão do Android, operadora, presença de SIM/eSIM e resultados das
etapas acima sem incluir identificadores pessoais.
