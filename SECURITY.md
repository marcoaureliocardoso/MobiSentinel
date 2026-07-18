# Política de segurança

## Versões com suporte

A versão estável mais recente publicada em [GitHub Releases](https://github.com/marcoaureliocardoso/MobiSentinel/releases) recebe correções de segurança. Versões anteriores e pré-releases podem ser analisadas, mas não têm garantia de correção retroativa.

## Reportar uma vulnerabilidade

Não publique detalhes exploráveis em uma issue. Use o [reporte privado de vulnerabilidade do GitHub](https://github.com/marcoaureliocardoso/MobiSentinel/security/advisories/new) e inclua:

- versão e modelo/versão do Android;
- impacto e condições necessárias;
- passos mínimos para reproduzir;
- logs ou capturas sem dados pessoais;
- sugestão de correção, se houver.

O recebimento será confirmado assim que possível. A análise prioriza impacto na integridade do APK, continuidade de atualização, execução do serviço, permissões e exposição de dados.

## Integridade das releases

O APK oficial usa o pacote `br.com.marcocardoso.mobisentinel`. A impressão digital SHA-256 canônica do certificado está em [signing/release-certificate.sha256](signing/release-certificate.sha256).

Cada release estável deve conter exatamente:

- `MobiSentinel-X.Y.Z.apk`;
- `MobiSentinel-X.Y.Z.apk.sha256`.

Confira o checksum e, para maior garantia, execute `apksigner verify --verbose --print-certs` e compare o certificado. Não instale uma pré-release, um APK com `debuggable`, um arquivo com nome diferente ou um APK cujo certificado/checksum não corresponda.

## Proteção da chave

A chave, o arquivo de recuperação e suas senhas não pertencem ao repositório. O backup pessoal em `MobiSentinel/production-signing` no Google Drive permite restaurar o ambiente protegido `production` do GitHub Actions.

Nunca compartilhe, anexe a issues, copie para logs ou publique os arquivos dessa pasta. Qualquer pessoa com o PKCS#12 e as credenciais pode assinar uma atualização aceita como MobiSentinel.

## Resposta a incidente de assinatura ou release

1. Suspenda merges de release e novas publicações.
2. Marque a release afetada como pré-release e remova-a da posição de latest sem apagar evidências.
3. Preserve logs do GitHub Actions, hashes, lista de assets e eventos do ambiente `production`.
4. Compare o certificado do APK com o fingerprint versionado e revise o histórico do workflow.
5. Se a chave permanecer confiável, publique a correção com versão superior; nunca reutilize uma versão ou tag.
6. Se houver suspeita de comprometimento da chave, não gere silenciosamente outra chave: documente o incidente e planeje a migração, pois APKs assinados por uma chave diferente não atualizam a instalação existente fora de um mecanismo de rotação compatível.

O runbook completo está em [docs/releasing/production-release.md](docs/releasing/production-release.md).
