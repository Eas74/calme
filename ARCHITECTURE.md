# Calme — client Instagram minimaliste (Android, WebView)

## Objectif
Consulter Instagram (abonnements, stories, messages, notes) **sans défilement infini** :
un élément à la fois, plafond quotidien, pas d'Explorer ni de Reels. Aucun serveur tiers.

## Modèle de menace (ce qu'on protège / contre quoi)
| Actif | Risque | Mesure |
|---|---|---|
| Session Instagram (cookies WebView) | vol par une autre appli / sauvegarde cloud / accès physique | stockage privé de l'appli, `allowBackup=false`, `data_extraction_rules`, verrou biométrique/écran à chaque ouverture, `FLAG_SECURE` |
| Vie privée | trackers, domaines tiers | `UrlPolicy.isRequestAllowed` coupe tout sauf instagram.com / cdninstagram.com / fbcdn.net ; pas d'analytics ni de lib tierce |
| Intégrité | contenu de la page qui attaque l'appli | pas de `addJavascriptInterface`, accès fichiers/contenus désactivés, HTTPS seul, JS injecté limité à www.instagram.com |
| Contournement du but | ouvrir Explorer/Reels | blocage de chemin dans `shouldOverrideUrlLoading` **et** `doUpdateVisitedHistory` (Instagram est une SPA) |

## Structure
- `UrlPolicy.kt` : politique de navigation (pure, testée). **Toute règle réseau passe ici.**
- `Quota.kt` : plafond quotidien (pur, testé). Constante `DAILY_LIMIT` dans `MainActivity`.
- `MainActivity.kt` : UI programmatique, WebView durcie, verrou biométrique, blocage du glissement vertical.
- `assets/selectors.json` : seuls sélecteurs dépendant du HTML d'Instagram.
- `assets/inject.js` : masquage CSS + `__calm.next()/prev()`. Appelé depuis Kotlin ; ne peut rien appeler en retour.

## Comportement
- Page d'accueil : `https://www.instagram.com/?variant=following` (flux Abonnements). L'accueil sans ce paramètre est redirigé (garde anti-boucle de 2 s).
- Scroll vertical bloqué partout **sauf** `/direct` (messagerie) et `/accounts` (connexion). Les boutons ◀ / Suivant ▶ font défiler publication par publication ; seul Suivant consomme le quota.

## Limites connues (assumées)
- Les WebSockets (messagerie temps réel) ne passent pas par `shouldInterceptRequest` : ils ne sont pas filtrés par domaine.
- Quota contournable en effaçant les données de l'appli ou en changeant la date. Aller-retour ◀/▶ consomme plusieurs fois.
- Instants non disponibles sur le web. Connexion Facebook/Google non prise en charge (connexion par identifiant Instagram).
- Usage d'un client non officiel : contraire aux CGU d'Instagram, vérifications de connexion possibles.

## Maintenance
1. **Après une mise à jour d'Instagram qui casse quelque chose** : inspecter la page (Chrome desktop, mode mobile), corriger `selectors.json` uniquement. Si `Suivant` ne fait rien : vérifier `post`.
2. **Domaine bloqué à tort** (page cassée) : lancer `adb logcat -s Calme`, repérer l'hôte bloqué, juger s'il est légitime avant de l'ajouter à `ALLOWED_DOMAINS`, puis ajouter un test.
3. **Dépendances** : versions épinglées dans `gradle/libs.versions.toml`. Mises à jour volontaires, une à la fois, puis `./gradlew test` + test manuel. Durcissement possible : `dependencyLocking`.
4. **Toute modification de règle** (UrlPolicy, Quota) s'accompagne d'un test.
5. **Build release** : signer avec une clé personnelle, ne jamais commiter le keystore.
6. Pour faire intervenir Claude : fournir ce fichier + les fichiers concernés + le message d'erreur/logcat.

## Build
Ouvrir le dossier dans Android Studio (génère le Gradle wrapper), brancher le téléphone (débogage USB), Run.
Tests : `./gradlew test`.
