# Política de privacidade

Última atualização: 17 de julho de 2026.

MobiSentinel diagnostica conectividade no próprio aparelho. O desenvolvedor não coleta, recebe, armazena, vende ou compartilha dados do usuário por meio do aplicativo.

## Dados e serviços ausentes

O aplicativo não possui:

- conta ou login;
- backend próprio;
- analytics, telemetria ou relatórios de crash remotos;
- anúncios ou rastreadores;
- histórico de conectividade;
- coleta de localização, contatos, telefone, SMS, microfone ou arquivos;
- leitura de número de telefone, IMSI, ICCID ou outros identificadores do SIM/aparelho;
- pings ou sondas para servidores externos.

O manifesto não declara a permissão `INTERNET`.

## Processamento no aparelho

As capacidades de rede fornecidas pelo Android são processadas localmente para classificar Wi‑Fi e dados móveis de forma independente. O aplicativo não registra endereço IP, SSID, BSSID, MAC, operadora ou identificadores da rede.

Preferences DataStore mantém somente:

- monitoramento ativado ou desativado;
- narração ativada ou desativada por transporte;
- intervalos configurados de confirmação de perda e recuperação.

Essas preferências permanecem no armazenamento privado do aplicativo e são removidas quando os dados do app são apagados ou ele é desinstalado.

## Narração

Quando a narração está habilitada, o texto do estado confirmado é entregue ao mecanismo Text-to-Speech instalado no Android. O MobiSentinel não envia esse texto ao desenvolvedor. O tratamento feito pelo mecanismo de voz escolhido está sujeito à política do fornecedor desse mecanismo.

## Distribuição

O APK é distribuído pelo GitHub. Downloads e acesso ao site podem ser registrados pelo GitHub segundo os termos e a política de privacidade dessa plataforma; esses registros não são produzidos nem recebidos pelo aplicativo MobiSentinel.

Mudanças futuras que adicionem backend, analytics, anúncios, histórico ou sondas externas exigirão atualização desta política antes da distribuição.
