# Changelog

## [1.0.0](https://github.com/marcoaureliocardoso/MobiSentinel/compare/v0.1.1...v1.0.0) (2026-07-18)


### ⚠ BREAKING CHANGES

* installs now use br.com.marcocardoso.mobisentinel and do not update com.mobisentinel.app.

### Features

* adopt permanent Android application identity ([e655972](https://github.com/marcoaureliocardoso/MobiSentinel/commit/e655972a367abbe40847e45c181c7e40e6a5209f))


### Bug Fixes

* close production release validation gaps ([70a0351](https://github.com/marcoaureliocardoso/MobiSentinel/commit/70a03518a8408c8739d67f48d1b4024ed61cbf90))
* guarantee signing key cleanup ([fec9b6a](https://github.com/marcoaureliocardoso/MobiSentinel/commit/fec9b6ac3f4acc56bde84cdf5a2d71b091ad9c92))
* protect production release credentials ([e21d7bb](https://github.com/marcoaureliocardoso/MobiSentinel/commit/e21d7bb60a1633160b31c831b4dfa72e2161ea94))
* resolve signing tools cross-platform ([939af3d](https://github.com/marcoaureliocardoso/MobiSentinel/commit/939af3db1c9f57d042595786390ad6a919c0555f))
* select Gradle wrapper cross-platform ([7d717f7](https://github.com/marcoaureliocardoso/MobiSentinel/commit/7d717f792625f814874e5f6bff05dc6c5d2b0cbe))

## [0.1.1](https://github.com/marcoaureliocardoso/MobiSentinel/compare/v0.1.0...v0.1.1) (2026-07-17)


### Bug Fixes

* add temporary cellular validation probe ([3dd499a](https://github.com/marcoaureliocardoso/MobiSentinel/commit/3dd499aa4940a12dbc2ec864b6d8a9abaf9f4eba))
* declare cellular sdk guard to lint ([4334e60](https://github.com/marcoaureliocardoso/MobiSentinel/commit/4334e60d12ec39162ceddcf4121e23b4c46f876d))
* define cellular validation policy ([885fc8f](https://github.com/marcoaureliocardoso/MobiSentinel/commit/885fc8ff37d66c741e9b0018212df8e1631a23c4))
* preserve network monitoring on legacy Android ([369417d](https://github.com/marcoaureliocardoso/MobiSentinel/commit/369417d8583019676e8214a5f45022fa29c3f1c0))
* release cancelled cellular probes safely ([4977482](https://github.com/marcoaureliocardoso/MobiSentinel/commit/49774821eab3152fb15082778704681c56794871))
* schedule cellular validation probes ([51c58db](https://github.com/marcoaureliocardoso/MobiSentinel/commit/51c58db9116df0b4a04a44c44969b70bb25d3f5d))
* validate cellular connectivity independently ([0dffbe4](https://github.com/marcoaureliocardoso/MobiSentinel/commit/0dffbe4e4be71f6e0bc9b046e034539adb0afac6))
* validate cellular connectivity independently ([206c9c1](https://github.com/marcoaureliocardoso/MobiSentinel/commit/206c9c11e6561bbf0d6bfd056716651588989dd7))

## 0.1.0 (2026-07-16)


### Features

* add status and monitoring settings screens ([dcb401e](https://github.com/marcoaureliocardoso/MobiSentinel/commit/dcb401e68c98909c97c2ed8ccfc3f935a9b73c0a))
* debounce confirmed connectivity states ([c70ecd1](https://github.com/marcoaureliocardoso/MobiSentinel/commit/c70ecd1d8e224012c050426fa753bafd33107663))
* narrate confirmed events in Portuguese ([7022513](https://github.com/marcoaureliocardoso/MobiSentinel/commit/70225131518f964c243b0ae8dc1f086816f3fbb6))
* observe Wi-Fi and cellular networks separately ([e799767](https://github.com/marcoaureliocardoso/MobiSentinel/commit/e7997678a29c589755aa326fde6f9947feea9aa9))
* orchestrate continuous connection monitoring ([6c42e1a](https://github.com/marcoaureliocardoso/MobiSentinel/commit/6c42e1a3a73a198d96603fca79f609ff4cf40769))
* persist monitoring preferences ([b1d602e](https://github.com/marcoaureliocardoso/MobiSentinel/commit/b1d602e6500d8c69a028fdff3e76c0d0f5b401e6))
* run monitoring as a restartable foreground service ([5d4a298](https://github.com/marcoaureliocardoso/MobiSentinel/commit/5d4a298b7e26c50f86eaba7045af29b77e917a2a))


### Bug Fixes

* guard notification updates by permission ([2fbb11f](https://github.com/marcoaureliocardoso/MobiSentinel/commit/2fbb11f31ef3383baeea6f6cf443098f9e700ce9))
* provide repository context to release dispatch ([#3](https://github.com/marcoaureliocardoso/MobiSentinel/issues/3)) ([d6d9d62](https://github.com/marcoaureliocardoso/MobiSentinel/commit/d6d9d62d61eee204af828b17aaed3f7c99eba5e0))
* provide repository context to release jobs ([#4](https://github.com/marcoaureliocardoso/MobiSentinel/issues/4)) ([d01b8cb](https://github.com/marcoaureliocardoso/MobiSentinel/commit/d01b8cbf761a078f802ecef6ff8b735ec9c4d4ba))
