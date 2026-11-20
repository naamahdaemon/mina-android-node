# Privacy policy — MinAndroid / Mina Android Node

Last updated: 20 November 2026

This policy covers **MinAndroid**, also called **Mina Android Node** or **Mina Node**, Android package **org.minanode.android**, maintained by **NaamahDaemon**. It covers the Android application and the Mina Rust node bundled with it.

## Purpose and contact

The app runs a Mina peer-to-peer node on your device. You can optionally enable block production or SNARK work. There is no developer-hosted account registration, custodial wallet service or developer-operated application backend required to run it.

For privacy questions or deletion requests concerning information you have sent to the maintainer, use the [project issue form](https://github.com/naamahdaemon/mina-android-node/issues/new) or the contact options published on the [maintainer profile](https://github.com/naamahdaemon). GitHub issues are public: do not include passwords, private keys, personal documents or unredacted logs. Ask for a private contact channel before sharing sensitive information. GitHub may require an account to send an inquiry; reading this policy does not require one.

## Information processed on the device

The app processes and stores the following to provide its features:

- **Node settings:** selected network, public producer/SNARK worker addresses, coinbase receiver, peer and persistence settings, notification and suspension schedules, language, theme and chart preferences.
- **Node data:** peer information, network identity and synchronization state, ledger data, blockchain checkpoints and other files needed to validate the chain and restore the node.
- **Monitoring:** CPU and memory use, available storage, battery level and temperature, node status, production attempts and timings, and diagnostic logs. These are shown locally; there is no automatic upload of these measurements to the maintainer.
- **Key material you choose to import or generate:** a password-encrypted Mina private-key file and the corresponding public address. Configured producer files receive an additional encryption layer using Android Keystore. A generated encrypted file awaiting export is retained privately until you complete the flow or remove application data.

These files are held in application-private storage. The manifest disables Android application backup. Copies you export through a document provider are outside this storage and may be uploaded by your selected provider under its own settings and policy.

## Passwords, private keys and clipboard

A producer-key password is used locally to unlock the key for the running native node; it is not saved as a reusable application preference or sent to the maintainer. The running node necessarily holds unlocked signing material in memory. Suspending the process retains that memory; suspension is not the same as locking or terminating the node. Stop the node to end that running process.

The key-generation flow can display the newly generated private key once and lets you explicitly copy it. Copying puts it into the Android clipboard. Private-key clips are marked sensitive, and the app attempts to clear its own clip after about one minute while its process remains available. This cannot revoke a copy already read or pasted elsewhere. You control where you save encrypted exports; protect both the file and its password. The app does not send private keys or passwords as Mina network messages.

## Network communication and public blockchain information

**Running a node is not an offline-only activity.** It connects to Mina peers and may use bootstrap/discovery services and DNS resolution. Remote services and peers can see connection information such as your public IP address, connection times, peer identity and protocol information. Network messages exchange blocks, proofs, transactions, ledger information and other data needed for synchronization, validation and any enabled production/SNARK role.

Public producer, worker and reward-receiver addresses, produced blocks, proofs and associated network activity may be distributed to other participants and recorded on the blockchain. Public addresses can be linked to a person when combined with information available elsewhere. Mainnet and devnet are third-party decentralized networks; the maintainer does not control their participants, storage locations or retention.

The application's node HTTP API and embedded dashboard are configured on the device loopback address (127.0.0.1); the app does not configure that HTTP service to listen on all network interfaces. This does not make loopback a separate authentication boundary against every other process on the device. Peer-to-peer connections are separate from the local HTTP interface.

Opening a block explorer uses your selected external explorer (Minascan by default, or a custom URL). The destination receives the requested block hash/height or other URL parameters and ordinary web connection information. Opening this policy, source links or support pages similarly connects to their hosting provider. Such services apply their own policies. The policy page and project are hosted by GitHub; see [GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).

## Sharing, diagnostics and notifications

The app does not include advertising, advertising-ID collection, third-party advertising/analytics SDKs or automatic developer crash-report uploads. It does not sell user data. This does not remove the network exchanges described above or data processing by Android, your app store, browser or document provider.

Logs are exported only when you choose an export destination. They can contain public addresses, peer IP addresses/identities, paths, schedules and diagnostic information. Review and redact them before sending or publishing them. If you send logs or support messages to the maintainer, they are used to investigate and answer your request, and are processed by the service you use to send them.

Production alerts and reminders are generated locally by the app, not by a developer push-notification server. Depending on Android settings, they can appear on the lock screen. The app uses Internet/network-state permissions for networking, a foreground service and wake locks to run the node, notification permission for status/production alerts, and optional exact-alarm access to resume a suspended node. File import/export uses the Android document picker. It does not request access to contacts, microphone, camera or device location.

## Retention and deletion

Settings, keys and node files remain locally until replaced, removed through an available app control, or deleted by clearing the app's storage or uninstalling it. Logs rotate by size; monitoring history and saved production snapshots are bounded and may be overwritten. An app update normally preserves private data.

To delete local application data, stop the node and use **Android Settings → Apps → Mina Node / MinAndroid → Storage → Clear storage**, or uninstall the app. This deletes local keys and may permanently remove access to funds if you have no separate key backup. Export any key you need to retain before deletion. Removing a configured producer key does not delete separately exported files or all other application data.

Delete exported backups/logs separately from the device, cloud provider, clipboard destinations or recipients where you placed them. Public blockchain data and copies already retained by peers cannot be erased by clearing this app. Support information sent through GitHub remains subject to GitHub's retention and deletion controls; contact the maintainer through the mechanism above about copies under their control. There is no fixed local deletion period for a ledger or key you choose to retain.

## Changes

Changes to app behavior may require a policy update. The date above identifies the current policy. Material changes will be described in the project release notes. This policy describes the application; it does not replace third-party service policies or guarantee acceptance by an application store.
