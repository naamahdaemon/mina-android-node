# Application Android — nœud et production

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
différents des ports Termux utilisés pendant les premiers essais. Le profil
initial est devnet sans production ni worker. Le checkpoint est réglable (30 min
par défaut) ; les délais réseau étendus sont facultatifs. Les logs sont limités à deux
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

La CI vérifie le certificat contre `android-signing.sha256`. Une clé manquante
ou différente arrête le build au lieu de produire une mise à jour incompatible.
La signature utilise exclusivement les secrets Actions, sans cache. Configurer
le secret Actions `MINA_SIGNING_KEYSTORE_BASE64` avec le keystore durable encodé
en base64, et si nécessaire `MINA_SIGNING_STORE_PASSWORD`,
`MINA_SIGNING_KEY_PASSWORD`, `MINA_SIGNING_ALIAS`. Ne jamais générer une nouvelle
clé pour cette migration, ni mettre le keystore ou un mot de passe dans Git.
Sans secret, la CI échoue explicitement. Le keystore est matérialisé dans le
répertoire temporaire privé du runner et supprimé à la sortie du script.
L'identité durable a été renouvelée après perte de l'ancienne clé de debug ;
la migration depuis les anciens APK nécessite une réinstallation unique.
Les versions suivantes utilisent la même identité. Ne pas désinstaller l'application pour contourner une incompatibilité
de signature sans accepter de perdre son stockage privé.

## Profils et clé de production

### Langue et apparence

Dans **Settings / Réglages**, la carte **Application settings / Réglages de
l’application** permet de choisir **English** (par défaut) ou **Français** et
de basculer entre thème clair et sombre. Ces choix sont conservés après fermeture
et mise à jour. Ils ne modifient pas le profil réseau et n’arrêtent pas le service
du nœud. Les brouillons de profil sont conservés lors du changement de langue.

Les textes natifs utilisent les ressources Android : `values/strings.xml` pour
l’anglais et `values-fr/strings.xml` pour le français. Les messages de statut sont
résolus à l’affichage, y compris ceux d’un nœud déjà actif. Les logs du moteur
restent techniques, sans traduction. Le dashboard upstream conserve ses textes
anglais ; il s’ouvre avec le thème sélectionné dans l’application.

### Configuration du nœud

**Réglages** permet de sélectionner mainnet/devnet, la production de blocs,
un worker SNARK (clé publique, frais MINA et stratégie), les checkpoints et
les délais réseau. Arrêter le nœud avant de modifier son profil.

Le profil propose aussi le destinataire public de la coinbase (vide = producteur),
le délai minimal de publication, la fenêtre de priorité production face au worker,
le pilotage du worker par le dashboard, le nombre maximal de pairs, le port P2P,
la sauvegarde/restauration de la chaîne et son délai, ainsi que le niveau du journal.
Le contenu du journal et son export sont regroupés dans l'onglet **Journal**.
Les anciens profils reçoivent les valeurs par défaut des nouveaux champs.

Les données
et configurations sont séparées par réseau ; le répertoire devnet existant est
conservé. Un changement de réseau démarre avec son propre stockage.

Importer le fichier JSON Mina **déjà chiffré**, via le sélecteur Android. Il est
enveloppé par AES-GCM avec une clé Android Keystore et conservé dans le stockage
privé exclu des sauvegardes Android. L'original importé reste sous la responsabilité
de son propriétaire : garder une copie chiffrée hors du téléphone.

Le mot de passe est demandé à chaque lancement du producteur. La boîte de saisie
est ouverte après l'éventuelle demande de permission de notification. Le service
utilise le profil exact confirmé par l'écran et une autorisation en mémoire,
consommée une seule fois et expirant après 60 secondes. Sans confirmation fraîche,
le démarrage est refusé ; un ancien Intent ne peut pas réutiliser un mot de passe.
Les profils enregistrés sont rafraîchis au retour dans l'application, sans écraser
des modifications en cours. La boîte de saisie
désactive sauvegarde, autofill et captures. Le secret passe par un pipe privé,
avec une trame bornée, puis est effacé au mieux des buffers applicatifs et Rust ;
il n'entre ni dans les Intent, ni dans les préférences, arguments ou variables
d'environnement. Rust déchiffre la clé en mémoire pour signer : Keystore protège
le stockage, pas la clé en cours d'utilisation dans le nœud. Le runtime Android
peut conserver des copies mémoire temporaires ; aucun effacement absolu n'est promis.
Le fichier temporaire transmis à Rust reste lui-même chiffré par le mot de passe.

Le patch d'entrée privée n'est utilisé qu'avec le drapeau interne de l'application.
L'utilisation CLI depuis Termux et son mode `MINA_PRIVKEY_PASS` restent disponibles.
Les deux paquets bénéficient du dashboard Rust embarqué à la racine de leur API.

## Dashboard et moniteur

La barre de menus regroupe **Nœud**, **Réglages**, **Moniteur**, **Journal**,
**Dashboard** et **Réglages Android**. Des flèches cliquables et une barre de
position rendent le défilement horizontal visible ; le glissement reste disponible.
**Dashboard** ouvre le dashboard upstream embarqué dans une WebView limitée à
`http://127.0.0.1:4300` : pas d'accès aux fichiers ni de pont JavaScript natif.
Son viewport respecte les barres système, l'encoche et le clavier Android,
y compris lorsque le système impose l'affichage bord à bord.
L'apparence des barres système est appliquée seulement après attachement de la vue.
Une erreur d'ouverture ou un arrêt du renderer WebView affiche un écran de retour/
réessai et écrit un diagnostic sans données de saisie dans le Journal.
**Moniteur** affiche CPU (100 % = un cœur), RSS, espace disque, batterie et
température. Les mesures interdites par Android restent absentes.
Les courbes affichent une grille, les graduations RAM/CPU et les horaires locaux ;
l'axe horizontal utilise les heures réelles des mesures, même si elles sont irrégulières.

En production : prochains slots annoncés, blocs canoniques/orphelins, transactions,
zkApps, préparation, preuve, délai preuve→commit et pics CPU/RAM échantillonnés.
Les courbes gardent au maximum six heures en mémoire ; les 500 derniers événements
publics de production sont conservés sur disque par réseau. Les pics sur une preuve
sont disponibles seulement si des échantillons couvrent sa période. Ce portage natif
reprend les indicateurs du moniteur Python Windows sans embarquer Python.

## Validation restante

La synchronisation devnet et la restauration ont été validées sur Xiaomi 14T Pro.
Valider maintenant les profils, le dashboard, l'import chiffré, le mot de passe
incorrect puis correct et la production **sur devnet**, avant un essai mainnet.
Vérifier l'arrêt avec worker, la restauration, la batterie et le fonctionnement
écran éteint. Un build CI vert ne valide pas ces comportements à lui seul.

Navigation remains visible beneath MINA / ANDROID, with Node, Monitor, Dashboard,
Settings and Logs in that order. Settings can switch to a hamburger menu and
configure an HTTPS explorer URL template (`{hash}` required, `{network}` optional).
Minascan is the default. Dashboard is hosted below the same fixed header; Android
application settings are available within Settings. Swipe between pages, except
when a graph consumes the gesture to browse its data.

The fixed header shows the next announced won slot and a one-second countdown.
The progress bar covers the final hour; it turns amber below five minutes and red
below one minute. Past schedules and stopped nodes do not show stale countdowns.

Monitor charts support pinch time zoom, horizontal history panning and a live/reset
button. Autoscale uses the visible samples; maximum scale uses physical device RAM
and logical CPU capacity (100% per core). Block plots scroll horizontally and show
ordinary transactions separately from zkApps, and prep/proof/delay in seconds.
Production attempts are paginated five per page. Tap an attempt for resource charts
around proof/commit/discard, available metrics, discard reason and a hash-based
explorer link. Resource history covers this run's last six hours; missing samples
or timings are not reconstructed. Raw public attempt history survives restarts.
Logs use a selectable, bounded text area with internal vertical scrolling.

Small page flicks now snap back unless they travel at least 30% of a page; slow
swipes require 65%. The Node page presents a colored status badge: green synced,
amber starting/syncing/stopping, gray stopped, red error.

The Monitor preserves its scale mode, time viewport and both block-plot scroll
positions when leaving and returning to the page. Reset/live explicitly clears
the time viewport. A second explorer link on each attempt with a known height
resolves that exact height against the local best-chain headers before opening
the configured explorer by hash. The network is checked before and after lookup.
This is the node's current chain, subject to reorganization, not an external
finality guarantee. Heights outside its retained frontier show an unavailable
message rather than silently selecting another block.

Resource viewports now use the measured interval, excluding empty startup samples.
Zooming before five seconds of measurements leaves live mode intact. A restored
viewport outside the available recording returns to recent data while retaining
its zoom span. Real gaps inside a selected period remain missing and have an
explicit message with the live/reset recovery action.

On Dashboard, the outer pager leaves all content gestures to the WebView. This
prevents diagonal vertical scrolls and internal charts from changing app pages.
Navigation remains available in the menu and by a deliberate 80 dp horizontal
swipe on the fixed MINA / ANDROID header. Other pages retain normal pager swipes.
