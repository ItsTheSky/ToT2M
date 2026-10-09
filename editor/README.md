# TotM Editor (prototype)

Éditeur de niveaux pour Tomb of the Mask 1.2.28 (Android arm64), intégré à l'APK du jeu.
Les niveaux créés sont joués **par le vrai moteur du jeu**.

## Installation

- `TotMEditor.apk` : package `com.happymagenta.fromcore.editor`, il s'installe à côté du jeu officiel.
- Deux icônes apparaissent : **TotM Mod** (le jeu) et **TotM Editor**.
- arm64 uniquement, Android 5 minimum.

## Utilisation

1. Ouvre **TotM Editor**. En haut, une ligne indique l'état du hook natif.
2. **+ Nouveau** crée un niveau 20×30 avec un couloir, un départ et une sortie. Tu peux aussi :
   - **Depuis un officiel** : copier un des 300 niveaux du jeu comme base ;
   - **Coller** : importer un niveau copié en texte.
3. Dans l'éditeur :
   - un doigt peint avec l'outil choisi ; deux doigts déplacent et zooment ;
   - outils : Mur, Vide, Point, Pics, Étoile, Départ, Sortie. Départ et sortie sont uniques : en poser un nouveau déplace l'ancien ;
   - **Annuler** et **Cadrer** sont dans la barre du haut ;
   - le menu **⋮** contient : Taille et lave, Remplir les vides de points, Fermer les bords, Renommer, Exporter, Statut du hook.
4. **▶ Tester** vérifie le niveau (1 départ, 1 sortie, avertissement si le bord est ouvert), le sauvegarde et ouvre le jeu.
   **Lance n'importe quel niveau du mode Stages** : c'est le tien qui est généré.
5. Reviens dans TotM Editor : le test est désactivé automatiquement et le jeu redevient normal.

Les niveaux sont sauvegardés automatiquement, au format texte de `totm_levels.py` (`files/levels/*.txt`).
L'export et l'import passent par ce texte : tu peux éditer sur PC puis coller sur le téléphone, et inversement.

## Fonctionnement

```
TotM Editor (Java)                         Jeu (Unity IL2CPP), même processus
  Tester -> files/test_level.bin   ----->  GameController.GenerateStage()
            [lava][w][h][tuiles]              -> DataManager.StageInfo(index)   (patché)
                                                 -> slot != 0 ? hook_StageInfo (libtotmeditor.so)
                                                       -> test_level.bin présent ? niveau de test
                                                       -> sinon stages[index] (comportement d'origine)
```

- **Patch statique** (`tools/patch_il2cpp.py`) : `StageInfo` (RVA 0x50E734) est réécrite en 7 instructions.
  Elles lisent un pointeur dans 16 octets ajoutés à la fin du `.bss`, puis sautent vers le hook s'il est défini.
  Sinon, elles appellent `List<StageInfo>.get_Item` comme l'original. Sans la lib native, le jeu tourne normalement.
- **Lib native** (`native/totmeditor.cpp`) : elle est chargée par `EditorApp` au démarrage du processus.
  Elle attend `libil2cpp.so`, vérifie le patch et écrit l'adresse du hook dans le slot.
  Aucune page de code n'est modifiée à l'exécution, donc pas de souci SELinux (`execmod`).
- Le hook crée le `byte[]` managé avec `il2cpp_array_new_specific`, en reprenant la classe `byte[]` d'un niveau officiel.

## Vérifié / non vérifié

Vérifié ici :
- Le patch au désassemblage.
- L'APK : manifeste, dex, libs, signature v1/v2/v3.
- La logique `Level` sur les 300 niveaux officiels : binaire identique octet pour octet, aller-retour texte, compatibilité avec `totm_levels.py`.
- Le hook natif sur PC, avec une fausse disposition mémoire IL2CPP. Il couvre l'original, l'injection, le fichier corrompu et l'index hors limites.

**Non testé sur un téléphone** (pas d'émulateur ARM ici). Si quelque chose ne marche pas, envoie la sortie de :
```
adb logcat -s TotMEditor AndroidRuntime Unity
```
et le texte de **⋮ > Statut du hook**.

Limites connues :
- Le stage lancé pendant un test enregistre sa progression (étoiles, déblocage) comme si tu avais joué le vrai stage.
- Seuls 7 outils sont proposés. Les autres tuiles d'un niveau officiel copié (chauves-souris, canons...) sont conservées et affichées en orange avec leur numéro.

## Reconstruire

```
./build.sh tomb-of-the-mask-1-2-28.apk TotMEditor.apk
```
Prérequis : NDK r26, R8/D8 (`r8.jar`), `android.jar` (API 34), apktool ≥ 2.7 avec aapt2, zipalign, apksigner, Python 3 + UnityPy.
Les chemins se règlent par variables d'environnement en tête de `build.sh`.
