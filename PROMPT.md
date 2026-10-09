# Mission : TotM Editor v1

Lis d'abord `CLAUDE.md` en entier : il contient tout ce qu'on sait déjà (formats, offsets, architecture, pièges de build).
Le prototype dans `editor/` **fonctionne sur le téléphone de Sky**. On part de là et on le transforme en vrai outil.
Ne casse pas ce qui marche : le patch statique + slot, la lib native, le format de fichier.

L'APK officiel est dans `input/tomb-of-the-mask-1-2-28.apk` (si absent, demande-le à Sky).

## Objectifs

### 1. « Tester » sans passer par le menu du jeu (priorité 1)
Aujourd'hui, Tester ouvre le jeu et il faut lancer un stage à la main. On veut :
- **Tester** : le jeu s'ouvre et démarre directement le niveau édité.
- À la fin (victoire, mort, abandon), on peut revenir à l'éditeur en un geste : par exemple un bouton ou un overlay natif « Retour à l'éditeur / Rejouer ». Si c'est faisable proprement, retour automatique.
- **Le test ne doit pas toucher la sauvegarde** : pas d'étoiles, pas de déblocage, pas de stats sur le stage utilisé comme support. Pistes : hooker `SaveGameResults`, `StageCompleted` ou `SaveGame` quand un test est actif.
- Pistes pour le lancement : `DataManager.activeStage` / `runNextStage`, `GameController.InitWithLevelIndex`, `GameOverController.BtnPlayLevelPressed(int)`, `StageInfoController.BtnPlayPressed`. Il faut aussi comprendre l'enchaînement des scènes `level0`, `level1`, `level2`.
- Pour appeler du code managé depuis le natif : API IL2CPP exportée ou pointeurs de fonctions par RVA, sur le thread Unity.
  Attention : pas de modification de pages de code à l'exécution. Si un nouveau hook est nécessaire, étendre le principe du **patch statique + slot** (autres slots en fin de `.bss`, `patch_il2cpp.py` généralisé et data-driven).

### 2. Tous les blocs, avec un modèle « logique » (priorité 1)
- L'éditeur manipule des **blocs logiques** et l'encodage en IDs bruts se fait à la sauvegarde ou au test :
  - un Canon + rotation (4 dir.) ; une Chauve-souris + direction ; une Plateforme + direction ; un Trampoline + diagonale ; BonusTeeth + côté ; Rotation + sens ; Portail + orientation H/V ;
  - **groupes appariés** : portails (paires 1..7), snakes (Enter/Exit/Trigger 1..6), light balls (balle + déclencheur 1..6), singes (déclencheurs 1..3). L'éditeur attribue et affiche les numéros de groupe (couleurs), et signale une paire incomplète ;
  - les autres blocs : Ice, CloseBrick, TimerWall, Fish, MSpikes, Spikes, BonusDoor, BonusWall, WallEmpty, ZoneExit, power-ups, Coin/points, Star, Enter/Exit.
- **Conversion sans perte** : les 300 stages officiels et les 517 sections font l'aller-retour brut → logique → brut à l'octet près (test automatisé obligatoire). Les valeurs inconnues (16, 17, 18, 25, 27, 163, 229) sont conservées en « bloc brut » ; essaie aussi de découvrir leur rôle dans `LevelLoader`.
- Rotation : un bouton ou un geste pour tourner le bloc sélectionné avant de le poser, et pour tourner un bloc déjà posé (appui sur un bloc posé avec l'outil rotation).
- Validation par type : 1 entrée / 1 sortie, paires complètes, pics sans mur adjacent, bords ouverts, nombre d'étoiles, etc. Panneau de problèmes cliquable qui centre la vue sur la case fautive.

### 3. UI dans l'esprit Tomb of the Mask, pensée pour le tactile (priorité 2)
- Direction artistique TotM : fond noir, néons jaune / magenta / cyan, pixel art net (filtrage nearest), police pixel. Tu peux réutiliser les sprites et textures de `reference/assets/` ainsi que `reference/dev_tmx/tiles.png`.
- Ergonomie téléphone :
  - palette en bas par catégories (Structure, Collectibles, Pièges, Ennemis, Mécanismes) ;
  - outils pinceau, rectangle, ligne, remplissage, gomme, pipette, sélection (déplacer / copier / coller) ;
  - annuler / rétablir ;
  - zoom et défilement fluides, mini-carte, grille activable ;
  - retour haptique léger ; gros boutons atteignables au pouce ; portrait, et paysage sur tablette.
- Liste des niveaux avec **vignettes**, tri, recherche, renommer, dupliquer, supprimer, import/export (fichier `.txt` compatible `totm_levels.py`, partage Android, presse-papiers).
- « Nouveau depuis un officiel » avec aperçu des 300 stages.

### 4. Aperçu fidèle avec les assets du jeu (priorité 2)
- Rendu du niveau avec les **vrais sprites** : murs avec bords et coins (`1_Wall_*`, `1_Corner_*`), points, étoiles, ennemis, pièges, sortie, etc., **teintés** comme en jeu. Les sprites sont blancs, il faut retrouver les couleurs : paires de couleurs du `GameController`, constantes dans le code ou les assets.
- Ce rendu sert pour les vignettes, l'écran d'aperçu, et idéalement l'éditeur lui-même (avec un mode « schéma » simplifié en option).
- Les sprites peuvent être embarqués comme assets de l'APK éditeur (usage perso). Choisis le format le plus simple à charger côté Java.

### 5. Qualité de vie (priorité 3, au choix selon le temps)
Symétrie miroir, remplissage auto des couloirs en points, vitesse de lave avec aperçu, statistiques du niveau, gabarits, packs de niveaux, choix du stage support (ou slot dédié), simulation simple du déplacement (glissement jusqu'au mur) pour vérifier qu'un niveau est faisable.

## Contraintes techniques
- Sortie : un APK unique produit par `editor/build.sh` à partir de l'APK officiel (reproductible, une commande).
- Choix techniques libres (Java ou Kotlin, Views ou autre), **à condition** que ça s'intègre à l'APK Unity existant (voir CLAUDE.md §6 : pas de fusion de ressources Gradle, attention à AndroidX). Justifie le choix dans le README.
- minSdk 21, targetSdk 28, arm64 uniquement.
- Le jeu doit rester **strictement identique** quand aucun test n'est actif (fallback du patch = comportement d'origine).
- Format de stockage : garde la compatibilité avec le `.txt` de `totm_levels.py`. Si tu ajoutes des métadonnées (nom, auteur, groupes...), mets-les dans des lignes `#`.

## Méthode
1. Commence par un plan court et l'ordre des étapes ; signale à Sky les décisions structurantes (UI toolkit, mécanisme de lancement direct) avant d'y passer des heures.
2. Travaille par incréments livrables : chaque étape produit un APK installable. Utilise git, avec un commit par étape.
3. Tests automatisés à garder verts : aller-retour des 817 niveaux, logique des blocs et des paires, hook natif sur l'hôte. Ajoute-en pour chaque nouveau patch ou hook.
4. Rétro-ingénierie : appuie-toi sur `reference/` (dump, DummyDll dans dotPeek, `dis.py`). Documente chaque nouvelle découverte (RVA, offsets, comportement) dans `CLAUDE.md`.
5. Tests sur appareil : si `adb` voit un téléphone, installe et lis `logcat` toi-même. Sinon, donne à Sky une procédure de test courte et précise, et dis-lui quoi te renvoyer.
6. À la fin : README à jour (installation, utilisation, architecture, limites), et la liste de ce qui a été vérifié sur appareil et de ce qui ne l'a pas été.
