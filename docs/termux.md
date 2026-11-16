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

## Validation du verrouillage et de la persistance Android

Le paquet inclut `frontier-lock-tests`, qui teste le même verrou `flock`
que le stockage du nœud. Depuis le répertoire extrait dans Termux :

```bash
chmod +x frontier-lock-tests
TMPDIR="$PREFIX/tmp" ./frontier-lock-tests --nocapture
```

Les tests vérifient l'exclusion de deux ouvertures, l'exclusion entre processus
et la libération du verrou après fermeture. Ils utilisent des fichiers temporaires
distincts du stockage réel du nœud.

Pour vérifier la persistance, arrêter proprement l'ancienne instance, conserver
le même dossier de données et lancer le nouveau binaire avec un intervalle court
pour ce test (le port 4000 est celui utilisé sur l'appareil de validation) :

```bash
MINA_HTTP_PORT=4000 bash run-termux.sh --checkpoint-interval 60 --log-level debug \
  2>&1 | tee "$HOME/mina/persistence-write.log"
```

Attendre le retour à `Synced`, puis un checkpoint réussi dans les logs. Arrêter
avec Ctrl+C et attendre la fin du processus avant de relancer la même commande
avec un autre fichier de log, `persistence-restore.log`. Rechercher les événements
`PersistRestored`, `PersistLoadAbandoned`, `PersistOpenError` et les erreurs
de checkpoint. La présence d'un fichier `LOCK` seul n'est pas une sauvegarde.
Ne pas utiliser `--frontier-store-reset` pour ce test.
