# Application Android — prototype devnet

Le workflow **Android native prototype** construit le binaire Rust avec ses
patches Android, puis l'application Kotlin/Jetpack Compose. L'artifact
**mina-android-devnet-apk** contient `mina-android-devnet-debug.apk` et
`APK-SHA256SUMS`. Le build exécute les tests du verrou, les tests du superviseur,
les tests unitaires Kotlin et Android Lint avant de fournir l'APK.

L'indicateur **Sauvegarde** affiche l'état réel du frontier store : ouverture,
absence de checkpoint, hauteur sauvegardée, vérification ou erreur. Le stockage
disque des comptes (`ledger-accounts`) utilise le même verrou Android natif que
le frontier store. Les deux mécanismes ont des rôles distincts : les fichiers
de comptes soutiennent le fonctionnement en cours, les checkpoints permettent
de restaurer la chaîne au prochain démarrage.

## Reconstruire uniquement l'application

Utiliser **Android application only** dans GitHub Actions, puis **Run workflow**.
Laisser `native_run_id` vide pour utiliser le dernier artifact natif disponible
sur `main`, ou saisir l'identifiant numérique d'un run du workflow natif sur
`main` (visible dans son URL `/actions/runs/…`). Le workflow télécharge le paquet,
vérifie ses SHA-256 et construit uniquement l'application et son petit superviseur
C, sans installer Rust ni appeler Cargo.

Les changements sous `android/` et les scripts de build de l'application lancent
automatiquement ce workflow. Ils ne déclenchent plus **Android native prototype**.
Si un même push modifie aussi les entrées natives, seul le job de routage tourne
dans le workflow application : son job APK est ignoré. Le workflow complet
construit alors Rust puis l'APK, dans cet ordre. Un lancement manuel du workflow
application reste possible après disponibilité d'un artifact natif compatible.
Le workflow natif se lance automatiquement lorsque les patches, `upstream.json`,
le script de compilation Rust, la préparation des sources ou le test du verrou
changent. Une modification du workflow natif lui-même peut être testée en le
lançant manuellement.

La sélection vérifie que les entrées de compilation natives n'ont pas changé
depuis l'artifact. Si elles ont changé, ou si les artifacts ont expiré, lancer
d'abord **Android native prototype**. Aucun repli ne recompile Rust dans le
workflow application seule. Les nouveaux artifacts natifs sont conservés 90 jours
et publiés avant le build APK, afin de rester réutilisables même si ce dernier
échoue. Le run et le commit du binaire réutilisé figurent dans le résumé Actions.

## Premier essai sur Xiaomi 14T Pro / Android 16

1. Télécharger l'artifact, extraire le ZIP puis ouvrir l'APK sur le téléphone.
   Autoriser l'installation depuis le navigateur ou le gestionnaire de fichiers
   si Android le demande. Ce paquet de test cible Android 13+ et ARM64.
2. Arrêter le nœud Termux pour ce premier essai et désactiver le VPN.
   Ouvrir **Mina Node**, autoriser les notifications et choisir **Démarrer**.
3. Observer la synchronisation, la hauteur, les pairs et le journal. La première
   synchronisation utilise le stockage privé de l'application, séparé de Termux.
4. Choisir **Arrêter**, attendre **Nœud arrêté**, puis **Démarrer** pour vérifier
   la restauration du frontier store.
5. Fermer l'écran de l'application et vérifier que la notification reste visible.
   Tester ensuite plusieurs minutes écran éteint. Le bouton **Exporter** enregistre
   les journaux via le sélecteur de fichiers Android.

La notification permet aussi d'arrêter le nœud. Le service maintient un verrou CPU
pendant l'exécution. Les réglages d'économie de batterie du téléphone peuvent
néanmoins limiter ou arrêter le service. Aucun démarrage au boot n'est prévu.
La présence d'un VPN est affichée, compte tenu du ralentissement observé en test.

L'API écoute uniquement sur `127.0.0.1:4300`, et libp2p utilise le port 8303,
différents des ports Termux utilisés
pendant les premiers essais. Aucun producteur de blocs ou portefeuille n'est
configuré. Les délais RPC et l'intervalle de checkpoint restent ceux du moteur
upstream pour ce premier essai sans VPN. Les logs console sont limités à deux
fichiers d'environ 4 Mio chacun; les données de chaîne restent persistantes.

## Cycle de vie natif

L'exécutable Rust est emballé sous `lib/arm64-v8a/libmina.so` et extrait par
l'installateur Android dans `nativeLibraryDir`. Il est exécuté directement,
sans `dlopen`, shell ou Termux. Le packaging natif traditionnel est activé et
l'alignement ELF est de 16 Kio. Les licences et notices upstream sont incluses
dans les assets de l'APK.

Un petit superviseur natif reçoit une commande d'arrêt par un pipe privé, envoie
SIGINT au nœud et attend sa sortie jusqu'à 90 secondes, puis impose un arrêt.
La fermeture du pipe quand l'application meurt déclenche le même chemin. Le
processus Rust porte un signal de mort du superviseur pour éviter un nœud orphelin.
Le verrou du frontier store protège aussi contre une seconde ouverture.

Le service utilise le type Android `specialUse`, décrit dans le manifeste pour
cette exécution continue et explicitement demandée par l'utilisateur. Une
publication Play Store nécessitera sa propre validation; cette CI fournit un APK
de test installable directement.

Références : [types de services au premier plan](https://developer.android.com/develop/background-work/services/fgs/service-types),
[restrictions sur les exécutables](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission),
[AGP 8.10 et SDK 36](https://developer.android.com/build/releases/agp-8-10-0-release-notes).

## Build et mises à jour

Les deux workflows calculent le `versionCode` via `scripts/apk-version.py`, en
secondes depuis le 1er janvier 2020 UTC. Les compteurs distincts de workflow ne
sont plus utilisés : un nouveau build reçoit un code supérieur aux anciennes
versions `0.1.*`, quel que soit le workflow. Le nom affiche la date UTC du build.
L'artifact inclut `APK-BUILD-INFO.txt`, avec version, commit et empreintes du
certificat de signature. Une version supérieure ne corrige pas une signature
différente : conserver l'application installée pour diagnostiquer ce cas.

Versions : AGP 8.10.1, Gradle 8.11.1, JDK 17, Kotlin 2.1.21, SDK/target 36.
Gradle est installé par `gradle/actions/setup-gradle` dans GitHub Actions; la
compilation locale n'est pas nécessaire. Android Studio peut ouvrir `android/`.

La clé de signature de debug est conservée dans le cache GitHub Actions pour
permettre les mises à jour. Ce cache peut être évincé : une clé de signature
durable devra être configurée avant de dépendre des mises à jour pour conserver
les données. Ne pas désinstaller l'application pour contourner une incompatibilité
de signature sans accepter de perdre son stockage privé.

## Validation restante

Le premier APK doit encore être validé sur appareil : extraction et exécution
des binaires, autorisation de service, synchronisation, arrêt, restauration,
notification et fonctionnement écran éteint. Un build CI vert ne valide pas ces
comportements Android à lui seul.
