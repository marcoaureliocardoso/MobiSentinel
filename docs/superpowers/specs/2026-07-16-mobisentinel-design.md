# MobiSentinel — Especificação de design do MVP

Data: 16 de julho de 2026

## Objetivo

Criar um aplicativo Android que monitore continuamente e de forma independente a conectividade por Wi‑Fi e dados móveis. Após confirmar uma mudança de estado, o aplicativo atualiza sua interface e pode narrar o evento por meio do mecanismo de síntese de voz do Android.

O aplicativo funciona inteiramente no aparelho. O MVP não possui conta, servidor, telemetria nem histórico de eventos.

## Plataforma e tecnologia

- Android nativo.
- Kotlin.
- Jetpack Compose para a interface.
- `ConnectivityManager` e `NetworkCallback` para observação das redes.
- `TextToSpeech` para narração.
- Preferences DataStore para persistência das configurações.
- Serviço em primeiro plano para monitoramento contínuo.

Flutter não será usado no MVP. A escolha reduz a complexidade e oferece acesso direto às APIs de ciclo de vida, conectividade, inicialização do aparelho, notificações e voz. Uma eventual versão para iOS será tratada como um projeto futuro.

## Arquitetura

O aplicativo será dividido em unidades com responsabilidades específicas:

1. **Interface Compose:** apresenta os estados e permite alterar configurações.
2. **Repositório de preferências:** persiste configurações e as disponibiliza para a interface e o serviço.
3. **Serviço de monitoramento:** mantém o monitoramento ativo, publica a notificação persistente e coordena os componentes internos.
4. **Observador de redes:** converte callbacks do Android em estados brutos separados para Wi‑Fi e dados móveis.
5. **Máquina de estados:** confirma transições após os intervalos configurados e elimina oscilações.
6. **Narrador:** transforma transições confirmadas em mensagens e as reproduz por Text-to-Speech quando habilitadas.
7. **Receptor de inicialização:** solicita a retomada do serviço após a reinicialização do aparelho, desde que o usuário já tenha ativado o monitoramento.

As dependências apontam das camadas Android para interfaces pequenas do domínio. O observador de redes, o relógio/temporizador, as preferências e o narrador devem poder ser substituídos por implementações falsas nos testes.

## Estados de conectividade

Cada transporte, Wi‑Fi ou dados móveis, possui um estado independente:

- **Desconectado:** nenhuma rede daquele transporte está disponível.
- **Conectado sem internet:** há uma rede do transporte, mas o Android não a considera validada para acesso à internet.
- **Conectado com internet:** há uma rede do transporte com capacidade de internet validada pelo Android.

O MVP usa a validação fornecida pelo sistema operacional. Não realiza pings periódicos nem depende de servidores externos.

Uma rede com portal de autenticação permanece no estado "conectado sem internet" até ser validada pelo Android. VPN não é apresentada como um terceiro transporte.

## Fluxo de eventos

1. O Android informa uma mudança de disponibilidade ou capacidade.
2. O observador produz um estado provisório para o transporte afetado.
3. A máquina de estados inicia o temporizador correspondente.
4. Se o estado mudar novamente antes do prazo, o temporizador anterior é cancelado.
5. Se o estado permanecer estável, ele se torna o estado confirmado.
6. A tela e a notificação são atualizadas.
7. Se não for o estado inicial e a narração daquele transporte estiver habilitada, uma mensagem é enviada ao narrador.

Os valores padrão são:

- 5 segundos para confirmar perda de conectividade.
- 2 segundos para confirmar recuperação.

Ambos podem ser personalizados entre 0 e 60 segundos.

O primeiro estado observado após a inicialização do serviço estabelece a linha de base e não é narrado.

## Mensagens narradas

- “Wi‑Fi desconectado.”
- “Wi‑Fi conectado, mas sem acesso à internet.”
- “Acesso à internet por Wi‑Fi restabelecido.”
- “Dados móveis desconectados.”
- “Dados móveis conectados, mas sem acesso à internet.”
- “Acesso à internet por dados móveis restabelecido.”

Eventos simultâneos de transportes diferentes são narrados sequencialmente. Um novo evento do mesmo transporte substitui uma mensagem daquele transporte que ainda não começou, para evitar anúncios contraditórios.

## Interface

### Tela principal

- Indicador textual de monitoramento ativo ou inativo.
- Cartão de Wi‑Fi com ícone, cor e estado textual.
- Cartão de dados móveis com ícone, cor e estado textual.
- Acesso à tela de configurações.
- Ação para ativar ou retomar o monitoramento quando necessário.

Cor nunca será o único indicador de estado. Controles e estados terão rótulos semânticos adequados para leitores de tela.

### Tela de configurações

- Alternância “Narrar eventos de Wi‑Fi”, ativada por padrão.
- Alternância “Narrar eventos de dados móveis”, ativada por padrão.
- Tempo para confirmar perda, com padrão de 5 segundos.
- Tempo para confirmar retorno, com padrão de 2 segundos.
- Ação “Testar narração”.
- Atalho para as configurações de síntese de voz do Android quando não houver voz adequada instalada.

### Primeira execução

O aplicativo explica o monitoramento contínuo e a notificação persistente, solicita a permissão de notificações nas versões do Android em que ela se aplica e inicia o serviço somente após uma ação explícita do usuário. A partir dessa ativação, o monitoramento pode ser retomado após reinicializações.

## Serviço e notificação

O monitoramento contínuo usa um serviço em primeiro plano com notificação persistente. A notificação apresenta “MobiSentinel monitorando conexões” e um resumo compacto dos estados de Wi‑Fi e dados móveis. Ao tocá-la, o usuário volta à tela principal.

O serviço deve tolerar recriação pelo Android, registrar callbacks uma única vez por instância, liberar callbacks e Text-to-Speech ao encerrar e reconstruir o estado a partir das preferências persistidas.

## Tratamento de falhas

- Falha ou atraso na inicialização do Text-to-Speech não impede a atualização dos estados.
- Uma mensagem que não pôde ser narrada não é reproduzida posteriormente, pois pode estar desatualizada.
- A interface apresenta orientação quando não houver mecanismo de voz adequado.
- Modo avião leva os dois transportes ao estado desconectado após o intervalo de confirmação.
- Se o serviço for encerrado pelo sistema ou pelo fabricante, o aplicativo tenta retomá-lo dentro das possibilidades oferecidas pelo Android e indica na interface quando o monitoramento não está ativo.
- Exceções em callbacks ou no narrador são isoladas e não encerram o processo de monitoramento.

## Limitações conhecidas

- Sem permissões privilegiadas, a ausência de rede móvel não pode ser atribuída com total confiabilidade a dados desativados, falta de sinal ou indisponibilidade da operadora. Todas essas situações são apresentadas como “Dados móveis desconectados”.
- Fabricantes podem aplicar políticas próprias de economia de bateria. A notificação persistente e a recriação do serviço reduzem, mas não eliminam, esse risco.
- O emulador não reproduz todas as condições reais de uma rede celular. A validação final de dados móveis exige um aparelho físico com chip.
- O MVP não monitora qualidade, velocidade, latência, consumo de dados ou intensidade de sinal.

## Privacidade e permissões

O aplicativo não envia dados para servidores. Ele solicita apenas permissões necessárias ao estado de rede, serviço em primeiro plano, inicialização e notificações. Não solicita localização, acesso a chamadas, contatos, microfone ou arquivos.

## Estratégia de testes

### Testes unitários

- Transições entre os três estados de cada transporte.
- Confirmação de perda e retorno com os valores padrão e personalizados.
- Cancelamento e substituição de transições durante oscilações.
- Ausência de narração no estado inicial.
- Seleção independente de narração para Wi‑Fi e dados móveis.
- Ordem e substituição das mensagens na fila do narrador.
- Recuperação das preferências persistidas.

### Testes de integração e interface

- Renderização dos cartões para cada estado.
- Alteração e persistência das configurações.
- Ativação inicial e abertura da tela pela notificação.
- Recriação do serviço sem duplicação de callbacks.
- Comportamento após reinicialização do emulador.

### Verificação manual

- Perda e retorno do Wi‑Fi.
- Wi‑Fi conectado sem internet ou com portal de autenticação.
- Modo avião.
- Reinicialização do aparelho.
- Falha e recuperação do Text-to-Speech.
- Perda e retorno de dados móveis em aparelho físico com chip.

## Critérios de aceite do MVP

1. O usuário ativa o monitoramento uma vez e ele permanece ativo com notificação persistente.
2. O aplicativo apresenta separadamente o estado confirmado de Wi‑Fi e dados móveis.
3. Quedas são confirmadas após 5 segundos e retornos após 2 segundos por padrão.
4. O usuário pode personalizar ambos os tempos entre 0 e 60 segundos.
5. O usuário pode habilitar ou desabilitar a narração de cada transporte independentemente.
6. O estado inicial não é narrado.
7. Estados desconectado, conectado sem internet e conectado com internet são distinguidos.
8. O serviço pode retomar o monitoramento após a reinicialização do aparelho.
9. O aplicativo não realiza pings periódicos nem transmite dados externos.
10. Os testes automatizados do domínio e da interface passam, e o fluxo de dados móveis é validado em aparelho físico antes da distribuição.

## Fora do escopo

- iOS e Flutter.
- Histórico e exportação de eventos.
- Contas, sincronização ou backend.
- Mensagens personalizadas pelo usuário.
- Seleção de voz, velocidade ou volume dentro do aplicativo.
- Monitoramento de VPN como conexão independente.
- Métricas de velocidade, latência, sinal ou consumo.
- Publicação na Google Play nesta primeira etapa.
