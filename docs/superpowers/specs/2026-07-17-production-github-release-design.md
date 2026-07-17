# MobiSentinel — Release de produção assinada no GitHub

Data: 17 de julho de 2026

Status: aprovado conceitualmente; aguardando revisão deste documento antes da implementação

## Contexto

O MobiSentinel 0.1.1 está publicado como pré-release com um APK de depuração assinado pela chave debug do Android. A automação atual verifica versão, pacote e SHA-256, mas declara corretamente que o artefato não é produtivo. Os fluxos essenciais de Wi-Fi e dados móveis foram validados no Moto G54 5G, incluindo observação individual dos transportes, modo avião, tela apagada, reinício e parada explícita.

Esta mudança cria a primeira distribuição de produção pelo GitHub. Google Play fica para uma etapa futura, mas a identidade do aplicativo e a chave de assinatura devem ser escolhidas agora de modo compatível com essa migração.

## Decisões confirmadas

- O identificador definitivo será `br.com.marcocardoso.mobisentinel`.
- O proprietário controla o domínio `marcocardoso.com.br`.
- A primeira versão estável será `1.0.0`.
- O canal inicial de produção será GitHub Releases.
- O APK será assinado pelo GitHub Actions.
- A chave e as credenciais de recuperação terão backup na pasta pessoal `MobiSentinel` do Google Drive.
- O backup não receberá uma segunda camada de criptografia. A chave continuará protegida pela senha obrigatória do próprio keystore, mas o arquivo de recuperação com as credenciais ficará na mesma pasta por decisão explícita do proprietário.
- A narração ligada e o silêncio com a narração desligada foram confirmados manualmente pelo proprietário, inclusive no cenário celular que permanecia aberto.
- Quatro cenários serão adiados e tratados como riscos aceitos, sem serem descritos como aprovados: TTS indisponível, Wi-Fi sem internet/portal cativo, execução prolongada sob restrições agressivas de bateria e aparelho físico Android 8–11.

## Objetivos

1. Migrar a identidade Android sem preservar compatibilidade de atualização com o APK debug anterior.
2. Criar uma chave definitiva de assinatura e uma cópia recuperável sob custódia do proprietário.
3. Produzir no GitHub Actions um APK `release` assinado, verificável e não depurável.
4. Impedir que uma release seja promovida a estável se build, assinatura, certificado, pacote, versão ou checksum não forem comprovados.
5. Atualizar exaustivamente a documentação pública e operacional para refletir o novo canal produtivo e os riscos aceitos.
6. Preservar uma rota futura para o Google Play sem trocar pacote ou certificado.

## Fora do escopo

- Publicação no Google Play ou geração de AAB nesta etapa.
- Implementação dos quatro cenários de validação adiados.
- Backend, telemetria, analytics, conta de usuário ou coleta de dados.
- Escolha de uma licença open source. Nenhuma licença será inferida em nome do proprietário.
- Rotação da chave sem incidente. A chave criada será tratada como definitiva.

## Identidade Android e migração

`applicationId`, `namespace`, declarações Kotlin e diretórios de código/teste passarão de `com.mobisentinel.app` para `br.com.marcocardoso.mobisentinel`. Manifesto, comandos ADB, scripts, documentação e verificadores usarão a nova identidade.

Essa mudança cria outro aplicativo aos olhos do Android. A versão debug anterior não receberá atualização direta, suas preferências não serão migradas e ela poderá coexistir com a versão nova. A documentação orientará desinstalar `com.mobisentinel.app` depois de confirmar que não há dados relevantes a preservar. Como o app armazena somente ativação e preferências locais, não haverá ferramenta de migração.

Antes da primeira publicação, os testes devem provar que não resta nenhuma referência funcional ao identificador antigo. Referências históricas em documentos de versões anteriores podem permanecer quando claramente contextualizadas.

## Chave de assinatura e custódia

Será gerado um keystore PKCS#12 com uma chave RSA de 4096 bits, validade longa o bastante para todo o ciclo esperado do produto e alias estável `mobisentinel`. O sujeito do certificado conterá apenas identidade pública do produto/proprietário, sem endereço pessoal.

Os seguintes valores serão criados com aleatoriedade criptográfica e armazenados no ambiente GitHub `production`. Se a implementação PKCS#12 exigir a mesma senha para o contêiner e a chave, os dois secrets de senha conterão intencionalmente o mesmo valor:

- `ANDROID_SIGNING_KEY_BASE64`: keystore codificado em Base64;
- `ANDROID_SIGNING_STORE_PASSWORD`: senha do keystore;
- `ANDROID_SIGNING_KEY_ALIAS`: alias da chave;
- `ANDROID_SIGNING_KEY_PASSWORD`: senha da chave.

O workflow reconstruirá o keystore somente no diretório temporário do runner. Nenhum segredo poderá ser impresso, anexado como artifact, incluído em cache ou gravado no repositório. O arquivo temporário será removido mesmo após falha.

A impressão digital SHA-256 do certificado é pública e será versionada no repositório. O verificador comparará o certificado do APK com essa impressão digital fixa. Alterar a impressão digital exigirá revisão explícita e será tratado como evento de segurança.

### Backup no Google Drive

A pasta pessoal `MobiSentinel` conterá uma subpasta claramente nomeada para a chave de produção, com:

- o keystore PKCS#12 original;
- um arquivo de recuperação contendo alias e senhas;
- a impressão digital SHA-256 do certificado;
- instruções curtas para restaurar os GitHub Secrets.

O usuário determinou que não haverá criptografia adicional nem separação entre o keystore e as credenciais. A documentação registrará que acesso indevido a essa pasta permite assinar versões falsas do aplicativo. O upload será verificado por leitura de metadados/listagem no Drive. Nenhum link será tornado público e o compartilhamento existente da pasta não será ampliado.

## Configuração Gradle

O build `release` consumirá exclusivamente variáveis de ambiente. Quando a tarefa de assinatura for solicitada e qualquer variável estiver ausente, vazia ou inválida, o build falhará com uma mensagem que identifica o nome da configuração ausente sem revelar seu valor.

O código não terá fallback para a chave debug. O APK produtivo será não depurável. Minificação e redução de recursos não serão introduzidas junto com a mudança de identidade/assinatura, evitando adicionar um segundo eixo de risco à primeira release; poderão ser ativadas depois com testes próprios.

O build local continuará permitindo testes e builds debug sem segredos. A documentação mostrará como apontar para um keystore local sem copiar credenciais para arquivos versionados.

## Automação de release

Release Please continuará criando a PR de versão e a tag somente após merge humano. Uma mudança incompatível na identidade do pacote conduzirá a versão para `1.0.0`.

O fluxo será fail-safe:

1. Release Please cria a tag e mantém a GitHub Release como pré-release durante a fabricação do artefato.
2. O job de build usa o ambiente `production`, reconstrói o keystore e executa os gates.
3. O APK é assinado e verificado.
4. Somente `MobiSentinel-X.Y.Z.apk` e `MobiSentinel-X.Y.Z.apk.sha256` são anexados.
5. Um job final confirma novamente os assets e promove a release a estável.
6. Qualquer falha mantém a release como pré-release e impede a apresentação do artefato como produção.

Os jobs usarão permissões mínimas e Actions fixadas por SHA. Segredos não serão disponibilizados para PRs nem para código não integrado. O ambiente `production` limitará a chave ao workflow produtivo.

## Gates automatizados

Antes de produzir o APK:

- testes da lógica de versionamento em `buildSrc`;
- testes JVM debug e release aplicáveis;
- lint debug e release;
- build debug para preservar o fluxo de desenvolvimento;
- build `release` assinado;
- testes do verificador de artefato.

O verificador produtivo deve falhar nos casos:

- pacote diferente de `br.com.marcocardoso.mobisentinel`;
- `versionName` ou `versionCode` divergente da tag;
- APK ausente, ilegível, não assinado ou assinado por outro certificado;
- APK depurável;
- componente SemVer inválido;
- checksum gerado para arquivo diferente.

O caminho positivo deve comprovar:

- assinatura válida pelos esquemas suportados pelo Android alvo;
- fingerprint SHA-256 igual à registrada;
- pacote e versão corretos;
- ausência da flag de depuração;
- SHA-256 reproduzido a partir do arquivo publicado.

## Validação no Moto G54 5G

O candidato assinado será instalado como aplicativo novo. A validação mínima da própria variante `release` inclui:

- instalação e abertura;
- ativação, permissão, foreground service e notificação;
- perda/recuperação individual de Wi-Fi;
- perda/recuperação individual de dados móveis com Wi-Fi ligado e desligado;
- narração ligada e silêncio quando desligada;
- modo avião sem inferência global imediata;
- tela apagada/segundo plano;
- reinício ativado, parada explícita e reinício parado;
- ausência de crash e de callbacks/sondas temporárias órfãs.

A aprovação anterior do build debug continua como evidência de desenvolvimento, mas não substitui o smoke test do APK release assinado.

## Documentação

A revisão deve localizar referências obsoletas por busca global e atualizar, no mínimo:

- `README.md`: instalação produtiva, diferença debug/release, nova identidade, verificação SHA-256/certificado, riscos aceitos e rota futura para Play;
- `CHANGELOG.md`: mudança incompatível da identidade e primeira release estável, gerada/revisada pela PR de release;
- `docs/testing/manual-test-matrix.md`: narração confirmada, quatro riscos adiados e evidência do APK assinado;
- `SECURITY.md`: reporte de vulnerabilidades, tratamento da chave, fingerprint oficial e resposta a comprometimento;
- `PRIVACY.md`: processamento local, ausência de conta/backend/analytics e dados não coletados/compartilhados;
- runbook de produção: criação/restauração dos secrets, build, verificação, rollback e promoção;
- especificações e planos históricos: manter o registro histórico, acrescentando notas de supersessão quando afirmações antigas sobre “somente debug” deixarem de representar o estado atual;
- scripts e mensagens de release: remover o aviso de que todo APK é debug e substituí-lo pela identificação inequívoca do tipo de artefato.

Documentos não devem afirmar que os quatro riscos aceitos passaram. Também não devem prometer aprovação futura do Google Play.

## Privacidade e segurança

O aplicativo continua sem `INTERNET`, backend, analytics, conta ou histórico de conectividade. A observação usa capacidades fornecidas pelo Android e mantém apenas preferências locais. A política de privacidade deve refletir exatamente essa arquitetura e ser adequada para futura publicação pública, sem afirmar certificações inexistentes.

O risco de backup não separado no Google Drive foi aceito explicitamente. Se a pasta ou conta for comprometida, a resposta prevista é interromper publicações, preservar evidências e avaliar migração de identidade; APKs instalados fora de um serviço de rotação de chave não aceitam silenciosamente uma nova assinatura.

## Riscos aceitos pós-lançamento

| Cenário | Estado na 1.0.0 | Mitigação atual | Acompanhamento |
| --- | --- | --- | --- |
| Android sem mecanismo TTS pronto | Não validado fisicamente | Cobertura automatizada da interface/erro e app sem dependência de backend | Teste dedicado pós-lançamento |
| Wi-Fi sem internet/portal cativo | Não validado em rede controlada | Estado deriva de `NET_CAPABILITY_VALIDATED` e possui testes de máquina de estados | Laboratório de rede pós-lançamento |
| Restrição prolongada de bateria | Janela longa não executada | Tela apagada, segundo plano e reinício passaram no Moto G54 | Soak test em fabricantes-alvo |
| Android 8–11 físico | Não executado | Política API 26–30 coberta por testes e sem `READ_PHONE_STATE` | Matriz física legada |

Esses itens serão visíveis no README e na matriz de testes. A decisão permite a distribuição 1.0.0 no GitHub, mas não transforma ausência de evidência em aprovação.

## Falhas, reexecução e rollback

- Segredo ausente ou inválido: falhar antes do build assinado.
- Fingerprint divergente: falhar antes do upload e tratar como possível incidente.
- Falha após criação da tag: manter GitHub Release como pré-release; não apagar automaticamente tag ou release.
- Upload parcial: substituir somente os nomes de assets esperados em reexecução idempotente.
- Falha após promoção: ocultar a versão como pré-release novamente, sem apagar evidências, e publicar correção com `versionCode` superior.
- APK produtivo incorreto: nunca reutilizar a mesma versão para conteúdo diferente depois que usuários o instalaram; lançar patch.

## Preparação para Google Play

No futuro, o app deverá usar o mesmo pacote e preservar o mesmo certificado de assinatura para permitir continuidade entre instalações do GitHub e do Play. Ao habilitar Play App Signing, a configuração deve importar a chave de assinatura existente ou seguir um processo oficialmente compatível que mantenha o certificado entregue aos dispositivos. AAB, upload key, ficha da loja, Data Safety, política hospedada e declaração de `specialUse` serão especificados separadamente.

## Critérios de aceite

1. Nenhum arquivo secreto aparece no Git, logs, cache ou artifacts.
2. Backup e recuperação são confirmados no Google Drive pessoal.
3. A master continua passando todos os gates existentes.
4. O APK release assinado passa testes de pacote, versão, debuggable, assinatura, fingerprint e checksum.
5. O APK release passa o smoke test definido no Moto G54 5G.
6. O workflow mantém falhas como pré-release e só promove após todos os gates.
7. A documentação não contém instruções produtivas contraditórias nem apresenta riscos adiados como aprovados.
8. A PR de implementação é revisada e aprovada antes do merge.
9. A PR de release 1.0.0 é revisada e aprovada separadamente antes do merge.
10. A GitHub Release 1.0.0 contém somente `MobiSentinel-1.0.0.apk` e `MobiSentinel-1.0.0.apk.sha256` e é estável após verificação final.
