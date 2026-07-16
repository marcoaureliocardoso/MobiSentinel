# MobiSentinel — Especificação de versionamento e automação de releases

Data: 16 de julho de 2026
Status: aprovado para planejamento

## Contexto

O MVP está publicado no GitHub com `versionName` 0.1.0 e `versionCode` 1, mas ainda não possui changelog, tag, GitHub Release ou automação de integração e publicação. O aplicativo também mantém gates de validação física abertos, documentados na [matriz de testes manuais](../../testing/manual-test-matrix.md), portanto os artefatos atuais não devem ser apresentados como uma versão pronta para produção.

Esta especificação define uma fonte única de versão, a geração do changelog e um fluxo reprodutível para publicar o código-fonte e um APK de depuração explicitamente identificado como não destinado à produção.

## Objetivos

- Adotar SemVer para a versão pública e uma conversão determinística para o `versionCode` Android.
- Gerar e manter `CHANGELOG.md`, tags e GitHub Releases a partir do histórico de Conventional Commits.
- Preservar revisão humana: a release só ocorre após o merge explícito de uma pull request de versão.
- Validar o projeto em pull requests, em atualizações da `master` e novamente antes da publicação.
- Publicar um APK debug nomeado por versão, acompanhado de seu SHA-256 e de um aviso de que não é um artefato de produção.
- Manter as releases como pré-lançamento enquanto houver gates físicos de liberação abertos.

## Fora do escopo

- Assinatura de APK/AAB de produção ou armazenamento de chaves Android no GitHub.
- Publicação na Google Play.
- Merge automático da pull request de release.
- Distribuição de um artefato release ou promessa de prontidão para produção.
- Remoção automática de tags ou releases quando uma etapa posterior falhar.

## Abordagem escolhida

O repositório usará Release Please. Em cada push na `master`, a ferramenta analisa os Conventional Commits desde a última release e mantém uma pull request de release com:

- a próxima versão SemVer;
- a atualização da fonte de versão usada pelo Gradle;
- as entradas acumuladas em `CHANGELOG.md`;
- os metadados necessários para criar a tag e a GitHub Release após o merge.

A pull request nunca será mesclada automaticamente. Seu merge representa a autorização humana para publicar. Após esse merge, a automação criará a tag `vX.Y.Z` e a GitHub Release correspondente; a etapa de artefatos fará uma compilação limpa, verificará os metadados do APK e anexará o APK debug e o arquivo de checksum à release.

### Alternativas consideradas

1. **Release manual:** é simples no primeiro ciclo, mas deixa versão, changelog, tag e artefatos sujeitos a divergência operacional.
2. **Workflow próprio acionado por tag:** automatiza a compilação, porém ainda exige que versão, changelog e tag sejam coordenados manualmente.
3. **semantic-release:** oferece publicação integralmente automática, mas o modelo sem pull request de versão reduz a etapa de revisão humana desejada neste projeto.

Release Please foi escolhido porque mantém a automação auditável por pull request e não publica até que essa revisão seja aprovada e mesclada.

## Componentes previstos

- `release-please-config.json`: tipo de release, estratégia de changelog, arquivos extras de versão e comportamento do primeiro ciclo.
- `.release-please-manifest.json`: estado de versão conhecido pela automação, inicializado em conjunto com a configuração que força o primeiro ciclo a publicar 0.1.0.
- `CHANGELOG.md`: histórico público gerado e mantido pela pull request de release.
- `.github/workflows/ci.yml`: validação em pull requests e pushes na `master`.
- `.github/workflows/release-please.yml`: manutenção da pull request, criação da release e publicação dos artefatos.
- `app/build.gradle.kts`: leitura e validação da versão SemVer e cálculo determinístico do `versionCode`.
- `README.md`: política de releases, natureza não produtiva do APK e instruções para conferir o checksum.

Os nomes finais dos jobs e a sintaxe exata das integrações serão definidos no plano de implementação, sem alterar o comportamento especificado aqui.

## Modelo de versão

### `versionName`

A versão pública segue estritamente o formato estável `X.Y.Z`, com três inteiros decimais não negativos e sem prefixo `v`, sufixo de pré-release ou metadados de build dentro do Gradle.

Cada componente deve estar no intervalo de 0 a 999. Qualquer valor fora desse formato ou intervalo interrompe a configuração do Gradle com uma mensagem clara.

### `versionCode`

O código Android é calculado a partir da mesma versão:

```text
versionCode = major × 1.000.000 + minor × 1.000 + patch
```

Assim, 0.1.0 produz `versionCode` 1000. O limite de três dígitos por componente evita colisões e preserva a ordenação entre versões dentro do esquema adotado.

No primeiro ciclo, a versão pública permanece 0.1.0, mas o `versionCode` atual, 1, passa a ser calculado como 1000. Depois desse bootstrap, Release Please será responsável por toda alteração da versão pública.

## Política de incremento e changelog

O histórico seguirá Conventional Commits:

- `feat:` incrementa a versão minor;
- `fix:` incrementa a versão patch;
- uma mudança incompatível indicada por `!` ou por `BREAKING CHANGE:` incrementa a versão major;
- `docs:`, `test:`, `build:` e `chore:` isolados não provocam nova versão.

Os commits relevantes são agrupados no `CHANGELOG.md` pela pull request de release. Alterações editoriais no changelog gerado devem ser feitas nessa pull request, antes do merge, para que código, versão e documentação permaneçam sincronizados.

## Fluxo de integração e publicação

1. Alterações normais entram no repositório com Conventional Commits.
2. Pull requests e pushes na `master` executam testes JVM, lint e montagem do APK debug.
3. Em um push na `master`, Release Please cria ou atualiza a pull request de release.
4. Uma pessoa revisa a versão, o changelog e a fonte de versão e decide quando mesclar essa pull request.
5. O merge cria a tag e a GitHub Release da versão correspondente.
6. A publicação repete os gates automatizados, compila o APK debug e valida seus metadados.
7. O APK é renomeado para `MobiSentinel-X.Y.Z-debug.apk`.
8. O workflow calcula `SHA-256` e publica o APK e o checksum na mesma GitHub Release.
9. A GitHub Release fica marcada como pré-lançamento enquanto qualquer gate físico da matriz manual estiver aberto.

O ciclo inicial deve gerar explicitamente a release 0.1.0, sem inferir uma versão maior a partir do histórico anterior ao bootstrap.

## Validações automatizadas

### Integração contínua

Em toda pull request e em todo push na `master`, o CI executa no mínimo:

```text
testDebugUnitTest
lintDebug
assembleDebug
```

Qualquer falha bloqueia o job e deve impedir o merge quando a proteção de branch estiver configurada para exigir esse check.

### Publicação

Antes de anexar artefatos, o workflow de release repete os três gates. Em seguida, inspeciona o APK produzido e confirma:

- `versionName` igual à versão da tag, sem o prefixo `v`;
- `versionCode` igual ao resultado da fórmula definida nesta especificação;
- package/application ID igual a `com.mobisentinel.app`.

Uma divergência falha a publicação antes do upload. A verificação não depende apenas do nome do arquivo; ela lê os metadados empacotados no APK.

## Artefatos e comunicação de risco

A GitHub Release disponibiliza o código-fonte gerado pelo GitHub e dois anexos:

- `MobiSentinel-X.Y.Z-debug.apk`;
- `MobiSentinel-X.Y.Z-debug.apk.sha256`.

O texto da release e o README devem declarar de modo visível que:

- o APK usa assinatura debug;
- não é adequado para produção nem para publicação em loja;
- há gates físicos ainda não concluídos;
- o checksum permite verificar a integridade do arquivo baixado.

A condição de pré-lançamento só pode ser removida depois que todos os gates físicos relevantes da matriz manual estiverem concluídos e registrados como aprovados. Essa mudança é uma decisão humana, fora da automação do bootstrap.

## Segurança e cadeia de suprimentos

- Os workflows usam apenas o `GITHUB_TOKEN` fornecido pelo repositório, com permissões mínimas declaradas por job.
- Nenhuma chave de assinatura Android ou credencial de produção é criada ou armazenada.
- Ações de terceiros são fixadas por SHA imutável, mesmo quando o comentário ao lado registra a versão legível correspondente.
- O job que analisa código não recebe permissão de escrita quando ela não é necessária.
- A permissão para criar pull requests, tags e releases fica restrita ao job de Release Please.
- O conteúdo gerado pela automação permanece sujeito à revisão da pull request de release.

## Falhas e repetição

- Uma falha de CI impede a progressão daquele job e permanece visível no GitHub Actions.
- Uma falha após a criação da tag ou release não apaga automaticamente nenhuma delas.
- A release permanece como pré-lançamento e sem os anexos ausentes ou inválidos.
- O workflow pode ser reexecutado para a mesma versão sem criar uma versão nova.
- O upload deve tratar repetição de modo seguro: confirmar ou substituir somente os anexos esperados daquela versão, sem afetar outros arquivos.
- Se os metadados do APK divergirem da tag, a correção deve ocorrer no código/configuração e ser auditável; a automação não mascara a divergência alterando o APK ou a tag silenciosamente.

## Critérios de aceite

1. A configuração do Gradle aceita apenas `X.Y.Z` com componentes entre 0 e 999.
2. A versão 0.1.0 resulta em `versionCode` 1000.
3. Pull requests e pushes na `master` executam testes JVM, lint e `assembleDebug`.
4. Release Please abre ou atualiza uma pull request contendo versão e changelog, sem mesclá-la automaticamente.
5. O primeiro merge de release cria `v0.1.0` e uma GitHub Release 0.1.0 marcada como pré-lançamento.
6. A publicação repete os gates e valida `versionName`, `versionCode` e package dentro do APK.
7. A release contém o APK debug com nome versionado e seu checksum SHA-256.
8. README e release alertam claramente que o APK é debug e não é destinado à produção.
9. Nenhum segredo de assinatura Android é necessário.
10. Ações externas estão fixadas por SHA e os jobs declaram permissões mínimas.
11. Falhas podem ser reexecutadas na mesma versão sem apagar automaticamente tag ou release.

## Fontes normativas

- [Release Please Action — documentação oficial](https://github.com/googleapis/release-please-action)
- [Release Please — customização e arquivos extras](https://github.com/googleapis/release-please/blob/main/docs/customizing.md)
- [Semantic Versioning 2.0.0](https://semver.org/)
- [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/)
- [GitHub Actions — segurança no uso de referências completas de commit](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions#using-third-party-actions)
