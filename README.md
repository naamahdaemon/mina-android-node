# Mina Android Node

Portage expérimental de [Mina Rust](https://github.com/minanode/mina-rust)
vers Android ARM64, d'abord sous Termux, puis dans une application Android.
La démarche reprend celle de [Mina Win Node](https://github.com/naamahdaemon/mina-win-node).
Le moteur Mina et son protocole proviennent du projet upstream.

## Compilation dans GitHub Actions

Le workflow **Android native prototype** compile sur Ubuntu avec le NDK
Android `27.3.13750724`, Rust `1.92.0` et la cible `aarch64-linux-android`.
Il démarre sur les pushes de code et peut être lancé avec **Run workflow**.
Aucune compilation locale n'est nécessaire.

La CI construit aussi un premier APK **Mina Node** pour Android ARM64 : démarrage
et arrêt du nœud, notification persistante, statut et export des logs.
Télécharger l'artifact `mina-android-devnet-apk` du build réussi.
Voir [le guide de l'application Android](docs/android-app.md).

Pour les modifications de l'application, **Android application only** réutilise
un artifact natif existant et évite de recompiler Rust. Il se lance sur les
changements de l'application ou manuellement avec **Run workflow**.

`upstream.json` fige les versions. `scripts/prepare-source.py` prépare les
sources et applique `patches/*.patch`. Les adaptations Windows ne sont pas
appliquées au port Android. Le frontend historique est exclu du checkout
pour permettre aussi l'inspection des sources sous Windows.

La première configuration utilise libp2p et les circuits embarqués, sans
WebRTC ni dashboard. Android utilise son allocateur système. Le linker est
configuré pour des segments alignés sur 16 Kio. La compatibilité effective
doit être vérifiée sur appareil.

Un build réussi fournit `mina-android-arm64` et ses sommes SHA-256. Les logs
sont conservés dans `android-build-diagnostics`, y compris en cas d'échec.
Voir [les instructions Termux](docs/termux.md).

## État

Chaîne CI et première adaptation en préparation. Compilation, démarrage,
synchronisation et reprise sur téléphone restent à valider. L'application
Android et la production de blocs ne sont pas encore qualifiées.

## Attribution

Le code du nœud conserve les licences et notices upstream, incluses dans
l'archive générée. Ce dépôt contient le portage et son outillage.
