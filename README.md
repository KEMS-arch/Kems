# KEMS Facturation Android

Application Android hors ligne (interface française) pour créer et conserver des factures localement, personnaliser les paramètres de l'entreprise et de numérotation, sélectionner le mode de paiement (Échéance par défaut), gérer les lignes de facture, puis générer un PDF via le service d'impression Android.

## Compiler l'APK
1. Installer Android Studio sur un ordinateur.
2. Ouvrir le dossier `KEMSFacturationAndroid` dans Android Studio.
3. Laisser Gradle synchroniser le projet et installer le SDK Android 35 si demandé.
4. Choisir **Build > Build APK(s)**.
5. Android Studio indique le chemin de l'APK généré, généralement `app/build/outputs/apk/debug/app-debug.apk`.
6. Copier cet APK sur le téléphone Android et l'installer. Autoriser l'installation depuis cette source si Android le demande.

## Fonctions
- Paramètres entreprise, coordonnées, identifiants, devise, préfixe, année, prochain numéro, paiement par défaut et délai de paiement.
- Création facture, lignes d'articles, quantités, prix et total.
- Historique local et génération PDF par le menu d'impression Android (choisir « Enregistrer au format PDF »).
- Export/import de sauvegarde JSON.

## Important
Ce dossier est le **projet source Android**, pas un APK déjà compilé. Les données sont stockées localement dans le stockage WebView de l'application. Exportez une sauvegarde régulièrement. Vérifier les factures générées avant usage professionnel.
