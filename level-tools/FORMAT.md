# Tomb of the Mask (Android 1.2.28) : format des niveaux

Analyse de `tomb-of-the-mask-1-2-28.apk` (Unity, IL2CPP arm64 + armv7, metadata v24.1).

## Où sont les niveaux

Pas dans les scènes : ce sont deux **TextAssets binaires** chargés par
`DataManager.LoadStages()` / `LoadSections()` via `Resources.Load(...)`.

| TextAsset  | Fichier dans l'APK                                   | Contenu |
|------------|------------------------------------------------------|---------|
| `stages`   | `assets/bin/Data/4d23eb85ba4b944738f79474d755842c`   | 300 niveaux du mode Stages (271 097 octets) |
| `sections` | `assets/bin/Data/5dd57769c40f044e9a47cacf70f2ae05`   | 517 morceaux du mode Arcade infini (54 815 octets) |

Bonus : les **sources Tiled des devs** sont aussi embarquées (TextAssets `1` à `300` et `0_0` à `14_26`,
format `.tmx` XML non compressé). Le jeu ne les lit pas, mais elles sont identiques au binaire (vérifié 300/300).

## Format binaire

Confirmé par désassemblage de `LoadStages` (`BinaryReader.ReadByte` x3 puis `ReadBytes(w*h)`).

```
stages   : répété 300 fois
  u8 lavaSpeed   0 = pas de lave montante ; sinon vitesse = lavaSpeed * 0.01 (GameController.GenerateStage)
  u8 width
  u8 height
  u8 tiles[width*height]

sections : répété 517 fois
  u8 SectionType (0 Begin, 1 Easy, 2 Spikes, 3 Bats, 4 Cannons, 5 Fishes, 6 Platforms,
                  7 Tramplins, 8 Hard, 9 Portals, 10 BonusEnter, 11 BonusRun, 12 Snake,
                  13 Tutorial, 14 BonusPipes)
  u8 cellCount   largeur fixe 13, hauteur = cellCount / 13
  u8 tiles[cellCount]
```

- Les lignes sont stockées **de bas en haut** (axe Y Unity vers le haut). Dans les `.txt` exportés, elles sont remises de haut en bas.
- Limite : largeur et hauteur max 255 pour un stage, 255 cases max pour une section (donc 19 lignes de 13).
- Un stage valide contient 1 `Enter`, 1 `Exit` et 3 `Star` (298 niveaux sur 300 ; 2 en ont 4).
- Valeurs inconnues de l'enum présentes dans quelques stages : 16, 17, 18, 25, 27, 163, 229 (rares, probablement ignorées ou décoratives).

## Valeurs des tuiles (enum `LevelTileType`)

| Val | Nom | Val | Nom | Val | Nom |
|---|---|---|---|---|---|
| 0 | None (vide) | 49-55 | PortalH1..7 | 80/92/104/116/128/140 | SnakeEnter1..6 |
| 1 | Enter (départ) | 61-67 | PortalV1..7 | 81/93/105/117/129/141 | SnakeExit1..6 |
| 2 | Exit | 56 | TramplinDR | 82/94/106/118/130/142 | SnakeTrigger1..6 |
| 3 | Wall | 57 | TramplinDL | 23/35/47/59/71/83 | LightBallTrigger1..6 |
| 4 | Star | 68 | TramplinUR | 24/36/48/60/72/84 | LightBall1..6 |
| 5 | WallEmpty | 69 | TramplinUL | 13/14/15 | MonkeysTrigger1..3 |
| 6 | BonusDoor | 73/74/85/97 | Cannon Left/Right/Up/Down | 109/110 | RotationCW/CCW |
| 7 | Ice | 76/77/88/89 | Bat Right/Left/Up/Down | 113/115 | BonusTeeth Right/Left |
| 8 | Spikes | 78/79/90/91 | Platform Right/Left/Up/Down | 114 | BonusWall |
| 12 | CloseBrick | 98 | MSpikes (pics mobiles) | 121 | TimerWall |
| 19 | Coin | 102 | Fish | 145-148 | Powerup CoinAddict/Freeze/Magnet/ScoreBoost |
| 31 | Dot | 43 | ZoneExit | 255 | Player |

Les portails vont par paires du même numéro (PortalH1 avec PortalH1...).

## Outil `totm_levels.py`

```bash
pip install UnityPy pillow

# 1. Extraire : .txt (grille de chiffres) + .tmx (Tiled) + .png pour chaque niveau, + tiles.png
python totm_levels.py extract tomb-of-the-mask-1-2-28.apk out

# 2. Éditer out/stages/stage_XXX.txt à la main, ou les .tmx dans Tiled
#    (tiles.png est un tileset généré : chaque tuile porte sa valeur et son nom)

# 3. Recompiler le binaire
python totm_levels.py build out/stages stages_mod.bytes            # depuis les .txt
python totm_levels.py build out/stages stages_mod.bytes --ext .tmx # depuis Tiled
python totm_levels.py build out/sections sections_mod.bytes --kind sections

# 4. Réinjecter dans l'APK, aligner, signer
python totm_levels.py patch tomb-of-the-mask-1-2-28.apk mod_unsigned.apk --stages stages_mod.bytes
zipalign -p -f 4 mod_unsigned.apk mod_aligned.apk
apksigner sign --ks debug.keystore --out totm_mod.apk mod_aligned.apk
```

L'ordre des fichiers dans le dossier = l'ordre des niveaux (tri alphabétique). Pour ajouter un niveau,
ajoute `stage_301.txt`. Le jeu lit le fichier jusqu'au bout, donc le niveau sera chargé en mémoire ;
reste à vérifier que l'écran de sélection l'affiche (non testé, `stagesCount` n'a pas été désassemblé).

## Vérifications faites

- Rebuild `.txt` et `.tmx` -> binaire identique octet pour octet à l'original (stages et sections).
- APK patché (niveau 1 : sortie déplacée à côté du départ) relu par UnityPy : seul le niveau 1 diffère, signature v1/v2/v3 OK.
- Aucune vérification de signature ou Play Integrity trouvée dans `classes.dex` ni dans le code IL2CPP du jeu.
- **Pas testé sur un téléphone.** Une signature différente impose de désinstaller la version Play Store
  (perte de la progression locale) et les achats in-app ne fonctionneront pas.

## Pistes pour l'éditeur "dans le moteur"

- `LevelLoader.AddTiles(SectionSettings)` construit un niveau à partir d'un `byte[]` : c'est le point d'entrée
  idéal à hooker (Frida ou lib native) pour charger un niveau depuis un fichier externe sans repackager à chaque fois.
- `DataManager.StageInfo(int)` (RVA 0x50E734) renvoie le `StageInfo` d'un stage : le hooker permet de remplacer
  n'importe quel niveau à la volée.
- `GameController.GenerateStage()` (RVA 0x5EF084) lance la génération.
