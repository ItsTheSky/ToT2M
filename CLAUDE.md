# TotM Editor : base de connaissances du projet

Projet de Sky : un éditeur de niveaux Android pour **Tomb of the Mask 1.2.28** (Playgendary / Happymagenta).
L'éditeur est intégré à l'APK du jeu, et les niveaux sont joués par **le vrai moteur du jeu**.
Usage strictement perso : on ne redistribue jamais d'APK modifié.

Tout ce qui suit a été vérifié par désassemblage ou par test, sauf mention « à vérifier ».
Langue du projet : français (UI, docs, commits).
Préférence de Sky : jamais de tiret cadratin dans les textes ; utiliser « ; », « - » ou une virgule.

---

## 1. Contenu du pack

```
CLAUDE.md                ce fichier (connaissances)
PROMPT.md                la mission à réaliser
input/                   mettre ici tomb-of-the-mask-1-2-28.apk (non fourni)
editor/                  prototype FONCTIONNEL actuel (Java + C++ + scripts de build), testé OK par Sky sur téléphone
  build.sh               pipeline complet APK officiel -> APK éditeur signé
  java/…/totmeditor/     app éditeur (Views Android en code, sans XML)
  native/totmeditor.cpp  hook natif (libtotmeditor.so)
  tools/                 patch_il2cpp.py, edit_manifest.py, extract_stages.py
  test/                  LevelTest.java (JVM), hook_test.cpp (hôte, fausse mémoire IL2CPP)
level-tools/             totm_levels.py (CLI PC : extract/render/build/patch) + FORMAT.md
reference/
  il2cpp/                sortie Il2CppDumper : dump.cs, script.json, stringliteral.json, il2cpp.h, DummyDll/ (ouvrable dans dotPeek/dnSpy)
  notes/                 désassemblages ARM64 commentables des fonctions clés
  levels/                stages.bytes, sections.bytes, 300 stages + 517 sections en .txt, aperçu PNG, quelques TextAssets
  dev_tmx/               les 817 cartes Tiled d'origine des devs (embarquées dans l'APK, inutilisées par le jeu) + tiles.png (leur tileset)
  assets/                1136 sprites + 78 textures extraits (PNG) + index.json (nom, fichier, atlas, pixelsToUnits)
  scripts/               scripts d'analyse (UnityPy, capstone) : dis.py = désassembleur avec noms de méthodes
```

## 2. Le jeu

- `com.happymagenta.fromcore`, version 1.2.28 (versionCode 29), Unity **2018.3.8f1**, **IL2CPP** (metadata v24.1), arm64-v8a + armeabi-v7a.
- minSdk 21, **targetSdk 28** (à garder ≤ 28).
- Une seule activité : `com.unity3d.player.UnityPlayerActivity`, en `singleTask` et portrait. Pas de classe Application custom, pas de provider.
- Aucune vérification de signature ni Play Integrity trouvée (ni dans `classes.dex` ni dans le code IL2CPP). Les achats in-app ne marchent pas sur un APK re-signé.
- Scènes : `level0`, `level1`, `level2` dans `assets/bin/Data` (à identifier : probablement préchargement, menu, jeu).
- Les sprites de jeu sont **blancs** et teintés à l'exécution par des paires de couleurs. Voir `GameController.ResetActiveColorPair`, `UpdateWallColor`, `UpdateDotColor`, `UpdateEnemyColor`, `UpdateLavaColor`.

## 3. Format des niveaux

Ce sont des TextAssets chargés par `Resources.Load` (chemins `Maps/stages` et `Maps/sections`). Ils sont lus avec un `BinaryReader` dans `DataManager.LoadStages` / `LoadSections`.

```
stages   (300 niveaux du mode Stages) : répété
  u8 lavaSpeed   0 = pas de lave (l'objet lave est désactivé) ; sinon vitesse = lavaSpeed * 0.01f
  u8 width, u8 height
  u8 tiles[width*height]   lignes stockées de BAS en HAUT

sections (517 morceaux du mode Arcade) : répété
  u8 SectionType (0 Begin, 1 Easy, 2 Spikes, 3 Bats, 4 Cannons, 5 Fishes, 6 Platforms, 7 Tramplins,
                  8 Hard, 9 Portals, 10 BonusEnter, 11 BonusRun, 12 Snake, 13 Tutorial, 14 BonusPipes)
  u8 cellCount   largeur 13 observée sur les 517 ; hauteur = cellCount/13
  u8 tiles[cellCount]
```

- Fichiers dans l'APK : `stages` = `assets/bin/Data/4d23eb85ba4b944738f79474d755842c`, `sections` = `…/5dd57769c40f044e9a47cacf70f2ae05`.
- Les chaînes `Editor/Stages/`, `Assets/_TotM/Resources/Maps/stages.bytes` et `loaded stages count:` montrent que les devs « bakaient » les TMX Tiled en binaire. Une branche XML (`FileStream` + `XmlDocument`) existe dans `LoadSections`, probablement du code éditeur Unity mort (à vérifier).
- Statistiques sur les 300 stages : 1 Enter et 1 Exit chacun ; 3 Star (298) ou 4 (2) ; tailles de 13 à 67 de large et de 4 à 83 de haut ; 26 ont des cases non-mur au bord.
- Dans le mode Stages, les « points » à ramasser sont la valeur **19 (Coin)**. La valeur 31 (Dot) n'apparaît que dans les sections Arcade.

### Valeurs de tuile : enum `LevelTileType` (complet)

| Val | Nom | Val | Nom | Val | Nom |
|---|---|---|---|---|---|
| 0 | None | 23/35/47/59/71/83 | LightBallTrigger1..6 | 80/92/104/116/128/140 | SnakeEnter1..6 |
| 1 | Enter | 24/36/48/60/72/84 | LightBall1..6 | 81/93/105/117/129/141 | SnakeExit1..6 |
| 2 | Exit | 49..55 | PortalH1..7 | 82/94/106/118/130/142 | SnakeTrigger1..6 |
| 3 | Wall | 61..67 | PortalV1..7 | 109 / 110 | RotationCW / RotationCCW |
| 4 | Star | 56 / 57 | TramplinDR / TramplinDL | 113 / 115 | BonusTeethRight / Left |
| 5 | WallEmpty | 68 / 69 | TramplinUR / TramplinUL | 114 | BonusWall |
| 6 | BonusDoor | 73 / 74 / 85 / 97 | Cannon Left / Right / Up / Down | 121 | TimerWall |
| 7 | Ice | 76 / 77 / 88 / 89 | Bat Right / Left / Up / Down | 145..148 | Powerup CoinAddict / Freeze / Magnet / ScoreBoost |
| 8 | Spikes (orientation auto) | 78 / 79 / 90 / 91 | Platform Right / Left / Up / Down | 255 | Player |
| 12 | CloseBrick | 98 | MSpikes (pics mobiles) | 13 / 14 / 15 | MonkeysTrigger1..3 |
| 19 | Coin (= points du mode Stages) | 102 | Fish | 31 | Dot (Arcade) |
| 43 | ZoneExit | | | | |

- Valeurs **hors enum** présentes dans quelques stages : 16 (28×), 17 (77×), 18, 25 (6×), 27, 163 (7×), 229. Il faut les conserver telles quelles et chercher leur rôle (switch de `LevelLoader.AddOthersTiles` ou `CalculateNeighboursForIndex`).
- Les numéros de portails, snakes, light balls et monkeys forment des **groupes appariés** : PortalH1 avec PortalH1, SnakeEnter2 + SnakeExit2 + SnakeTrigger2, LightBall3 + LightBallTrigger3, etc.
- Groupes orientables à fusionner en un bloc logique + rotation : Cannon, Bat, Platform, Tramplin (diagonales), BonusTeeth, Rotation (sens), Portal (H/V).
- `reference/dev_tmx/tiles.png` est le **tileset Tiled des devs**. Une tuile de valeur `v` est à l'index `v-1`, sur une grille de 12 colonnes × 96 px, avec des libellés lisibles (Ent, Exi, Str, portails par couleur...). C'est parfait pour des icônes d'éditeur.

## 4. Carte du code (RVA dans `libil2cpp.so` arm64 ; offset fichier = VA pour le 1er segment)

| Élément | Détail |
|---|---|
| `DataManager` (HMSingleton) | `stages` : `List<StageInfo>` @+0x20 ; `sections` : `List<byte[]>[]` @+0x18 ; props `activeStage`, `runNextStage`, `animateNextStage`, `lastUnlockedStage`, `stagesStars` |
| `DataManager.StageInfo(int)` | **0x50E734**, seul appelant : `GameController.GenerateStage` (0x5EF1E0). Original : `return stages.get_Item(i)` |
| `DataManager.LoadStages` / `LoadSections` | 0x50AE4C / 0x509D2C |
| `struct StageInfo` (0x18) | `int width`@0, `int height`@4, `byte[] tiles`@8, `byte lavaSpeed`@0x10. Retour > 16 octets, donc via **x8** |
| `List<StageInfo>.get_Item` | 0xCA1EE4 (n'utilise pas x2) ; `List._items`@0x10, `_size`@0x18 ; données d'un tableau IL2CPP @+0x20 |
| `GameController` | `GenerateStage` 0x5EF084, `StartGame` 0x5E9320, `InitWithLevelIndex(int)`, `ProcessStageCompleted`, `StageCompleted`, `ProcessPlayerDeath`, `SaveGameResults`, `SaveGame`, `ActivateLava` |
| `LevelLoader` | `AddTiles(SectionSettings)` 0x546D68 ; prefabs par type en champs (`batPrefab`@0x80, `cannonPrefab`@0x88, `spikesPrefab`@0x40…) ; `AddWalls/AddPortals/AddPlatforms/AddLightBalls/AddOthersTiles/ProcessMonkeysAndSnakes` |
| `struct SectionSettings` | `byte[] tiles`@0, `short wallsIndex`@8, `bool inverted`@0xA, `removeEnemies`@0xB, `byte width`@0xC, `height`@0xD, `bonusSection`@0xE |
| Lancement d'un stage (pistes) | `GameOverController.BtnPlayLevelPressed(int stageIndex)` 0x5FF098 ; `StageInfoController.BtnPlayPressed` 0x998B1C ; `GameStateController.ShowStageInfo` 0x53C894 / `RestartGame` 0x5401B0 |
| Exports IL2CPP utiles | `il2cpp_array_new_specific`, `il2cpp_class_from_name`, `il2cpp_runtime_invoke`, `il2cpp_thread_attach`, `il2cpp_gchandle_new`, `il2cpp_domain_get`… (201 exportés) |

Outils : `python3 reference/scripts/dis.py <libil2cpp.so> <rva_hex> <n_instr> reference/il2cpp/script.json` désassemble avec les noms des cibles `bl`. Ouvrir `reference/il2cpp/DummyDll/Assembly-CSharp.dll` dans dotPeek donne les signatures, sans les corps.

## 5. Architecture du prototype (à conserver dans l'esprit)

1. **Patch statique** de `libil2cpp.so` (`editor/tools/patch_il2cpp.py`). `StageInfo` @0x50E734 devient :
   `adrp x16, SLOT ; ldr x16,[x16,lo] ; cbz x16, fb ; br x16 ; fb: ldr x0,[x0,#0x20] ; mov x2,xzr ; b get_Item`.
   `SLOT` = 0x1343A50 : 16 octets ajoutés en fin de `.bss` (p_memsz du segment RW + 0x10 ; pas de GNU_RELRO).
2. **Lib native** chargée par `EditorApp.onCreate`. Elle attend `libil2cpp.so` (`dlopen` NOLOAD), vérifie le patch et écrit `&hook_StageInfo` dans SLOT.
   **Aucune page de code n'est modifiée à l'exécution**, ce qui évite le refus SELinux `execmod`. C'est volontaire, à garder.
3. `hook_StageInfo` réimplémente l'original. Si `<filesDir>/test_level.bin` existe (même format qu'une entrée `stages`), il crée un `byte[]` via `il2cpp_array_new_specific` (classe reprise d'un niveau officiel) et renvoie le niveau de test.
4. L'éditeur (même processus, même `filesDir`) écrit `test_level.bin` puis lance le jeu. Le fichier est supprimé à `onResume` de l'éditeur et au démarrage du processus.
5. Manifeste : `application android:name=EditorApp`, activités éditeur avec `taskAffinity="com.sky.totmeditor"`, package renommé `com.happymagenta.fromcore.editor` (via `renameManifestPackage` d'apktool), `app_name` = "TotM Mod".
6. Limites actuelles : il faut lancer un stage à la main ; la progression du stage remplacé est enregistrée ; seulement 7 outils.

## 6. Build : pièges déjà rencontrés

- **NDK r26d** (`aarch64-linux-android21-clang++`, `-static-libstdc++`, `-Wl,-z,max-page-size=16384`).
- **D8 de build-tools 34 plante** sous JDK 21 (NPE). Utiliser **R8 8.5.35** (`com.android.tools:r8` sur maven.google.com) : `java -cp r8.jar com.android.tools.r8.D8 --min-api 21 --lib android.jar`.
- **apktool + aapt (v1) plante** au rebuild. Utiliser `apktool b --use-aapt2`. Décompiler avec `-s` pour garder `classes.dex` intact ; notre code va dans `classes2.dex`.
- Supprimer `lib/armeabi-v7a` (la lib éditeur n'existe qu'en arm64 ; ça réduit aussi l'APK à ~26 Mo).
- `zipalign -p -f 4`, puis `apksigner` (v1+v2+v3). La keystore de debug est générée si absente.
- Si on ajoute des ressources Android (XML, drawables, polices), elles doivent passer par le dossier `res/` décodé par apktool : il n'y a pas de fusion Gradle. Les bibliothèques AndroidX avec ressources (Compose, Material) sont donc coûteuses à intégrer. Évaluer avant de choisir.
- Unity possède l'Activity du jeu et sa tâche : lancer le jeu avec `ACTION_MAIN` + `CATEGORY_LAUNCHER` et `NEW_TASK | RESET_TASK_IF_NEEDED`.

## 7. Tests disponibles

- `editor/test/LevelTest.java` : les 300 stages font un aller-retour binaire → modèle → binaire identique à l'octet, et texte → binaire aussi.
- `editor/test/hook_test.cpp` : hook natif testé sur l'hôte avec une fausse disposition mémoire IL2CPP (cas original, injection, fichier corrompu, index hors limites).
- Sur appareil : `adb install -r`, puis `adb logcat -s TotMEditor AndroidRuntime Unity`. Le menu ⋮ > Statut du hook affiche l'état natif.
