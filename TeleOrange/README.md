# Télécommande Orange (sans pub)

App Android qui détecte ton décodeur Orange sur le Wi-Fi et le pilote via son API locale (port 8080). Aucune dépendance, aucune pub, aucun tracker.

## Compiler et installer
1. Installe **Android Studio** (gratuit) puis *File → Open* → choisis le dossier `TeleOrange`.
2. Laisse la synchronisation Gradle se terminer (il télécharge le SDK si besoin).
3. Branche ton téléphone en USB avec le **débogage USB** activé (Paramètres → À propos → taper 7× sur « Numéro de build », puis Options développeur → Débogage USB).
4. Clique sur ▶ *Run*. L'app s'installe et se lance.

Pour un APK à garder : *Build → Build App Bundle(s)/APK(s) → Build APK(s)*, fichier dans `app/build/outputs/apk/debug/`.

## Utilisation
- Au premier lancement l'app balaye le réseau et mémorise l'IP du décodeur.
- Si elle ne le trouve pas : « Saisir l'IP » (visible dans l'interface de la Livebox, http://192.168.1.1).
- Volume, chaînes et flèches se répètent quand on garde le doigt appuyé.

## Compatibilité
Décodeurs Orange UHD 86, TV 4 et TV 5 (API `http://IP:8080/remoteControl/cmd`). Le téléphone doit être sur le même réseau que la Livebox.
