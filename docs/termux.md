# Prototype Android ARM64 / Termux

Appareil de validation : Xiaomi 14T Pro, Android 16, 12 Go de RAM.
Ce prototype n'est pas encore validé sur téléphone. Commencer sur devnet.

Télécharger l'artifact `mina-android-arm64` du workflow GitHub Actions,
décompresser le ZIP puis transférer le fichier `.tar.gz` vers le téléphone.
Dans Termux :

```bash
termux-setup-storage
mkdir -p "$HOME/mina"
cd "$HOME/mina"
# Adapter le chemin au téléchargement.
tar -xzf "$HOME/storage/downloads/mina-android-arm64.tar.gz"
cd mina-android-arm64
sha256sum -c SHA256SUMS
./mina --help
getconf PAGESIZE
bash run-termux.sh
```

Extraire dans le stockage privé Termux : le stockage partagé Android ne
convient pas à l'exécution des binaires. Aucun environnement proot n'est prévu.
L'API HTTP écoute sur `127.0.0.1:3000`. Le tableau de bord n'est pas embarqué.

Dans une autre session Termux :

```bash
cd "$HOME/mina/mina-android-arm64"
./mina node status --node http://127.0.0.1:3000
```

Arrêter avec Ctrl+C, puis relancer pour vérifier la reprise des données.
Pour une session prolongée, utiliser `termux-wake-lock` si disponible et
autoriser l'activité en arrière-plan dans les réglages Android. Libérer ensuite
avec `termux-wake-unlock`. Cela ne garantit pas qu'Android gardera le processus.

Relever les erreurs, le temps de synchronisation, la mémoire, la température
et l'espace disque. Ne pas utiliser de clé de production pour cette validation.
