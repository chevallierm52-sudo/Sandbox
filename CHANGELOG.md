# Changelog

## [Phase 37 - Système de métiers complet + alignement packets combat sur AncestraR] - 2026-05-15

**Gros pavé sur deux fronts** : implémentation complète du système de métiers (protocole officiel Dofus 1.29) et corrections en cascade des packets combat à partir de logs Ancestra capturés.

### A. Système de métiers (protocole 1.29 officiel)

Suppression du parser maison `M*` (`ML/MC/Mx/MK`) — un client 1.29.1 vanilla ne l'utilise jamais. Tout passe désormais par le vrai protocole.

**Modèle & registre** :
- `Job(id, name, canCraft, MAX_LEVEL=100)` — métier statique.
- `JobProgress(jobId, level, xp)` — état perso, calcule `xpMin/xpMax` depuis `experiences_template.job`, `addXp(amount)` gère le levelup automatique en chaîne.
- `JobsRegistry` — 16 métiers : Base (id 0, non-craft) + Alchimiste, Paysan, Bûcheron, Mineur, Pêcheur, Chasseur, Boulanger, Cordonnier, Bijoutier, Bricoleur, Sculpteur, Tailleur, Forgeron, Forgeur de boucliers, Sculpteur de bâtons.
- `JobSkills` — table `skillId → jobId` (45/53/57/... = Paysan, 6/10/33/... = Bûcheron, 24/25/26/... = Mineur, etc.) + formule XP par récolte `max(1, respawnMs / 60_000)`.

**Persistance** :
- Table SQL `character_jobs(character_id, job_id, level, xp)` + PK composite + index sur owner. `sql/character_jobs.sql` à exécuter.
- `CharacterJobsData` : `loadFor()`, `save()`, `saveAll()` (upsert batch), `delete()`. Le métier Base (id 0) n'est jamais persisté (toujours implicite).
- `Characters.jobs` (LinkedHashMap) + `addJob()`/`knowsJob()`/`getJob()` ; load via `CharactersData.load`, save via `CharactersData.update` (batch automatique).

**Packets officiels** :
- `JS<jobId>;<canCraft>;<level>,<xp>,<xpMin>,<xpMax>;|...` — envoyé au login après `AS` (`RolePlayHandler` constructor) et après chaque apprentissage. Le métier Base apparaît toujours en tête avec le niveau du personnage.
- `JX<jobId>~<xp>~<xpMin>~<xpMax>` — diffusé à chaque gain d'XP simple ; remplacé par un `JS` complet sur levelup (le client recompute le niveau de son côté).
- `JN<jobId>` — notification d'apprentissage.

**Apprentissage gratuit via PNJ** :
- `NpcReply.Action.LEARN_JOB` ajouté à l'enum + `getJobIdToLearn()` (parse `params`).
- `DialogParser.reply` route `LEARN_JOB` vers `JobParser.learn(character, session, jobId)` qui ajoute `JobProgress(jobId, 1, 0)`, persiste, envoie `JN` + `JS`. Insertion BDD type : `INSERT INTO npc_replies(id, text, action, params) VALUES (9001, 'Devenir Mineur', 'LEARN_JOB', '4');`

**XP de récolte** :
- Hook dans `InteractiveObjectService.grantReward(character, session, reward, skillId, respawnMs)` : si `JobSkills.jobIdFor(skillId) > 0` et le perso connaît le métier, on calcule `xp = xpForGather(respawnMs) × quantité` puis `JobParser.addXp()`.
- Sans métier appris → ressource récoltée mais aucune XP gagnée (logique 1.29 : impossible de farmer un métier qu'on n'a pas appris).

**Craft via protocole officiel `E*`** :
- `CraftRecipesData` — extrait le chargement de recettes (`craft_recipes + craft_ingredients`) hors de l'ancien CraftParser, ajoute un index `byJob` et `findMatching(jobId, ingredients)` pour la validation côté serveur sur `EK1`.
- `CraftSession(characterId, jobId, slotted)` — session serveur d'une fenêtre d'atelier ouverte. `ACTIVE` map statique (character_id → session, max une par perso). Les ingrédients sont **réservés** dans la grille sans modifier la BDD, consommés uniquement à la validation.
- `CraftExchange` (nouveau parser) — handlers `Ec2|<jobId>` (ouverture), `EMG<uid>|<qty>` (Move Get = poser ingrédient), `EMD<uid>|<qty>` (Move Drop = retirer), `EK1` (Kommit = lancer le craft), `EV` (close). Vérifications : métier connu, niveau ≥ recette, ingrédients suffisants. Production : retire les ingrédients via `inv.removeItem` + `OR`/`OQ` packets, crée le résultat via `inv.addItem` + `OA` packet, XP via `JobParser.addXp` (`baseXp = level_requis × 5`, diminué par l'écart de niveau actuel).
- `ExchangeParser.parse` dispatch `case 'c'` → `CraftExchange.open`, `case 'M'` → `move`, `case 'K'`/`'V'` → handlers qui choisissent entre `CraftExchange` et le trade J-J selon `CraftSession.isOpen(charId)`.

**Commande admin** :
- `.job <jobId> <level>` — apprend si nécessaire puis fixe le niveau (et l'XP au seuil correspondant). Pratique pour les tests : `.job 4 50` → Mineur niveau 50.

**Fichiers supprimés** :
- `CraftParser.java` (protocole `M*` maison). Imports/routes retirés de `RolePlayHandler` et `Initialisation`.

### B. Combat : alignement complet sur les logs Ancestra

L'utilisateur a fourni des logs serveur AncestraR-R d'un combat gagné + d'un combat abandonné. Diff ligne par ligne contre `packets.log` Sandbox a révélé une douzaine d'écarts qui expliquaient pratiquement tous les symptômes (sprites mob invisibles, animations qui ne jouent pas, sort bloqué après mouvement, panneau résultat invisible, désync PM/PA).

**Format `GM` mob — case 2 d'AncestraR `Fighter.getGmPacket`** :
- Format exact : `cell;1;0;id;templateId;-2;gfx^100;grade;c1;c2;c3;0,0,0,0;maxPdv;PA;PM;team`.
- Sandbox envoyait depuis Phase 36 du `-3` (groupe roleplay) ou un `-2` mal formé. Restauré à l'identique de la référence : dir hardcodé à `1` (pas l'orientation réelle), champ 8 = grade index (1-5) et **pas** le level réel, pas de résistances séparées (juste maxPdv/PA/PM/team).
- `Fighter.mobGrade` ajouté (champ + getter/setter) ; `FightParser.buildMonsterFighter` appelle `setMobGrade(entry.getGrade())`.

**IDs mob négatifs** :
- `FightParser.initiateFightVsMonsters` : `monsterFightId = -1; ... monsterFightId--` au lieu de l'ancien `1_000_000 + group.id × 10 + index` (positif → le client traitait l'acteur comme un joueur invisible).

**Enum `FighterType` réaligné sur le switch `_type` AncestraR** :
- `PLAYER(1)`, `MONSTER(2)`, `PERCEPTOR(5)`, `BOT(99)` — champ `id` explicite, plus de dépendance à `.ordinal()` dans le code de Fight (aucun appel `.ordinal()` n'existait). `PERCEPTOR(5)` ajouté en prévision des collecteurs de guilde (case 5 du `getGmPacket`).

**Packets d'action — format AncestraR exact** :
- `GA<gaId>;<type>;<actor>;<args>` uniquement pour l'action **principale** (`GA;1` mouvement, `GA;300` sort). `gaId` global incrémenté via `AtomicInteger Fight.GA_ID` + helpers `nextGaId()` / `nextGaPacketId()` (public).
- `GA;<type>;<actor>;<args>` (SANS gaId) pour tous les **follow-up** : `GA;102` perte PA, `GA;129` perte PM, `GA;100` dégâts, `GA;108` soin, `GA;103` mort, `GA;303` melee. Cet écart filtrait des packets côté client.
- Deltas en **négatif** pour les pertes (`,-N`), positif pour les gains (`,+N`). Le client fait `currentX += delta` et soustrait si négatif. (Mes essais antérieurs en positif sur les pertes désynchronisaient le HUD : « plus possible de bouger » alors qu'il restait des PM serveur.)
- `GA;300` à **7 args** : `spellId,targetCell,spellSprite,spellLevel,X,Y,Z` (les 3 derniers : `0,0,1` neutres). Précédemment 4 args + un `,targetCell,-1,1` custom qui faisait ignorer le packet par le client → pas d'animation de sort.
- `GA;103;<caster>;<target>` simple (mort). Suppression du `GA;402` custom qui n'existe pas chez AncestraR.

**`GAS<actor>` — Game Action Start** :
- Ajouté avant chaque `GA;1` (move) et `GA;300` (spell) pour les joueurs ET pour les mobs. Sans `GAS`, le client 1.29.1 ne passe pas en mode animation et téléporte le sprite à la destination au lieu de jouer le walk cycle. AncestraR commente *« les monstres n'ont pas de GAS/GAF »* mais l'expérimentation sur Sandbox montre que le client _exige_ `GAS<mob>` — donc on l'envoie pour move et melee mob via `MonsterAI`.

**`GAF<type>|<actor>` — Game Action Finished** :
- Marqueur constant par type d'action (constantes `GAF_SPELL=0`, `GAF_MELEE=1`, `GAF_MOVE=2`). Envoyé après chaque action joueur pour signaler au client que le traitement serveur est terminé → le client peut alors envoyer son `GKK<type>` et passer à la suite. Sans GAF, le client restait bloqué en attendant la confirmation, refusait les sorts suivants.

**`GTR<actor>` — Game Turn Ready** :
- Ajouté dans `FightTurn.endTurn()` : séquence finale du tour devient `GTF` → `GTM` → `GTR`. Sans GTR le client ne fermait pas l'UI de tour côté HUD.

**Pas de `GIC` après mouvement** :
- `Fight.handleMove` et `Fight.sendMovementEnd` n'appellent plus `syncFightState()`. Le GIC envoyé pendant l'animation TPait le sprite à la destination et cassait le walk cycle. Le GTM final arrive plus tard via `endTurn`.

**Délai d'animation calé sur le client** :
- Mesure dans le packets.log : intervalle `GA;1` → `GKK1` du client = ~650-1300ms par case. Sandbox utilisait 300ms/case ce qui faisait arriver le GA;129/GIC pendant l'animation.
- `Fight.handleMove` : `animMs = Math.max(900L, steps * 700L)`.
- `MonsterAI` : `MOVE_STEP_DURATION_MS = 700L`, `MOVE_END_PADDING_MS = 500L`, `ATTACK_ANIMATION_MS = 900L`, `SPELL_ANIMATION_MS = 700L`.

**Cellule d'origine mémorisée** :
- `Fighter.originalCell` initialisé à la création du Fighter (cellule roleplay avant placement combat), pas modifié par `setCell()`. `restorePlayersAfterFight` utilise `f.getOriginalCell()` au lieu de `f.getCell()` pour ramener le joueur à sa case roleplay (gagnant ou mort) — sans ça il réapparaissait sur une case de placement parfois hors zone visible.

**Refonte `GE` (panneau résultat) alignée sur `Fight.GetGE` AncestraR** :
- Header : `GE<time>;<starBonus>|<initiator>|<type>|...` avec `time` calculé depuis `startedAt` (nouveau champ initialisé dans `startActive`, _pas_ depuis `createdAt` qui inclurait le placement) et `type = 0` (CHALLENGE) en PvM — pas `4` comme avant.
- Winners triés par prospection desc (cohérent avec la distribution drops). Mobs gagnants désormais inclus dans la liste (auparavant filtrés → le client refusait d'afficher un panneau avec une équipe vide).
- Entrée winner : 13 champs `2;guid;name;level;dead;min;cur;max;winXp;guildXp;mountXp;drops;kamas` — `guildXp`/`mountXp` laissés vides (TODO), valeurs nulles → `""` (convention AncestraR).
- Entrée loser : 12 champs `0;guid;name;level;dead;min;cur;max;;;;` (4 vides trailing).
- Trailing `|` conservé (Ancestra) — le retirer faisait sauter la dernière entrée côté client.

**Plus de `GV` après `GE`** :
- Sandbox envoyait `broadcast("GE..."); broadcast("GV")` — le `GV` fermait l'UI combat _immédiatement_, le panneau résultat n'avait pas le temps de s'afficher. Vérifié dans les logs Ancestra : **`GV` n'est jamais envoyé** après un combat actif. Sandbox aligné : `GE` → `fC0` → `restorePlayersAfterFight()` → `ILS1000` (signal de unlock, le client peut alors fermer le panneau quand l'utilisateur clique).
- `FightParser.leaveFight` (abandon) : suppression du `session.write("GI")` parasite (GI est un packet client→serveur, le renvoyer pollue le flux).

**Why** : les bugs combat depuis Phase 28 (sprites invisibles, anim manquantes, sort bloqué, panneau résultat absent, désync PM/HUD) viennent **tous** d'écarts mineurs accumulés sur les packets exacts envoyés à un client 1.29.1 vanilla. Les logs Ancestra capturés en parallèle d'un combat équivalent ont servi d'oracle ligne par ligne pour corriger.

### Correctifs supplémentaires fin de phase (après nouveaux logs)

Comparaison d'un 2e log Sandbox post-fixes avec les logs Ancestra a révélé 2 derniers écarts critiques.

**Retour en arrière sur `GAS<mob>`** :
- L'ajout précédent de `GAS<mobId>` dans `MonsterAI` (move + melee) **cassait** l'animation au lieu de la fixer. AncestraR commente explicitement *« les monstres n'ont pas de GAS/GAF »* et n'envoie aucun `GAS` pour les acteurs négatifs.
- Hypothèse : le client 1.29.1 reçoit `GAS<actorNégatif>` et passe en état d'attente d'un GKK qui ne viendra jamais (pas de client mob), bloquant ainsi toute la chaîne d'animation.
- Code retiré : plus de `fight.broadcast("GAS" + monster.getId())` dans `executeMoveThenMaybeAttack` ni `executeMeleeThenEnd`. Le `GAS` reste pour les joueurs uniquement (logique).

**Réorganisation handleSpell — ordre Ancestra exact + suppression du délai** :
- Ancestra envoie tous les packets d'un sort **d'affilée** sans délai côté serveur : `GAS → GA;300 → GA;100 (dégâts) → GA;103 (mort éventuelle) → GA;102 (perte PA) → GAF0`. Le client met les animations en queue côté UI et les enchaîne lui-même.
- Sandbox utilisait un `FightTurn.schedule(..., 700ms)` entre `GA;300` et `GA;100` : les dégâts apparaissaient au milieu de l'animation du sort, brisant l'affichage visuel. Le `GA;102` (perte PA) était aussi envoyé **avant** les dégâts au lieu d'après.
- Suppression du `schedule` : `applyEffect()` est appelé en ligne juste après `GA;300`, puis `GA;102`, puis `GAF0`. Le `checkWinCondition()` reste à la fin pour déclencher `endFight()` si applicable.

**`directionChar` mob reverse-engineered depuis les paths joueur** :
- Le mapping initial `delta +14 → 'f'` était faux (la mauvaise direction visuelle iso), puis mon premier fix `delta +14 → 'b'` aussi faux. Le mob continuait à ne pas s'animer car le client refusait probablement de jouer une marche dans la direction inverse du mouvement réel.
- Analyse empirique des paths du joueur dans le log (le client envoie ses propres dir chars cohérents avec sa logique iso) :
  - `adihcS` : delta -28 = 2 × -14 avec dir `h` → **delta -14 = `h`** (NORD-EST visuel)
  - `acSddibdx` : step1 delta +28 = 2 × +14 avec dir `d` → **delta +14 = `d`** (SUD-OUEST visuel)
  - `beedes` : step1 delta +45 = 3 × +15 avec dir `b` → **delta +15 = `b`** (SUD-EST visuel)
- En iso Dofus, +1 (même row, col+1) et +14 (next row, même col) sont visuellement la **même** direction (DOWN-LEFT), d'où le même char `d` pour les deux deltas. Idem pour -1 et -14 → `h`.
- Table corrigée appliquée à [MonsterAI.directionChar](src/org/dofus/game/fight/MonsterAI.java:188).

**Orientation mise à jour après mouvement** :
- Ajout d'un champ `Fighter.originalCell` ← déjà fait dans la passe précédente ; nouveau : mise à jour de `Fighter.orientation` à la fin du mouvement avec la direction du dernier step (via `EOrientation.valueOf(HASH.indexOf(lastDirChar))`). Sans ça le sprite gardait son orientation de placement initial tour après tour.
- `Fight.handleMove` : décode le 3e dernier char de `effectivePath` (dir du dernier step) → met à jour l'orientation du joueur.
- `MonsterAI.MonsterMove` : nouveau champ `finalDirChar` calculé dans `buildMoveToward`. `executeMoveThenMaybeAttack` met à jour l'orientation du mob via ce champ.

**Restore map view différé jusqu'au GC1 client (panneau résultat propre)** :
- L'utilisateur voyait "les cellules des joueurs présents sur la map" pendant l'affichage du panneau résultat, parce que `restorePlayersAfterFight` envoyait IMMÉDIATEMENT à la fin du combat un `GM` complet de la map (tous les acteurs + groupes mob) au joueur — qui apparaissait en transparence derrière le panneau.
- Comportement Ancestra exact (vérifié dans les logs) : après le `GE` + `fC0` + `ILS1000`, le serveur **n'envoie plus rien** au joueur jusqu'à ce qu'il clique Fermer (envoie `GKK0`) puis `GC1` (game creation), qui déclenche le map reload via `GameParser.creation`.
- Refactor [Fight.restorePlayersAfterFight](src/org/dofus/game/fight/Fight.java:691) : restauration serveur-side uniquement (cell roleplay, life, `map.addActor`, broadcast `GM|+player` aux AUTRES acteurs de la map). Le map view au joueur lui-même est laissé à la prochaine `GameParser.creation` quand le client envoie `GC1` après avoir fermé le panneau.

**Reste à faire** :
- `GKK<gaId>` acknowledgment côté serveur (au lieu de delays fixes) pour un timing parfait — discuté avec l'utilisateur, repoussé à plus tard.
- `OAKO<uidBase36>~<tpl>~<qty>~~;` format Ancestra pour ajouter les drops à l'inventaire (notre `OAKO*O;<entry>` est custom et peut ne pas afficher les items dans le panneau résultat).
- `As<stats>` refresh stats avant le `GE` (Ancestra l'envoie après les OAKO drops, avant le panneau).
- Téléportation post-mort vers map phénix (au lieu de remise sur la cellule d'origine).
- Investigation du « chapeau qui disparaît après combat » — soupçon de bug d'affichage de `buildAccessories` dans le GM post-fight, à confirmer avec un log post-combat complet (les logs reçus s'arrêtent au tour 5).
- Drops vides dans le panneau résultat : `DropTable.loadDefaults()` ne couvre que 3 templates (Tofu, Larve Blanche, Bouftou-id-5). Les templates Bouftou variants (489, 491) et autres mobs n'ont aucun drop chargé. À compléter avec une table SQL `monster_drops` ou enrichir les defaults.



**Cause racine enfin trouvée**. Utilisateur a soufflé l'hypothèse correcte : « il y a un actorID spécial fight pour les monstres ».

**Vérification dans StarLoco** (`Fight.java:200`) :
```java
for (Entry<Integer, MonsterGrade> entry : group1.getMobs().entrySet()) {
    Fighter mob = Fighter.NewMob(entry.getKey(), this, entry.getValue());
    //                            ^^^^^^^^^^^^^^^^
    //                            entry.getKey() est NÉGATIF (-1, -2, -3...)
    //                            car _Mobs.put(guid--, mob) descend depuis -1
}
```

**Vérification dans AncestraR** (`Monstre.java:174`) :
```java
int guid = -1;
for (int a = 0; a < nbr; a++) {
    ...
    _Mobs.put(guid, Mob);
    guid--;  // -1, -2, -3, ...
}
```

Le client Dofus 1.29 utilise le **signe de l'actorId** pour distinguer player (positif) de mob (négatif). Sandbox utilisait `1_000_000 + group.id × 10 + index` = 3000000+ (positif et grand) → le client tente d'afficher comme un player → pas de sprite trouvé → invisible.

**Fix** :
- `FightParser.initiateFightVsMonsters` génère un `monsterFightId = -1` puis décrémente (`-1, -2, -3...`) à chaque mob ajouté au combat.
- `FightParser.buildMonsterFighter` accepte maintenant le `fightId` négatif en paramètre au lieu de le calculer en interne.

**Retour au format StarLoco officiel** :
- Maintenant que les IDs sont corrects, on remet le format `-2` pour les mobs combat (Phase 35 utilisait `-3` en rustine, c'était l'ID négatif le vrai bug).
- Format mob restauré : `cell;dir;0;NEGATIVE_ID;templateId;-2;gfx^100;grade;col1;col2;col3;0,0,0,0;maxPdv;PA;PM;team` (16 champs StarLoco)
- Format player restauré : `cell;dir;0;id;name;classe;gfx^size;sex;level;0,0,0,levelPlusId;col1;col2;col3;accessories;currentPdv;PA;PM;resN;resE;resF;resW;resA;dodgePA;dodgePM;team` (26 champs StarLoco)

**Orientation hardcoded "1"** (StarLoco convention) :
- `Fight.appendGMEntry` : `fighter.getOrientation().ordinal()` remplacé par `"1"` hardcoded pour player ET mob, comme `PlayerFighter.getGmPacket` ligne 859 + `MobFighter.getGmPacket`.
- Évite que le perso garde son orientation roleplay (ex: NORTH_EAST=7) bizarre face à la team adverse.

**Why :** retour utilisateur « prend une décision radicale copie starloco », screenshot montrant cases placement OK mais aucun sprite mob visible. L'hypothèse actorID négatif était la bonne. C'est le bug racine depuis Phase 26.

## [Phase 35 - Format GM combat aligné sur format roleplay Sandbox] - 2026-05-14

**Cause racine identifiée** : le client custom Sandbox utilise une logique de parsing GM qui ne distingue PAS roleplay et combat. Il attend le **même format** dans les deux modes. Les formats StarLoco/AncestraR officiels (avec stats fight dans le GM) ne sont pas compris par ce client.

**Fix** :
- **Player combat** : `Fight.appendGMEntry` réécrit pour copier exactement `GProtocol.getCharacterPattern` (format roleplay qui marche, sprites + couleurs OK). Stats fight (PV/PA/PM/team) ne sont plus dans le GM — transmises uniquement via `GTM` envoyé juste après.
  - Format : `cell;dir;0;id;name;breed;skin^size;sex;align,100,alignLvl,0,dishonor;col1Hex;col2Hex;col3Hex;accessories;classFlag;;;;0;;`
  - Faction `0,100,0,0,0` Sandbox (au lieu du `0,0,0,levelPlusId` StarLoco testé Phase 30).
  - Pas de `level` après sex (StarLoco) — pas de level dans format roleplay Sandbox.
- **Mob combat** : aligné sur format roleplay groupe AncestraR `MonsterGroup.toGMEntry` (qui MARCHE en RP). On simule un « groupe d'un seul mob » avec marker `-3` au lieu de `-2`.
  - Format : `cell;dir;0;id;templateId;-3;gfx^100;level;-1;-1;-1;0,0,0,0;`
  - Stats fight (PV/PA/PM/team) transmises via GTM.
  - Champ 8 = `level` absolu (cohérent avec `MonsterGroup.toGMEntry` ligne 168 `grade.getLevel()`), pas le numéro de grade Phase 32.

**Pourquoi cette approche** :
- En roleplay : sprites visibles, couleurs OK. → Format A fonctionne.
- En combat (Phase 30-34) : sprites invisibles, couleurs fausses. → Format B (StarLoco/AncestraR officiel) ne marche pas.
- Conclusion : le client utilise format A pour tout. On bascule format combat sur format A.

**Test attendu** : sprites mobs visibles à l'entrée combat, couleurs player correctes, barres PV/PA/PM affichées correctement via GTM.

**Why :** retour utilisateur "le sprite des monstres au moment du combat ne fonctionne toujours pas". Lecture du log packets.log Phase 34 confirme que les paquets sont bien envoyés mais le client ne parse pas le format `-2` officiel. Bascule sur format `-3` roleplay-compatible.

## [Phase 34 - Formule XP officielle, starBonus, taille groupe AncestraR, minProsp/max] - 2026-05-14

Implémentation des formules officielles AncestraR pour XP, kamas, distribution taille groupe et drops avec `minProsp`/`max`. Lecture directe de `Formulas.getXpWinPvm2` (ligne 523), `Formulas.getKamasWin` (ligne 663), `MobGroup` constructeur (ligne 32) et `Fight.java:2743-2980`.

**1. Formule XP officielle `Formulas.getXpWinPvm2`** (`Fight.calculateAndApplyRewards`) :
```
xpWin = groupXP × rapport1 × bonus × coef × rapport2 × (1 + starBonus/100)
```
Avec :
- `coef = (sage + 100) / 100` (bonus sagesse du joueur)
- `rapport1 = max(1.3, 1 + lvlLoosers / lvlWinners)` (rapport challenge équipe perdante / gagnante)
- `rapport2 = 1 + winnerLevel / lvlWinnersSum` (part proportionnelle au level individuel)
- `bonus` selon `nbBonus` (winners dont level > lvlmax/3) : 1→1.0, 2→1.1, 3→1.3, 4→2.2, 5→2.5, 6→2.8, 7→3.1, 8+→3.5
- `starBonus` = bonus étoiles du groupe (cf. point 3)

**2. Formule kamas officielle `Formulas.getKamasWin`** : chaque joueur reçoit son propre roll `rand(min, max)` (pas de partage entre joueurs). Avant, on divisait le total entre les winners — moins généreux que l'officiel.

**3. `MonsterGroup.getStarBonus()`** : nouveau. Calcule `floor(elapsedMs / 3_600_000)`, cap à 200. Bonus de +1% par heure depuis la création du groupe (système officiel Dofus 1.29 qui récompense les groupes anciens). Appliqué aux drops ET à l'XP.

**4. Distribution officielle taille groupe** (`MonstersData.pickGroupSize`) : remplace le random uniforme `2..5` par les probabilités exactes AncestraR `MobGroup` constructor. Table pour maxSize 1-8 :
| maxSize | Distribution |
|---------|-------------|
| 2 | 50/50 |
| 3 | 33/33/33 |
| 4 | 22/26/26/26 |
| 5 | 15/20/25/25/15 |
| 6 | 10/15/20/20/20/15 |
| 7 | 9/11/15/20/20/16/9 |
| 8+ | 9/11/13/17/17/13/11/9 |

**5. `DropTable.DropEntry.minProsp` et `max`** : nouveaux champs.
- `minProsp` : PP minimum requise pour que le drop soit éligible (default 0 = toujours éligible).
- `max` : nombre max d'occurences avant épuisement (default `Integer.MAX_VALUE` = infini). Décrémenté à chaque obtention, drop retiré du pool global quand `max <= 0`.

**6. Filtre + boost effectifs** (`Fight.calculateAndApplyRewards`) :
- `effectivePP = groupPP × (100 + starBonus) / 100` (boost étoiles avant filtrage).
- Drop ignoré si `drop.minProsp > effectivePP`.
- Sinon, taux boosté `boostedRate = drop.rate × effectivePP / 100`.

**Why :** retour utilisateur "execute les patch (pour l'experience une formule est déjà presente dans l'ému il me semble utilise celle la)" — formule trouvée `Formulas.getXpWinPvm2` AncestraR ligne 523, implémentée à l'identique.

**Reste à faire (Phase 35+) :**
- Attente `GKK1` réelle au lieu de délai fixe pour animations IA (complexe : nécessite système de callbacks par action en cours).
- Persister `minProsp`/`max` depuis la BDD (actuellement les drops chargés via `loadDefaults` n'utilisent que rate/qty).
- Bonus challenges (XP+drop) si on implémente le système de challenges plus tard.

## [Phase 33 - Drop officiel AncestraR : tri PP, cycle, maxItemsPerPerso] - 2026-05-14

Réécriture de `Fight.calculateAndApplyRewards` pour suivre l'algorithme officiel AncestraR (`Fight.java:2743-2980`) + StarLoco.

**Avant** : tous les drops étaient attribués au premier joueur de l'équipe (`winners.get(0)`), XP et kamas divisés à l'égale entre tous les joueurs.

**Maintenant** (5 étapes alignées sur AncestraR) :

1. **PP de groupe agrégée** : somme des prospections (équipement + chance/10) de tous les winners. Clampée min 100.
2. **Pool `possibleDrops`** : pour chaque mob mort, on copie ses drops dans le pool avec le `rate` boosté par `groupPP / 100` (jusqu'à `MAX_RATE = 10000`). Source via nouveau `DropTable.getDrops(monsterId)` qui expose la liste brute (sans roll).
3. **Tri winners par PP descendant** : le joueur avec le plus de PP loot en premier.
4. **Distribution cyclique** : `maxItemsPerPerso = floor(possibleDrops.size() / nbWinners)` (au moins 1). Pour chaque winner dans l'ordre PP DESC : shuffle du pool, roll 1d10000 contre chaque drop, max `maxItemsPerPerso` items. Les drops obtenus sont **retirés du pool global** → joueurs suivants ont moins de choix (= équité officielle).
5. **XP et kamas** :
   - XP proportionnelle au level : `xpShare = totalXp × winnerLevel / totalTeamLevel`. Joueurs bas level reçoivent moins (en valeur absolue) mais c'est plus juste car ils gagnent un level plus vite.
   - Kamas : roll global `[minKamasTotaux, maxKamasTotaux]` boosté par PP, puis partage proportionnel à la PP individuelle de chaque winner.

**Nouvelle méthode `DropTable.getDrops(monsterTemplateId)`** : retourne la liste brute des drops (pas roll) d'un monstre. Permet à `Fight` de construire le pool global avant distribution.

**Pas implémenté (TODO Phase 34+)** :
- `minProsp` par drop : actuellement DropEntry n'a pas ce champ (AncestraR refuse certains drops si groupPP < minProsp). Notre code accepte tous les drops, donc plus permissif.
- `max` par drop (nombre d'occurences total possible avant épuisement) : on retire le drop dès qu'il tombe une fois, équivalent à `max = 1`.
- Bonus challenges + starBonus du groupe (timer depuis la création du groupe). Notre `MonsterGroup` n'a pas `starBonus`.

**Why :** retour utilisateur — "lister les drops par monstres dans le combat et répartir de façon officiel chercher sur internet le fonctionnement du drop et l'implémenter". Lecture directe du code AncestraR `Fight.java:2743-2980` (algorithme officiel Dofus 1.29) extrait depuis `ressources/émulateur pour support/`.

**Rapport complet** : `RAPPORT_MONSTRES_STARLOCO.md` — analyse comparée 7 sections (spawn, respawn, clic groupe, sprites, animations, drops/XP, priorités).

## [Phase 32 - Mob sprite : champ 8 = grade number, pas level absolu] - 2026-05-14

Analyse du log `logs/packets.log` Phase 31 — vérifie que le format `GM|+` mob suit bien StarLoco (16 champs). Découvert un bug subtil :

**Le champ 8 du GM mob doit être le numéro de grade (1-5), pas le level absolu (3-7)**.

- StarLoco `MobFighter.getGMPacketParts()` : `String.valueOf(mobGrade.getGrade())` — c'est `getGrade()`, le numéro 1-5.
- AncestraR `Fight.java:443` : `str.append(_mob.getGrade())` — pareil.
- Sandbox Phase 30 envoyait `fighter.getLevel()` qui est le level absolu via `setVisual(grade.getLevel(), ...)` — un Bouftou royal grade 5 envoyait `25` ou `30` au lieu de `5`.
- Conséquence : le client Dofus 1.29 attend grade ∈ [1,5] et refuse d'afficher le sprite si valeur > 5 → mobs invisibles.

**Fix** :
- `Fighter.java` : nouveau champ `mobGrade` avec getter/setter (n'utilise plus `level` pour deux choses différentes).
- `FightParser.buildMonsterFighter` : `fighter.setMobGrade(entry.getGrade())` ajouté.
- `Fight.appendGMEntry` (mob) : utilise `fighter.getMobGrade()` au lieu de `fighter.getLevel()` pour le champ 8. Fallback à `1` si non set.

**Vérification via PacketLogger** : ligne 62 du log Phase 31 montrait `+353;2;0;3000000;31;-2;1563^100;3;-1;-1;-1;0,0,0,0;15;4;2;1`. Avant Phase 32, le `3` au champ 8 était le `grade.getLevel()` du mob de level 3 (donc par chance ça matchait pour un grade 1). Pour les mobs de level > 5, ça envoyait des valeurs invalides.

## [Phase 31 - PacketLogger persiste sur disque (logs/packets.log)] - 2026-05-14

- `PacketLogger.java` réécrit : en plus de l'affichage `System.out.println` existant, écrit chaque ligne dans `logs/packets.log` (fichier texte brut, 1 ligne par paquet, format identique à la console).
- **Au démarrage** : si `logs/packets.log` existe déjà (run précédent), il est archivé en `logs/packets-YYYYMMDD-HHmmss.log` avant la création d'un nouveau fichier. Le run en cours est donc TOUJOURS dans `logs/packets.log`.
- **À la fermeture** : shutdown hook (`Runtime.getRuntime().addShutdownHook`) qui flush + close le fichier proprement. Marqueur `# PacketLog end <timestamp>` ajouté en dernière ligne.
- **En cours d'exécution** : chaque `recv()`/`sent()` fait un `flush()` immédiat → aucune ligne perdue même si le serveur crash SIGKILL.
- Synchronisation via `Object FILE_LOCK` autour de l'écriture (les filters MINA peuvent appeler depuis plusieurs threads I/O).
- Aucun changement dans `Game.java` ni `Server.java` — les appels existants `PacketLogger.recv/sent` fonctionnent toujours, juste persistés en plus.

**Why :** demande utilisateur — pouvoir relire tout l'échange réseau d'une session pour debug, et permettre à un assistant externe (Claude) de lire le log via un chemin fixe sans avoir à demander un copier-coller à chaque fois.

**Comment lire** : ouvrir `C:/Users/cheva/eclipse-workspace/Sandbox/logs/packets.log`. Ou `tail -f logs/packets.log` en live. Ou demander à Claude de faire un `Read` sur ce chemin.

**Note `.gitignore`** : `*.log` était déjà ignoré, donc rien ne sera commité par accident.

## [Phase 30 - Combat format StarLoco officiel, délai animation, maxLife] - 2026-05-14

Recherche dans `ressources/émulateur pour support/StarLoco-Game-master.zip` (`PlayerFighter.getGMPacketParts` ligne 97 et `MobFighter.getGMPacketParts` ligne 63) pour aligner le format `GM` combat sur la référence officielle Dofus 1.29.

**Format `GM|+` combat aligné sur StarLoco** :
- **Player** : `cell;dir;0;id;name;classe;gfx^size;sex;level;align,0,0,levelPlusId;col1;col2;col3;accessories;currentPdv;baseAP;baseMP;resN;resE;resF;resW;resA;dodgePA;dodgePM;team` (26 champs). Le `level + id` dans factionParts est le bizarre StarLoco officiel (commenté `// WTF ?` dans la source). Le `level` est bien ENTRE sex et factionParts (mon hotfix Phase 28 qui le retirait était une fausse piste).
- **Mob** : `cell;dir;0;id;templateId;-2;gfx^size;grade;col1;col2;col3;0,0,0,0;maxPdv;PA;PM;team` (17 champs). Pas de resists/dodge pour les mobs (format StarLoco MobFighter). C'était la cause des mobs invisibles : on envoyait 25 champs au lieu de 17.

**Fix `Fighter.maxLife` (régression maxLife=currentLife)** :
- `Fighter.maxLife` n'est plus `final`. Ajout d'un setter `setMaxLife(short)`. Le constructeur initialise `maxLife = life` (vie courante au moment de l'entrée combat) mais c'est buggé quand le joueur entre avec vie réduite (par exemple 1 PV après mort précédente). `FightParser.buildPlayerFighter` appelle maintenant `fighter.setMaxLife(character.getLifeMax())` pour fixer la vraie max.

**Délai animation déplacement (perso « TP »)** :
- `Fight.handleMove` : `GA1;1` envoyé immédiatement (le client commence l'animation), mais `GA;129` (perte PM) + `GIC` (position) + `GTM` (timeline) sont retardés via `FightTurn.schedule` de `Math.max(400, steps × 300)` ms. Avant, `GIC` immédiat après `GA1;1` téléportait le sprite à la nouvelle cellule sans animer le chemin intermédiaire.
- Le callback vérifie `fight.state != FINISHED` et que le fighter existe encore.

**Format `GE` fin combat amélioré** :
- Pour player (canLoot=true) : ajout de `defaultGfx` (skin) après `level` selon format StarLoco PvM winner. Champs vides remplacés par chaîne vide au lieu de `0` (xpGuild, xpMount). Format : `<result>;<id>;<name>;<level>;<skin>;<dead>;<minXp>;<curXp>;<maxXp>;<gainXp>;;;<drops>;<kamas>`.
- Pour mob : `<result>;<id>;<templateId>;<level>;<dead>;0;0;0;;;;;` (pas de skin).

**Why :** retour utilisateur Phase 29 :
1. « Pas les bonnes couleurs du perso » → format align mauvais (`0,100,0,0,0` au lieu de `0,0,0,levelPlusId` StarLoco).
2. « Mobs invisibles » → format mob avait des champs en trop (resists/dodge) que le client ne sait pas parser.
3. « Anime pas, le perso TP » → GA1;1 et GIC dans la même ms, le client n'a pas le temps d'animer.
4. « Fenêtre fin combat buggée » → format GE incomplet, manque `defaultGfx`.
5. « Mort en combat = perso invisible » → probablement lié au maxLife=1 si vie courante était 1, et au refresh sprite après combat. Partiellement adressé par le fix maxLife.

**Reste à faire :** vérifier en jeu, puis si toujours problèmes, comparer paquet par paquet avec StarLoco (j'ai maintenant accès aux sources `tmp/starloco/` extraites).

## [Phase 29 - Combat hotfix 2 : format GM combat, calcul PM, double GV] - 2026-05-14

Hotfix après analyse log combat fourni par l'utilisateur (Phase 28 → sprites toujours invisibles + déplacement illimité + panneau résultat buggé).

**Bug A — Calcul `steps` faux dans `handleMove` (PM jamais consommés)** :
- Le format path Dofus 1.29 est `(dir + cell) × N` = 3 chars par step. Pour `GA001ddH` (3 chars), c'est **1 step**, pas 0.
- Ancien calcul `(length / 2) - 1` donnait 0 pour 3 chars → pas de `GA;129` envoyé → compteur PM client jamais décrémenté → joueur peut se déplacer "illimité".
- Nouveau : `int steps = pathStr.length() / 3`. Validation min length passée de 2 à 3 chars.

**Bug B — Format `GM` combat insère `level` après `sex` (sprite invisible)** :
- Comparaison avec `GProtocol.getCharacterPattern` (format roleplay qui marche, sprites visibles) :
  - Roleplay : `cell;dir;0;id;name;breed;skin^size;sex;ALIGNMENT;col1;col2;col3;accessories;...`
  - Combat (avant) : `cell;dir;0;id;name;breed;skin^size;sex;**LEVEL**;ALIGNMENT;col1;col2;col3;accessories;...`
- Le client parsait `70` (level) comme alignment → tous les champs suivants décalés (skin/couleurs/accessoires) → sprite invisible.
- Fix : retiré le champ `level` du `appendGMEntry` player. Ajouté `maxLife` après `currentLife` pour le client puisse afficher la barre de vie correctement.
- Mob : ajouté aussi `maxLife` après `currentLife` pour cohérence du format.

**Bug C — Double `GV` en abandon (panneau résultat buggué)** :
- `FightParser.leaveFight` envoyait `session.write("GV")` après `handleAbandon`. Mais `handleAbandon` → `endFight` envoie déjà `broadcast("GV")`. Résultat : 2 `GV` consécutifs → client traite le 1er (sortie + panneau résultat), puis le 2e invalide le panneau.
- Fix : retiré le `session.write("GV")` dans `leaveFight` après `handleAbandon`. Garde uniquement `session.write("GI")` pour rafraîchir la map après le panneau.

**Why :** analyse fine du log combat fourni montrant :
1. `GA001ddH` (3 chars) → serveur envoie `GA1;1` mais pas de `GA;129` → PM client toujours plein.
2. `GM|+213;5;0;...;1;70;0,100,...` → le `70` mal placé décale le parsing client.
3. `GV` × 2 puis `GI` → panneau combat ferme/réouvre/glitche.

**Reste à faire :** vérifier en jeu que ces 3 fixes débloquent enfin les sprites + PM corrects + panneau de résultat propre. Ensuite ré-implémenter tacle/portée Phase 30 avec coordonnées isométriques correctes (utiliser `map.getWidth()`).

## [Phase 28 - Combat hotfix : sprites GM concaténé, tacle/portée désactivés, abandon] - 2026-05-14

Hotfix après retours utilisateur Phase 27 : sprites toujours invisibles, joueur impossible à déplacer, sorts impossibles à lancer, abandon non fonctionnel.

- `Fight.sendFightSprites(session)` et `Fight.broadcastFightSprites()` : envoient maintenant un SEUL paquet `GM|+fighter1|+fighter2|+fighterN` concaténé (format roleplay standard 1.29), au lieu de N paquets `GM|+xxx` séparés. Méthode `buildAllSpritesGMPacket()` ajoutée. C'était la cause principale des sprites invisibles : le client 1.29 attend tous les acteurs dans un seul `GM`, pas un par paquet.
- `Fight.handleMove()` : retiré le check tacle (Phase 27 patch 9). Le calcul d'adjacence avec `diff == 1 || diff == 14` ne matche pas la grille isométrique Dofus, ce qui pouvait bloquer le joueur même quand aucun ennemi n'était réellement adjacent. À réimplémenter Phase 29+ avec coordonnées (x,y) isométriques correctes.
- `Fight.handleSpell()` : retiré le check portée + ligne (Phase 27 patch 8). Pour la même raison : `manhattanDistance` avec `MAP_GRID_WIDTH=14` constante ne correspond pas à la vraie largeur isométrique. Garde uniquement la validation `map.isValidCellId(targetCell)` et le coût PA. À réimplémenter avec `map.getWidth()` + grille isométrique correcte.
- `Fight.handleAbandon(fighter)` : nouvelle méthode publique pour gérer l'abandon en combat actif. Marque le fighter mort via `takeDamage(99999, 0)`, broadcast `GA;103` + `GA;402`, fin de tour si current, puis check de victoire.
- `FightParser.leaveFight()` : ne renvoie plus `BN` quand fight ACTIVE. Si placement → retire le fighter et restore map (Phase 27). Si actif → appelle `fight.handleAbandon(fighter)` pour mort propre + `GV` + `GI`. C'était la cause de l'« impossible d'abandonner le combat ».
- Helpers `adjacentEnemies`, `tackleEscapeChance`, `manhattanDistance`, `isInStraightLine`, `MAP_GRID_WIDTH` conservés dans le code mais inutilisés pour l'instant (réutilisés Phase 29+ après fix grille isométrique).

**Why :** la Phase 27 a introduit deux régressions (tacle + portée) basées sur une grille rectangulaire alors que Dofus 1.29 utilise une grille isométrique. Les sprites invisibles étaient un bug Phase 26 (GM individuel) qui n'avait pas été identifié. Cette phase corrige les 3.

**Reste à faire :** ré-implémenter portée et tacle avec coordonnées isométriques (`(x = cell % mapWidth, y = (cell - x) / mapWidth)` mais sur grille en losange Dofus avec offset alterné selon parité de row).

## [Phase 27 - Combat patches manquants : GDK placement, GV fin, tacle, portée, restore] - 2026-05-14

Compléments à la base Phase 26 — appliqués directement sur main après audit du log de combat fourni.

- `Fight.startPlacement()` : `broadcast("GDK")` après `GM`/`GIC`. Sans ce signal de fin de chargement, le client 1.29 ne finalisait pas l'affichage des sprites au lancement du combat. Le `scheduleFightSpritesRefresh` à +100 ms reste comme filet de sécurité.
- `Fight.endFight()` : `broadcast("GV")` après le packet de résultat `GE`. Sans ce signal de sortie, le client restait dans l'état combat et le panneau de résultat (XP/kamas/drops) ne se finalisait pas correctement.
- `Fight.removeFighter()` : si le fighter retiré est un joueur, `map.addActor(chr)` est appelé pour le replacer sur la map roleplay. Avant, `leaveFight` envoyait `GV`+`GI` au client mais le `Characters` n'était jamais réattaché à la map roleplay — les autres joueurs ne le voyaient plus revenir.
- `Fight.cancelFight()` : restaure le `MonsterGroup` sur la map via `map.addMonsterGroup(group)` + broadcast `GM|+<entry>` aux acteurs roleplay. Avant, si l'initiateur quittait en placement PvM, le groupe restait disparu jusqu'au prochain respawn — perte définitive pour les autres joueurs. Code dédié ne réutilise pas `restorePlayersAfterFight()` qui écraserait la cellule RP avec la cellule de placement combat.
- `Fight.broadcastOnMap(packet, except)` : nouvelle méthode privée pour diffuser un paquet aux acteurs encore sur la map roleplay (équivalent à la version dans `FightParser`).
- `Fight.handleSpell()` : portée min/max et `isLineOnly()` vérifiés AVANT de consommer les PA. `manhattanDistance(a, b)` et `isInStraightLine(a, b)` ajoutés (helpers utilisant `MAP_GRID_WIDTH = 14`). Refus immédiat si `targetCell` hors map. Avant, un joueur pouvait lancer n'importe quel sort sur n'importe quelle cellule et l'AP était consommé même si la cible était invalide.
- `Fight.handleMove()` : tacle 1.27 simplifié — si départ adjacent à au moins un ennemi vivant, roll d'esquive `(agiFlee + 25) / (agiFlee + agiTotal + 50)` clampé [0.10, 0.90]. Sur échec, mouvement annulé, perte de `currentMP × (1 - escape)` PM et `currentAP × (1 - escape) / 2` PA. `GA;102` et `GA;129` broadcastés pour notifier la perte. Helpers `adjacentEnemies(ref, cell)` et `tackleEscapeChance(fleeing, taclers)` ajoutés.
- `FightTurn` : champ `turnStartMs` initialisé dans `startTurn()`, méthode `getRemainingMs()` qui calcule `TURN_DURATION_SEC*1000 - elapsed`. Avant, un joueur reconnecté recevait toujours un `GTS` avec 30 000 ms même si le tour était déjà avancé.
- `Fight.reconnect()` et `Fight.addSpectator()` : envoient maintenant `GTS<id>|<remainingMs>` (au lieu de `TURN_DURATION_SEC*1000` plein).

**Why :** patches faits dans un git worktree (branche `claude/agitated-gould-34babd`) puis ré-appliqués sur main car Eclipse compile main, pas le worktree — d'où l'observation utilisateur « Clean & Build et toujours buggué ». Les changements main du commit `ab62e7b6 "Fight"` couvraient ~30 % des patches prévus ; cette phase 27 ajoute le reste.

**Reste à faire (Phase 28+) :** ligne de vue réelle avec raycast et obstacles, cooldown/maxPerTurn/maxPerTarget des sorts, CC/EC, états (Pesanteur, Stabilisé), zones de sort (croix/cercle/ligne), invocations, packet `GA;5` explicite pour signaler le tacle au client.

## [Phase 26 - Base combat PvM et corrections client 1.29] - 2026-05-14

- `RolePlayMovement.java` : l'attaque d'un groupe de monstres se declenche maintenant apres l'arrivee du deplacement, avec un delai de 100 ms apres la cellule d'arret. Le chemin vers une cellule de monstre est tronque avant la cellule occupee.
- `Fight.java` / `FightParser.java` : base PvM consolidee avec placement `GJK/GP`, pret `GR`, initiative, ordre `GTL`, etat `GTM`, timer `GTS/GTF`, deplacement PA/PM, sorts, morts, fin `GE`, drops/xp/kamas, spectateurs et reconnexion combat.
- `Fight.java` : les sprites de combat sont envoyes en packets `GM|+...` separes, puis rafraichis apres 100 ms avec `GIC/GTM`, pour eviter l'ecran ou seuls les cercles rouge/bleu apparaissent.
- `Fight.java` / `MonsterAI.java` : suppression des faux `GA;306` envoyes apres les degats. Le client 1.29 lit `306` comme un piege, ce qui provoquait des messages `undefined`; les PV/PA/PM sont maintenant synchronises par `GTM`.
- `MonsterAI.java` : l'IA PvM joue son tour, temporise ses animations, synchronise l'etat et passe son tour proprement. Les taches differees de `Fight` n'accedent plus aux champs prives par accesseur synthetique fragile.
- `FightParser.java` / `RolePlayHandler.java` : ajout de la sortie du mode spectateur via `GQ`/`fV`, avec envoi `GV` pour revenir sur la carte normale.
- `Inventory.java` : encodage des accessoires visuels recale sur Ancestra (`arme,coiffe,cape,familier,bouclier`) afin que le bouclier utilise le bon layer client.
- `MapTemplate.java` / `GameParser.java` : la validation de cellule ignore maintenant le personnage courant, ce qui evite le blocage de deplacement juste apres creation/connexion.

## [Phase 25 - Commande admin .align et fix zaapis] - 2026-05-13

- `AdminParser.java` : commande `.align <nom> <type>` ajoutée. Fixe l'alignement d'un personnage connecté (0=neutre, 1=Bonta, 2=Brakmar), sauvegarde en BDD, envoie `ZS{type}` au client pour mettre à jour l'interface immédiatement. Le panel zaapis (`Wc`) s'ouvre correctement ensuite via skill 157. Investigation depuis `Subway.as` (sources StarLoco client) : `Wv` reçu = close panel (`Subway.onLeave`), `Wc{data}` = open panel (`Subway.onCreate`). La colonne `alignment` était déjà présente en BDD mais valait 0 (neutre) — le serveur renvoyait `Wv` correctement selon l'alignement.

## [Phase 24 - Orientation personnage et SQL logging] - 2026-05-13

- `RolePlayHandler.java` : `case 'e'` décommenté et `parseEnvironementPacket()` ajouté. Le paquet `eD{n}` (clic droit → changer orientation) met maintenant à jour `character.setCurrentOrientation()` et diffuse `eD{characterId}|{n}` à tous les acteurs de la carte.
- `Connector.java` : chaque connexion JDBC est maintenant enveloppée dans un `Proxy` qui intercepte tous les appels `prepareStatement(sql, ...)` et log le SQL au niveau DEBUG (`[SQL] ...`). Aucun changement dans les classes Data — le logging est automatique sur toutes les requêtes préparées.

## [Phase 23 - Groupes monstres multi-membres et cellule safe minimap] - 2026-05-13

- `MonstersData.java` : les groupes de monstres ont maintenant plusieurs membres (2–5) au lieu d'un seul. Le code collecte toutes les entrées `monster_spawns` dans un pool, mélange aléatoirement, puis découpe en groupes de `GROUP_SIZE_MIN=2` à `GROUP_SIZE_MAX=5` membres. Chaque restart produit des groupes de taille différente. La classe interne `PendingMonsterGroup` est remplacée par `RawSpawnEntry`. Imports `LinkedHashMap`/`Map` supprimés, `Collections`/`Random` ajoutés.
- `BasicParser.java` : `moveByClickMap()` (paquet `BaM`) vérifie maintenant que la cellule d'arrivée est walkable avant de téléporter. Si le zaap de la carte cible ou la cellule 200 de fallback est non-walkable, on appelle `map.findNearestValidActorCell()` pour trouver la cellule libre la plus proche — même logique que `WaypointParser.getArrivalCell()`.

## [Phase 22 - Stabilisation téléport et persistance] - 2026-05-13

- `RolePlayMovement.java` : `teleport()` vide maintenant la pile d'actions (`client.getActions().clear()`) avant de changer de carte. Avant, un déplacement en cours restait dans la pile après un BaM ou un clic mini-map, laissant le joueur bloqué en état BUSY sur la nouvelle carte.
- `WaypointParser.java` : `use()` appelle `CharactersData.update(character)` immédiatement après le téléport zaap/zaapi. La position `currentMap`/`currentCell` est maintenant sauvegardée en BDD dès l'utilisation, pas seulement à la déconnexion.

## [Phase 21 - Cleanup zaapis, walkability et loaders] - 2026-05-13

- `EConstants.java` : suppression de 10 doublons exacts dans `BRAKMAR_ZAAPI` (même mapId + cellId). Le panel zaapi Brakmar affichait des destinations en double. 45 entrées → 35 uniques.
- `WaypointParser.java` : correction du packet `Wc` (panel zaapi) — le champ `mapRespawn` envoyait `currentMap.getId()` au lieu de `character.getSaveMap()`, incohérent avec le panel zaap `WC`.
- `InteractiveObjectsData.java` : correction du fallback `isWalkable()` — les arbres (frêne, chêne, orme, bambou...), les minerais et les poissons étaient incorrectement marqués traversables par le check sur le nom. Seuls les céréales (blé, orge, avoine, houblon, lin, seigle, riz, malt, chanvre) et les herbes (menthe, orchidée, trèfle, edelweiss, pandouille) restent traversables dans ce fallback ; tout le reste reste bloquant.
- `CharactersData.java` : `ensureSaveColumns()` ne déclenche plus `ALTER TABLE IF NOT EXISTS` à chaque sauvegarde de personnage. Un flag statique `volatile` garantit qu'il s'exécute une seule fois au premier appel.
- `MapsData.java` : `optionalString()` utilise maintenant un `try/catch` direct au lieu d'itérer les métadonnées `ResultSet` via `hasColumn()` pour lire `mapKey` et `cellsData`. Suppression de l'import `ResultSetMetaData` devenu inutile.

## [Phase 20 - Cellules interactives et zaaps officiels] - 2026-05-12

- `sql/starloco_maps_triggers_patch.sql` importe les vraies maps/triggers StarLoco (`11117` maps, `29304` triggers) dans `map_templates` et `map_triggers`, avec colonnes officielles `mapKey`, `cellsData` et `capabilities`.
- `sql/starloco_maps_triggers_patch_v2.sql` fournit la version robuste du patch SQL : un `INSERT` par map et paquets triggers reduits pour eviter les erreurs MySQL `2006/2013 Server has gone away`.
- `sql/starloco_maps_triggers_v3_parts/` decoupe l'import SQL en plusieurs fichiers : metadata legeres, `cellsData` en chunks `UPDATE CONCAT` de 3000 caracteres, puis triggers. Cette version evite aussi l'erreur MySQL `1153 Got a packet bigger than max_allowed_packet`.
- `MapsData.java` et `MapTemplate.java` acceptent maintenant le schema StarLoco sans casser l'ancien format : les cellules sont decodees depuis `cellsData` avec la vraie `mapKey`, puis fallback sur `key/date`.
- `MapCellDecoder.java` prepare la cle hex officielle StarLoco avant dechiffrement et conserve le decodeur legacy pour les dumps historiques.
- Les triggers de map passent en IDs `int` cote SQL/Java pour importer StarLoco sans truncation.
- `InteractiveObjectsData.java` integre le mapping StarLoco officiel `gfx -> objet interactif -> type/skills/walkable`. Les ressources explicitement traversables (ble, orge, avoine, lin, riz, plantes, etc.) restent traversables meme si une vieille BDD les marque `walkable=0`; puits, zaaps/zaapis, poubelles, coffres et portes restent bloquants.
- Suppression des anciennes rustines `MapCellWalkabilityData`, `InteractiveObjectCellsData`, `map_cell_walkability` et `interactive_object_cells`. Le serveur ne maintient plus de tables paralleles qui contredisent les donnees officielles de map.
- `MapTemplate.java` applique la regle officielle : une ressource walkable peut etre traversee, mais une cellule interactive prete ne peut plus etre la cellule d'arrivee. Les objets non walkable bloquent toujours le passage.
- `InteractiveObjectService.java` branche les ressources metiers StarLoco sur `GA500/GA501/GDF` avec le troisieme parametre d'animation `unknow` : paysan, alchimiste, bucheron, mineur, pecheur, tas de patates et puits donnent maintenant les ressources correspondantes a l'inventaire avec sauvegarde SQL et mise a jour pods.
- `WaypointParser.java` gere maintenant `GA500` zaap/zaapi sans fausse animation de recolte : skill 114 ouvre le panel, skill 44 sauvegarde la position zaap, skill 157 ouvre les zaapis disponibles.
- `CharacterExperience.java` soigne maintenant le personnage a 100% de ses PV max au level-up.
- `AdminParser.java`, `Item.java`, `ItemEffect.java` : les commandes menu admin `BA!item` / `BA!getitem` creent les objets avec jets maximum.
- `sql/interactive_objects_official_patch.sql` ajoute une migration pour aligner `interactive_objects_data` avec les vrais noms officiels StarLoco accentues, ajouter `saveMap/saveCell`, et supprimer les tables de walkability apprises.
- Les deplacements tronquent maintenant explicitement le dernier pas quand la cible est une `cellAction` : le joueur s'arrete devant le puits, le zaap, le zaapi ou la ressource, puis l'action s'execute au contact.
- Les panels zaap/zaapi envoient maintenant les destinations au format `mapId;cout`, ce qui corrige le cout affiche en `NaN` cote client; le TP arrive sur une cellule libre proche du zaap/zaapi au lieu de poser le personnage sur la cellule interactive.
- Les cellules officielles zaap/zaapi sont maintenant bloquees par `Formulas.isWaypointCell(...)` meme si le decodeur map ne marque pas correctement le layer interactif. Astrub `7411,311` ne peut donc plus etre une cellule d'arrivee de marche.
- Liste zaap recalee depuis les scripts StarLoco : Brakmar passe de `5295,579` a `5295,561`, Duty Free passe a `10114,282`, et les panels `WC/Wc` respectent le format client `mapCourante|mapRespawn|mapId;cout|...`.
- `MapTemplate` normalise maintenant la walkability au chargement : cellules waypoint/objets bloquants forcees non walkable, ressources officielles type ble/avoine/malt forcees walkable pour la traversee.
- `RolePlayMovement` n'utilise plus l'ancien recalcul directionnel fragile du chemin client. Il garde le chemin envoye, mais si l'arrivee vise une cellule action/non walkable, il remplace la destination par la case precedente dans la meme direction.
- Ajout de `ressources/starloco_interactive_cells.csv` genere depuis les scripts maps StarLoco : 18 378 cellules interactives officielles `mapId;cellId;gfxId`. Cela corrige les maps SQL sans cellsData complet, par exemple Astrub ou le vrai objet zaap est sur la cellule `7411,297` alors que la cellule de destination/respawn zaap est `7411,311`.
- `OfficialInteractiveCellsData.java` applique le modele StarLoco `GameCase.isWalkable(checkObject, inFight, targetCell)` en fallback : objet non walkable bloque la traversee, ressource walkable peut etre traversee, aucune cellule interactive prete ne peut etre la cellule d'arrivee.

## [Phase 19 - Familier PV et clic ressource officiel] - 2026-05-12

### Familiers
- Encodage des points de vie familiers en format client 1.29 `800#vie#0#vieMax#0` (`320#a#0#a#0`) au lieu d'un jet fixe `0d0+vie`.
- Les anciens familiers charges depuis la BDD sont normalises en memoire afin que la ligne de PV apparaisse aussi apres reconnexion.
- Les pertes/soins de PV conservent maintenant le maximum du familier et renvoient un paquet `OC` avec l'effet de vie lisible par le client.
- Le parsing `Of` des nourritures accepte maintenant les variantes d'ordre cible/nourriture et les UIDs en decimal ou hexadecimal, pour corriger les familiers qui refusaient de manger alors que l'objet existe bien en inventaire.
- Le parsing `Of{familier}|{position}|{nourriture}` privilegie maintenant le premier UID comme cible et le dernier UID comme nourriture, afin de ne plus confondre la position d'equipement avec un item.
- Une nourriture refusee renvoie maintenant le message officiel `Im153` au lieu d'une condition impossible generique.

### Deplacement / ressources
- Les cellules interactives bloquantes restent non marchables, mais un chemin client qui finit sur une ressource est maintenant tronque sur la derniere cellule marchable.
- Les `GA500` recus pendant la marche sont mis en attente puis executes apres le `GKK`, ce qui reproduit le flux officiel : courir jusqu'au pied de la ressource puis lancer l'interaction.
- Si le joueur est deja au contact, le serveur libere l'action de mouvement sans bloquer le client et laisse l'action ressource se traiter normalement.
- Suppression du faux no-op `GA;2` qui etait interprete par le client comme une action de changement de map ; les chemins nuls passent maintenant par `GA1;4` ackable, sans envoyer un chemin de marche vide au client Flash.
- Les attaques de groupes de monstres (`GR` et `GA906`) sont mises en attente si le joueur est encore en train de marcher, puis declenchees apres le `GKK` pour eviter la pile d'actions bloquee.
- Une interaction carte executee apres une marche ne declenche plus le trigger/soleil de la cellule d'arret dans le meme cycle, ce qui evitait les teleportations surprises autour des ressources.
- Les `GA500` recus pendant une action non-mouvement sont refuses proprement par `BN` au lieu d'entrer dans une action concurrente.

### Regen / packets
- `RegenService` n'envoie plus de paquet `As` strictement identique au precedent et ne redemarre plus une regen deja planifiee. Le tick officiel/rythme de regen n'a pas ete change sans validation.

## [Phase 18 - Puits non marchables et familiers pets.sql] - 2026-05-12

### Cartes / interactifs
- Ajout de `InteractiveObjectCellsData` : memoire SQL optionnelle `interactive_object_cells(map_id, cell_id, skill_id, blocking)` pour bloquer les cellules interactives quand les 560 cellules de la map ne sont pas decodees cote serveur.
- Seed direct du puits d'Amakna (map `1359`, cellule `283`, skill `102`) : le serveur refuse maintenant le deplacement roleplay sur cette cellule meme si `map_templates` ne contient que la cle/date client.
- `InteractiveObjectService` apprend et persiste les cellules `GA500` inconnues afin que les ressources/interactifs deviennent bloquants lors des prochains deplacements.
- Au chargement de map, un personnage sauvegarde sur une cellule devenue invalide est replace sur la cellule valide la plus proche.

### Familiers
- Ajout du loader `PetsData` branche sur la table `pets` (`StatsUp`, `Max`, `Gain`, `DeadTemplate`).
- La nourriture des familiers utilise maintenant les nourritures autorisees de `pets.sql`; une nourriture invalide renvoie une condition impossible.
- La poudre d'Eniripsa (`2239`) rend 1 PV aux familiers vivants, sans depasser 10 PV.
- A 0 PV, le familier se transforme en fantome via `DeadTemplate`, perd ses effets de vie/repas, passe en sac et conserve ses bonus pour une future resurrection.

## [Phase 16 - Objets interactifs, ressources et cellules non marchables] - 2026-05-12

### Cartes / cellules
- Alignement du `MapCellDecoder` sur le decodeur client 1.29 : lecture de `layerObject1Num`, `layerObject2Num` et `layerObject2Interactive`.
- Les cellules decodees exposent maintenant leur etat actif, leur objet interactif et une validation dediee au spawn de monstres.
- Les groupes de monstres ne peuvent plus apparaitre sur une cellule non marchable, inactive, trigger/soleil ou contenant un objet interactif de couche 2.

### Ressources / interactifs
- Ajout d'un `InteractiveObjectService` pour centraliser les clics client `GA500` sur ressources, portes et objets interactifs de map.
- Le serveur refuse les interactions sur cellules invalides ou non interactives, joue l'animation officielle `GA;501`, puis bascule l'objet en cooldown visuel via `GDF`.
- Les objets interactifs sont automatiquement rendus a nouveau cliquables apres un delai de respawn serveur.

### Deplacement / sol
- Validation serveur des chemins roleplay avant broadcast : un client ne peut plus finir un deplacement sur une cellule non marchable ou occupee.
- Les objets jetes au sol cherchent maintenant une cellule adjacente reellement marchable et libre selon les donnees de map decodees.
- Les teleportations d'objets utilisables se recalculent vers la cellule valide la plus proche si la destination SQL cible une cellule invalide.

## [Phase 17 - Donnees interactifs SQL et familiers] - 2026-05-12

### Cartes / interactifs
- Ajout du loader optionnel `InteractiveObjectsData` pour lire `interactive_objects_data` ou `interactive_object_data`.
- Les cellules dont le `layerObject2Num` correspond a un interactif SQL `walkable=0` sont maintenant bloquees pour les joueurs, bots, monstres et depots au sol.
- Les ressources/interactifs `GA500` utilisent maintenant `respawn` et `duration` depuis la table SQL quand ces valeurs sont disponibles.

### Familiers
- Initialisation propre des familiers type 18 : les nouveaux familiers demarrent avec leurs effets d'etat (`800` vie, `806` corpulence, `807` dernier repas) sans roller tous les bonus possibles du template.
- Nourriture via `Of` : consommation de l'objet donne, mise a jour du dernier repas (`807/808`), corpulence normale (`806`) et gain de bonus borne par le cap du template.
- Les familiers equipes rafraichissent les stats du personnage apres gain de bonus.
- En fin de combat, un familier equipe perd 1 point de vie si son porteur meurt ; a 0 PV il est desequipe et persiste comme familier mort.

## [Phase 15 - Spawn naturel : groupes multi-monstres restaurés] - 2026-05-12

### Monstres / spawn
- Correction d'une régression du placement naturel v4 : les lignes `monster_spawns` partageant la même cellule et la même orientation sont de nouveau regroupées dans un seul `MonsterGroup` avant résolution de la cellule naturelle.
- Les groupes multi-monstres ne sont plus séparés en plusieurs groupes d'un seul monstre quand le premier membre occupe déjà la cellule naturelle.
- Le placement naturel reste appliqué à tous les groupes, mais une seule fois par groupe logique complet.
- Le log de spawn indique maintenant aussi le nombre de monstres contenus dans chaque groupe.

### Notes
- Les derniers cas de monstres posés sur des ressources ou éléments interactifs restent liés au manque de données serveur sur les cellules de récolte/interactifs. Une couche dédiée de cellules interdites au spawn sera ajoutée dans une prochaine étape.

## [Phase 14 - Stats officielles, conditions objets, Obvijevan et regen] - 2026-05-11

### Statistiques personnage
- Base personnage forcee cote serveur a 6 PA et 3 PM, avec bonus +1 PA calcule au niveau 100 sans l'ecrire en doublon dans la BDD.
- Pas de plafond PA/PM artificiel applique cote serveur pour rester compatible 1.29.
- PV max recales sur une base minimale de 55 PV + 5 PV par niveau + vitalite totale.
- Creation personnage : la vie initiale sauvegardee utilise maintenant le max calcule.
- SQL : `sql/phase14_stats_items_regen.sql` met a jour les classes existantes en 55 PV / 6 PA / 3 PM.

### Conditions objets
- Evaluation des conditions avec groupes `&`/`,` en ET et alternatives `|` en OU.
- Ajout des checks de conditions sur PA, PM, niveau, classe, sexe, alignement, grade, kamas et stats totales equipees.

### Objets speciaux
- Types Dofus, cadeau et objet vivant ajoutes aux types connus.
- Objets vivants type 113 et cadeaux type 89 non stackables.
- Support Obvijevan : association sur slot compatible, effets 970-974 persistants, dissociation `Ox`, changement de skin `Os` et nourriture `Of`.
- Les accessoires personnage utilisent maintenant le template visuel vivant et les parametres `template~type~skin` attendus par le client.
- `Ox` rend maintenant l'Obvijevan detache dans le sac au lieu de supprimer seulement les effets visuels.
- Refus d'associer un Obvijevan sur un objet deja vivant, correction du decalage de skin `Os`, et validation du type de nourriture `Of`.
- Les Obvijevan nus type 113 recoivent maintenant les effets vivants initiaux `971/972/974`, pour que le client les affiche dans l'inventaire.
- Les Obvijevan nus ajoutent aussi `973` avec le type supporte (`17/16/1/9`), sinon le client cherche l'icone dans le type 113 et ne rend pas l'objet.
- Les cadeaux Obvijevan `9360-9363` s'ouvrent via `OU` en vrais Obvijevan `9255/9256/9233/9234`.
- Les commandes admin `.item` et `BA!getitem` acceptent `113` comme raccourci de test pour donner les quatre Obvijevan reels.
- SQL : `sql/phase14_stats_items_regen.sql` force les templates Obvijevan en type 113 et leurs cadeaux en type 89.
- Association, dissociation, skin et nourriture Obvijevan renvoient maintenant un paquet `OC` complet pour rafraichir le sprite de l'objet support sans deco/reco.
- Nourrir un Obvijevan augmente son XP `974` selon le niveau de l'objet mange, avec clamp sur les 20 seuils de skin officiels du client 1.29.
- La nourriture Obvijevan affiche maintenant le gain `+XP` et le skin maximum debloque.
- Commande debug `.obvixp <uid> <xp>` / `BA!obvixp <uid> <xp>` pour ajouter directement de l'XP Obvijevan, UID accepte en decimal ou hex.

### Regeneration
- Regen passive remplacee par 1 PV par seconde apres 10 secondes hors combat.
- Suppression de l'ancien rythme trop rapide `maxLife / 10` toutes les 2 secondes.
- Le refresh vie continue d'envoyer un paquet `As` complet pour garder le client synchronise.

## [Phase 13 - Corrections commandes, IM, regen vie et stacking equipables] - 2026-05-11

### Commandes admin
- Recherche joueur insensible a la casse pour les commandes admin en ligne (`.level`, `.item`, etc.).
- Correction du crash possible de `.level` lie a `Statistic.add()` quand une stat n'existait pas encore dans la map.

### Messages client
- Neutralisation du `Im021;quantite~nom` qui affichait `Undefined` cote client : les ajouts admin utilisent maintenant un message canal serveur propre.

### Vie / regeneration
- `RegenService` renvoie maintenant le paquet `As` complet au lieu d'un paquet `AS` incomplet.
- La regeneration est relancee uniquement si `life < lifeMax`, et stoppee si la vie est pleine.
- Refresh immediat des stats/vie apres equipement, desequipement ou boost vitalite.

### Inventaire / equipement
- Les effets envoyes au client sont prefixes par `,` dans la partie stats des items, comme attendu par le client 1.29.
- Les anciennes lignes `character_items.rolled_effects` contenant encore des jets template (`1dX+Y`) sont rerollees au chargement puis sauvegardees.
- Les equipements ne sont plus stackables : les commandes `.item` et `BA!getitem` creent des instances separees quantite 1 pour les objets equipables.
- Refus d'equiper une pile `quantity != 1` pour eviter les disparitions/doublons.
- Le restack est limite aux templates non equipables et aux jets identiques.

## [Phase 12 — Menu admin BA!getitem + jets aléatoires objets] — 2026-05-11

### Commandes administrateur

- **`RolePlayHandler.java`** — Le paquet console admin `BA` est maintenant branché. Le client peut envoyer directement des commandes internes du menu admin.
- **`AdminParser.java`** — Ajout de `parseBasicAdmin()` pour traiter les paquets du type `BA!getitem 9131 1`.
- **`AdminParser.java`** — Ajout de la commande `getitem` utilisée par le menu admin : `BA!getitem <templateId> [quantite]`. Elle donne l'objet au personnage GM courant, sans devoir écrire `.item Cheuch ...`.
- **`AdminParser.java`** — Factorisation de la création d'objet dans `giveItem()` : `.item <nom> <templateId> [quantite]` et `BA!getitem <templateId> [quantite]` utilisent maintenant le même code.
- **`AdminParser.java`** — Correction d'un doublon accidentel de méthode `getSession()` introduit pendant les phases précédentes.

### Objets / jets

- **`ItemEffect.java`** — Amélioration de la formule de création des jets aléatoires. Le serveur lit maintenant les expressions de dés du champ client, par exemple `1d20+0`, `1d6+4`, puis génère une valeur aléatoire réelle.
- **`ItemEffect.java`** — Fallback conservé pour les anciennes bases : si le champ de dés n'est pas exploitable, le serveur utilise les bornes numériques déjà présentes dans l'effet.
- Les objets donnés par `.item` ou par `BA!getitem` sont donc créés avec des jets rollés au moment de l'insertion, puis sauvegardés dans `character_items.rolled_effects`.

### Test conseillé

- Envoyer depuis le menu admin : `BA!getitem 9131 1`, `BA!getitem 7925 1`, `BA!getitem 10125 1`.
- Vérifier que l'objet arrive dans l'inventaire avec `OAKO*O`, que les pods se mettent à jour avec `Ow`, et que deux créations du même objet avec une plage de stats peuvent avoir des jets différents.

## [Phase 11 — Commandes GM level/item + résistances objets] — 2026-05-11

### Commandes GM

- **`AdminParser.java`** — Ajout de la commande `.item <nom> <templateId> [quantité]` : création d'une instance d'objet depuis `item_template`, insertion en BDD dans `character_items`, ajout en inventaire et envoi client `OAKO*O;...` + refresh pods `Ow`.
- **`AdminParser.java`** — Correction complète de `.level <nom> <niveau>` : changement réel du niveau, recalage de l'expérience au seuil minimum du niveau, recalcul des points de caractéristiques/sorts, correction PA niveau 100, sauvegarde BDD et envoi des paquets `AN`, `As`, `Ow`, `Im`.

### Refresh social après changement de niveau

- **`AdminParser.java`** — Refresh groupe : renvoi `PM+{parseParty}` aux membres connectés pour mettre à jour le niveau du personnage dans le groupe.
- **`AdminParser.java` / `GuildsData.java`** — Refresh guilde : mise à jour du cache `GuildMember.level`, sauvegarde `guild_members.level`, puis renvoi `gL` aux membres connectés.
- **`AdminParser.java`** — Refresh map : renvoi `GM|-id` puis `GM|+...` pour mettre à jour l'affichage/aura du personnage après changement de niveau.

### Statistiques objets

- **`Statistic.java`** — Le paquet `As` inclut maintenant les bonus de résistances des objets équipés : résistances fixes, résistances %, résistances PvP fixes, sur les éléments neutre/terre/eau/air/feu.

### Expérience

- **`CharacterExperience.java`** — Ajout d'un setter contrôlé `setLevel(short)` pour les commandes GM/debug, avec recalage de l'XP sur le minimum du niveau.

## [Objets Dofus 1.29 - inventaire, sol, ramassage et accessoires] - 2026-05-11

### Phase 01 - protocole inventaire
- Correction du parsing des paquets objets officiels du client 1.29 : `OM`, `OD`, `Od`, `OU` et `Ou`.
- Alignement des positions d'equipement sur les slots officiels : sac `-1`, amulette `0`, arme `1`, anneaux `2/4`, ceinture `3`, bottes `5`, coiffe `6`, cape `7`, familier `8`, Dofus `9-14`, bouclier `15`.
- Correction des paquets serveur : `OAKO*O;...`, `OMuid|position`, `OQuid|quantity`, `ORuid`, `Owused|max`.
- Sauvegarde BDD de `character_items.position` et `quantity` apres equipement, desequipement ou modification de pile.

### Phase 02 - depose d'objets au sol
- Remplacement de la suppression temporaire `OD` par une vraie depose au sol.
- Ajout d'un etat serveur en memoire par map pour les objets poses au sol.
- Affichage map via `GDO+cell;templateId;0`.
- Envoi des objets deja presents aux joueurs qui chargent la map tant que le serveur tourne.

### Phase 03 - vraies cases adjacentes, messages IM et ramassage
- Correction des cellules adjacentes Dofus 1.29 autour du personnage : `-15`, `-14`, `+14`, `+15`.
- Refus de depose si l'objet est equipe, avec `Im1129`.
- Refus de depose si les quatre cellules autour du personnage sont occupees ou invalides, avec `Im1145`.
- Verification des collisions avec joueurs, PNJ, groupes de monstres et objets deja poses.
- Ajout du ramassage basique : retrait visuel `GDO-`, ajout inventaire `OA` ou `OQ`, recalcul pods `Ow`.
- Prise en charge d'un cas `GA500` pour interaction/clic sur cellule contenant un objet au sol.

### Phase 04 - restack et accessoires personnage
- Correction du restack au ramassage : deux objets de meme template et memes jets fusionnent dans la pile existante.
- Ajout de la generation des accessoires visibles depuis l'inventaire equipe.
- Broadcast de changement d'apparence via `Oa{characterId}|{accessories}`.

### Phase 05 - ordre officiel des accessoires
- Correction de l'ordre graphique lu par le client 1.29 : index 1 coiffe, index 2 cape, index 3 familier, index 4 arme, index 5 bouclier.
- Correction du decalage visuel qui pouvait afficher le familier sur la tete ou les couches sur de mauvais emplacements.

### Phase 06 - arme masquee en roleplay
- Conservation de l'arme equipee en inventaire et en BDD.
- Masquage volontaire du sprite d'arme dans les accessoires `GM/Oa` roleplay pour rester coherent avec l'affichage attendu.
- Conservation d'un slot arme vide dans la chaine d'accessoires pour ne pas redecaler les autres layers.

### Phase 07 - accessoires sur l'ecran de selection
- Ajout des accessoires equipes dans le paquet `ALK` de liste des personnages.
- L'ecran de selection affiche maintenant coiffe, cape, familier et bouclier avec le meme ordre que le roleplay.
- L'arme reste masquee dans cette chaine pour garder le meme comportement visuel que sur la map.

### Phase 08 - garde-fous d'equipement
- Ajout des refus d'equipement cote serveur avant modification d'inventaire.
- Refus si le niveau du personnage est inferieur au niveau requis de l'objet, avec `Im13`.
- Refus des slots invalides et des conditions basiques non satisfaites, avec `Im11`.
- Ajout d'un support optionnel des colonnes `conditions` / `criteria` / `criterions` dans `item_template`, sans casser les bases qui ne les ont pas.
- Ajout d'un support optionnel des colonnes `two_handed` / `is_two_handed` pour les armes a deux mains.
- Gestion des conflits arme a deux mains / bouclier : desequipement automatique de l'element incompatible et envoi des messages `Im78` / `Im79`.
- Refus d'equiper deux fois le meme Dofus dans les slots `9-14`.

### Phase 10 - recalcul des stats d'equipement
- Ajout du calcul des bonus/malus fournis par les objets equipes sans les melanger aux stats de base du personnage.
- Le paquet `As` envoie maintenant les colonnes `base,equipement,gift,buff,total` avec les bonus d'equipement reels.
- Recalcul automatique apres equipement, desequipement et remplacement d'objet.
- La vitalite equipee augmente maintenant les PV max affiches.
- La force equipee et les bonus pods `158` augmentent maintenant les pods max.
- Initiative et prospection prennent maintenant en compte l'equipement dans l'en-tete du paquet `As`.
- Gestion des principaux malus officiels : PA, PM, portee, initiative, prospection, vitalite, sagesse, force, intelligence, chance, agilite, pods et coups critiques.



## [Bots - Navigation par soleils, memoire de chemins et evaluation monstres] - 2026-05-11

### Navigation bots
- Ajout de `BotPathMemory.java` pour apprendre les chemins empruntes via les `map_triggers`.
- Les bots memorisent les transitions `map -> cellule soleil -> nextMap` dans `bot_paths.csv`.
- Les chemins sont scores selon leur usage, les maps jugees favorables et les maps jugees dangereuses.
- `BotBehavior.java` utilise maintenant les cellules soleil comme cible d'exploration au lieu de chercher uniquement un bord de map au hasard.
- Les bots marchent vers la cellule du trigger, declenchent le changement de map, puis enregistrent le passage.
- Le chemin vers le soleil est envoye en une seule action `GA1`, avec estimation du temps de marche avant changement de map.
- Ajout d'un verrou temporel de deplacement pour eviter qu'un bot relance un mouvement pendant son animation.
- Le deplacement aleatoire reste disponible en fallback si aucune sortie exploitable n'est connue.

### Evaluation des monstres
- Ajout de `BotMonsterStrategy.java` pour analyser les groupes de monstres visibles sur la carte.
- Evaluation locale basee sur le niveau, les PV, les statistiques du bot, la composition du groupe et la personnalite du bot.
- Les bots evitent davantage les cartes dont les groupes semblent trop dangereux pour leur profil.
- Les cartes avec groupes favorables renforcent positivement les chemins qui y menent.

### ChatGPT tactique
- Ajout de `BotAIService.getCombatAdvice(...)` pour demander un avis court a ChatGPT quand l'heuristique locale est incertaine.
- L'avis attendu est limite a `FIGHT` ou `AVOID` avec une raison courte.
- Les decisions restent cachees temporairement pour eviter de solliciter l'API a chaque tick.
- Le systeme continue de fonctionner en mode heuristique si OpenAI est desactive ou rate-limit.

### Initialisation
- `Main.java` initialise et sauvegarde la memoire des chemins au demarrage/arret serveur.

## [Débogage maps, monstres et diagnostic serveur] — 2026-05-11

### Base de données
- Reconstruction partielle de la base de données à partir du dossier `database` du projet et des données AncestraR.
- Recréation des tables et colonnes nécessaires au fonctionnement actuel du serveur.
- Découpage des fichiers SQL en plusieurs morceaux pour éviter les erreurs `max_allowed_packet`.
- Correction de problèmes d’encodage sur certaines colonnes `name`.
- Correction de la table `monster_grades` :
  - remise en cohérence des colonnes `grade` et `level` ;
  - suppression des conflits liés aux doublons de clé.
- Correction de la table `npc_replies` :
  - adaptation du schéma pour accepter plusieurs actions liées à une même réponse PNJ.

### Commandes joueur / admin
- Correction du système de commandes avec préfixe `.`.
- Correction du parsing des paquets `BM*|.commande|`.
- Ajout de la prise en charge de `.infos` en plus de `.info`.
- Correction de `.help`, qui était bloquée par le contrôle des droits admin.
- Séparation des commandes publiques et des commandes réservées aux administrateurs.

### Monstres
- Débogage du chargement des templates monstres depuis `monster_templates`.
- Débogage du chargement des grades depuis `monster_grades`.
- Ajout / correction du système de spawn des monstres depuis la table `monster_spawns`.
- Correction du chargement des groupes monstres via `map_id`.
- Ajout de l’appel à `MonstersData.spawnAll(map)` lors du chargement des cartes.
- Vérification que les groupes monstres sont bien ajoutés dans les objets `MapTemplate`.
- Correction de la logique évitant les doubles spawns grâce à `areMonsterGroupsSpawned()`.
- Ajout des groupes monstres au paquet `GM` envoyé lors du `GI`.
- Débogage des logs réseau pour vérifier que les maps n’envoyaient d’abord que le personnage joueur.
- Validation du fonctionnement attendu : les monstres doivent maintenant être chargés sur les maps concernées et envoyés au client.

### Maps
- Vérification du chargement des maps et de leur lien avec les spawns monstres.
- Correction de la logique pour charger les monstres au moment du chargement de la carte.
- Vérification du passage entre maps et de l’envoi des paquets `GDM`, `GI`, `GM`, `GDK`.

### Logs et diagnostic
- Préparation d’un système de logs structurés pour améliorer le débogage serveur.
- Idée d’un fichier de diagnostic dédié contenant :
  - classe concernée ;
  - méthode concernée ;
  - paquet réseau ;
  - personnage ;
  - map ;
  - message d’erreur ;
  - stacktrace.
- Objectif : faciliter l’analyse des bugs liés au réseau, aux maps, aux monstres, aux PNJ, aux objets, aux sorts, aux combats et à la base de données.
- Les logs devront rester strictement techniques et ne pas contenir de données sensibles.


## [Inventaire, Banque complète, Trade items, Fix MonsterGroup] — 2026-05-10

### Corrections de compilation

- **`MonsterGroup.java`** — Constructeur principal : `List<MonsterEntry>` → `List<? extends MonsterEntry>`. Java generics invariance : `List<Member>` (produit par `MonstersData.spawnAll()`) n'est pas assignable à `List<MonsterEntry>` même si `Member extends MonsterEntry`. Le wildcard `? extends` accepte les deux, sans modifier aucun site d'appel.
- **`MonstersData.loadGrades()`** — La requête SQL ne sélectionnait pas `kamas_min` et `kamas_max` alors que `MonsterGrade(18 params)` les attendait. Ajoutés dans le SELECT.

---

### Inventaire — `Characters` + `InventoryParser.java`

#### `Characters.java`
- Ajout du champ `private Inventory inventory = new Inventory()` avec `getInventory()` / `setInventory()`.
- Import `org.dofus.objects.items.Inventory` ajouté.
- Compatible avec le constructeur existant (initialisation lazy inline, pas de changement de signature).

#### `InventoryParser.java` (nouveau)
Parseur complet des paquets `O` (inventaire, équipement) :
- **`Oe{uid}|{position}`** — Équipe l'item dans son slot par défaut (déduit du `typeId`). Item déplacé → `OM`. Pods mis à jour → `Ow`.
- **`Ou{uid}`** — Déséquipe vers le sac → `OM`.
- **`Od{uid}|{qty}`** — Supprime un item (refus si équipé). `OR` si supprimé totalement, `OM` si quantité réduite. `ItemsData.delete()` ou `ItemsData.update()` selon le cas.
- Branchement `RolePlayHandler case 'O'` (l'ancien case commenté `parseObjectPacket` a été activé).

---

### Trade — transfert d'items complet

#### `Trade.execute()` — implémentation physique complète
- Récupère les `Inventory` des deux joueurs via `Characters.getInventory()`.
- Pour chaque `TradeSlot` de l'initiateur → `initInv.removeItem()` + `targetInv.addItem(template, qty)`.
- Pour chaque `TradeSlot` de la cible → `targetInv.removeItem()` + `initInv.addItem(template, qty)`.
- Import `org.dofus.objects.items.{Inventory, Item}` ajouté dans `Trade.java`.

---

### Banque — items complets

#### `BankParser.java` — dépôt et retrait d'items fonctionnels
- **`depositItem()`** : retire l'item du `Inventory` du personnage (`inv.removeItem()`), envoie `OR` ou `OM` au client, persiste en `bank_items` via `saveBankItem()` (upsert `ON DUPLICATE KEY UPDATE quantity + ?`).
- **`withdrawItem()`** : lit le `template_id` via `getBankItemTemplate()`, charge l'`ItemTemplate` depuis `ItemsData.getTemplate()`, crée l'item dans l'inventaire (`addItem()`), envoie `OA`, supprime la ligne `bank_items` via `removeBankItem()`.
- 3 méthodes privées BDD ajoutées : `saveBankItem()`, `getBankItemTemplate()`, `removeBankItem()`.
- Imports ajoutés : `ItemsData`, `Inventory`, `Item`, `ItemTemplate`.

---

### Combat — quitter en placement

#### `Fight.java`
- **`removeFighter(int fighterId)`** : retire le fighter de `fighters` et de `turnOrder`. Si l'une des équipes devient vide en phase PLACEMENT → appel de `cancelFight()`.
- **`cancelFight()`** : passe l'état à FINISHED, retire le fight du registre, broadcast `fA` (annulation).

#### `FightParser.leaveFight()` — implémentation complète
- Vérifie la phase PLACEMENT (sinon `fKE`).
- `fight.removeFighter(character.getId())`.
- Envoie `fKO` + `GI` au joueur qui part (retour sur la carte).
- Broadcast `fK{id}` aux autres combattants restants.

---

### Artisanat — craft avec vérification réelle des ingrédients

#### `CraftParser.craft()` — refonte complète
- Construit `Map<templateId, qty>` disponible depuis `character.getInventory().getBag()`.
- Multiplie les quantités requises par `qty` (crafts multiples).
- Vérifie `hasEnough(available, needed)` avant toute consommation.
- Consomme chaque ingrédient via `inv.removeItem()` + `OR`/`OM` + `ItemsData.delete/update`.
- Donne le résultat via `inv.addItem()` + `OA` + `MC+{tplId}|{qty}`.
- Envoie `Ow` (pods mis à jour) et `MK10` (XP placeholder).
- Code erreur `MC-3` : ingrédients insuffisants.
- Import `ArrayList`, `List`, `ItemsData`, `Inventory`, `Item`, `ItemTemplate` ajoutés.

---

### Échange — vérification inventaire dans `addToTrade`

#### `ExchangeParser.addToTrade()` — fix templateId + validation inventaire
- `uid` passé de `int` à `long` (cohérent avec `Item.uid`).
- Lookup `character.getInventory().getByUid(uid)` pour vérifier que l'item existe, n'est pas équipé et a la quantité requise.
- `templateId` réel transmis à `Trade.addItem()` (au lieu de `0`).
- Import `Item` ajouté.

---

### Chargement d'inventaire au login

#### `CharactersData.load()` — inventaire branché
- Appelle `ItemsData.loadForCharacter(character.getId())` après chaque personnage chargé.
- Si des items existent → crée un `Inventory`, charge les items, assigne via `character.setInventory()`.
- Import `Inventory`, `Item`, `List` ajoutés.

#### `GameScreenHandler` / `RolePlayHandler` — inventaire + `Ow` au login
- Les items sont fournis dans `ASK` et `RolePlayHandler` envoie seulement `Ow` pour les pods.
- Envoi `Ow{usedPods}|{maxPods}` (pods réels calculés depuis l'inventaire).
- Remplace l'ancien `Ow0|{maxPods}` hardcodé.

---

### Banque — liste des items à l'ouverture

#### `BankParser.openBank()` — items banque dans Bd
- Appelle `appendBankItems(character, sb)` pour construire la liste des items de banque.
- `appendBankItems()` : requête `SELECT id, template_id, quantity FROM bank_items WHERE account_id=?`, format `id~templateId~qty~-1~` séparé par `|`.
- Paquet `Bd{kamas}|{items}` maintenant complet.

---

## [Combat, Drops, Échange, Banque, Artisanat, Anti-spam] — 2026-05-10

### Moteur de combat — améliorations majeures

#### `Fight.java` (réécriture partielle)
- **Ordre de tour par initiative** : tri décroissant `agilité + random(wisdom/10)` — remplace le shuffle aléatoire pur.
- **IA monstres intégrée** : si le fighter courant est un monstre, `MonsterAI.playTurn()` est appelé directement après `startTurn()`.
- **`handleSpell(fighter, spellId, args)`** : implémentation complète. Parse la cellule cible (base64 Dofus), vérifie les PA, applique les effets via `SpellEffect.roll()` avec bonus offensif calculé depuis les stats du caster. Broadcast des packets `GA{effectId}` + `GA306` (mise à jour vie) + `GA402` (mort).
- **`endFight()` complet** : XP divisée entre les vainqueurs + envoi paquet, kamas rollés via `DropTable.rollKamas()`, drops via `DropTable.roll()`, `RegenService.start()` après le combat, retrait du groupe monstre de la carte + `MapRespawnService.scheduleRespawn()`, sauvegarde `CharactersData.update()`.
- **`calculateTeamProspection()`** : somme chance/10 + stat prospection (176) de chaque fighter.
- **Null-safe** : `turn.getTurnTimer()` vérifié avant `cancel()`.

#### `MonsterAI.java` (nouveau)
IA de base pour les monstres pendant un combat :
- Cherche l'ennemi le plus proche (distance Manhattan).
- Se déplace vers lui en consommant des PM (pathfinding ligne droite, grille isométrique).
- Attaque au corps-à-corps si adjacent (≥ 3 PA) : dégâts = `strength/10 + 1`, élément neutre.
- Broadcast `GA303` (attaque), `GA306` (MAJ vie), `GA402` (mort si applicable).
- Termine toujours par `FightTurn.endTurn()`.

#### `DropTable.java` (nouveau)
Système de drops avec probabilités et prospection :
- Taux sur 10 000 (5000 = 50%). Bonus prospection : `taux × (prospection/100)`.
- `roll(monsterTemplateId, prospection)` → `List<DropResult>`.
- `rollKamas(grade, prospection)` → kamas aléatoires entre kamasMin et kamasMax × bonus.
- `loadDefaults()` : drops hardcodés de départ (Tofu → oreille, Larve → bois, Bouftou → bois).
- `register()` / `addDrop()` : alimentation depuis BDD ou code.

#### `FightParser.java` — initiation de combat
- `initiateFightVsMonsters(character, session, groupId)` : pipeline complet d'initiation.
  - Retire le groupe de la carte + notifie tous les joueurs (`GM|-groupId`).
  - Crée le `Fight`, ajoute le joueur (team 0) et tous les monstres du groupe (team 1) comme `Fighter`.
  - Envoie `fJK{fightId}` + `fL{fighters}` au joueur, puis démarre le combat.
- `getFightForCharacter()` rendu package-visible (utilisé par `RolePlayHandler`).

#### `RolePlayHandler.java` — routage combat
- `case 'G'` / `parseGamePacket()` :
  - **`GA`** : si le personnage est en combat → `FightParser.parseAction()`, sinon → `GameParser.action()`.
  - **`GR{groupId}`** : attaque un groupe de monstres → `FightParser.initiateFightVsMonsters()`.
  - **`GS`** : passer son tour en combat → `FightParser.parseFightPacket("fN")`.

---

### MonsterTemplate / MonsterGroup — corrections et enrichissements

- **`MonsterTemplate.MonsterGrade`** : ajout de `kamasMin`, `kamasMax`, `getXpBase()` (alias `getXp()` conservé). Constructeur mis à jour (17 paramètres).
- **`MonsterGroup`** : refonte complète.
  - `Member` devient `MonsterEntry` (alias `Member extends MonsterEntry` conservé pour rétrocompat).
  - Constructeur léger `(int id, short cell, EOrientation)` pour `MapRespawnService` — orientation null → `EOrientation.SOUTH`.
  - `addMember(template, grade)` public, `getCell()` / `setCurrentMap()` ajoutés.
  - `getActorId()` retourne `groupId`, `getActorType()` = 3.

---

### MapRespawnService.java (nouveau)
Réapparition automatique des monstres après un combat :
- `scheduleRespawn(map, group)` : planifie le respawn dans `DEFAULT_RESPAWN_SEC` = 600s (10 min).
- `scheduleRespawn(map, group, delaySec)` : surcharge avec délai personnalisé.
- Crée un nouveau `MonsterGroup` avec la même composition, l'ajoute à la carte et notifie tous les joueurs (`GM|+{entry}`).
- `shutdown()` ajouté dans `Main.stop()`.

---

### WorldData — `getCharacterById(int id)` (ajout)
Lookup O(1) par ID — manquait et nécessaire pour `Fight`, `RegenService`, `MapRespawnService`.
TOCTOU `containsKey+put` dans `addCharacterById()` remplacé par `putIfAbsent`.

---

### Système d'échange joueur-joueur — `Trade.java` + `ExchangeParser.java`

#### `Trade.java` (nouveau)
Machine d'état d'échange avec registre statique (`characterId → Trade`) :
- `addItem()` / `removeItem()` : gestion des slots d'échange avec invalidation de la validation.
- `setKamas()` : vérification kamas suffisants + limite `MAX_KAMAS = 1G`.
- `validate()` : retourne `true` quand les deux joueurs ont validé.
- `execute()` : transfert physique des kamas (items TODO Inventory).
- `getTradeFor()` / `removeTrade()` : accès registre.

#### `ExchangeParser.java` (implémentation complète)
- `EX` : demande d'échange — vérifie même map, cible connectée, joueur non déjà en échange. Envoie `EX{id}` aux deux côtés.
- `EA` / `ER` : ajout/retrait d'items avec `PacketValidator.validateItemId()`. Notify les deux.
- `Em` : kamas mis sur la table avec `PacketValidator.validateKamas()`.
- `EK` : validation — si les deux ont validé → `Trade.execute()` + `Of=` kamas MAJ + `EV` fermeture.
- `EV` : annulation propre des deux côtés.
- Boutique PNJ (EW/EB/ES) : stubs maintenus.

---

### Banque — `BankParser.java` (nouveau) + `bank_system.sql`

- Ouverture coûte `OPEN_COST = 10` kamas (taxe Dofus 1.29).
- `Bd` : ouvre la banque, envoie solde banque + items (items TODO Inventory banque).
- `Bk` / `Bq` : dépôt / retrait kamas avec validation range et kamas suffisants.
- `Bi` / `Bo` : dépôt / retrait items (placeholder, Inventory non branché).
- Persistance BDD : colonne `bank_kamas` dans `accounts` + cache `kamasCache` par compte (évité à la déconnexion via `evictCache()`).
- Branchement `RolePlayHandler` : cases `'d'`, `'k'`, `'q'`, `'i'`, `'o'` dans `parseBasicsPacket()`.
- `BankParser.evictCache()` + `ChatFilter.remove()` + `RegenService.stop()` appelés dans `onClosed()`.
- **`bank_system.sql`** : `ALTER TABLE accounts ADD bank_kamas` + `bank_items` + `monster_drops` (table optionnelle avec données démo).

---

### Anti-spam chat — `ChatFilter.java` (nouveau)

Filtre stateful branché dans `BasicParser.channelsMessage()` **après** `PacketValidator` :

| Règle | Seuil |
|---|---|
| Doublon | Même message refusé si < 4 secondes |
| Flood | > 5 messages en 3 secondes |
| Caps | > 70% de majuscules sur ≥ 6 caractères |
| Répétition | > 5 fois le même caractère consécutif |

- `allow(characterId, message)` → `boolean`.
- `remove(characterId)` → nettoyage à la déconnexion.
- Ne bloque pas les commandes admin (`.`).

---

### Artisanat — `CraftParser.java` + `CraftRecipe.java` + `craft_system.sql`

#### `CraftRecipe.java`
- `JobType` enum : 15 métiers Dofus officiels (Alchimiste → Forgeur de boucliers).
- `canCraft(Map<templateId, qty>)` : vérification des ingrédients.

#### `CraftParser.java`
- `load()` : chargement depuis `craft_recipes` + `craft_ingredients` (JOIN). Appelé dans `Initialisation.init()`.
- `ML{jobId}` : liste des recettes du métier (format `recipeId|resultId|resultQty|ing,qty;...~`).
- `MC{recipeId}|{qty}` : lancer un craft — validation, accord placeholder `MC+`, `MK10` XP.
- `Mx{jobId}` : infos métier du personnage (niveau, XP — TODO BDD).

#### `craft_system.sql`
- `craft_recipes`, `craft_ingredients`, `character_jobs`.
- Données démo : Pain d'orge (boulanger) + Bois de Frêne (bûcheron).

---

### Métriques — `ServerMetrics.java` (enrichissement)
- `getActiveFights()` : nombre de combats actifs depuis `Fight.getActiveFights()`.
- `getBotCount()` : nombre de bots (ID < 0) dans `WorldData.getCharacters()`.
- `getRateLimiterSessions()` : sessions suivies par `RateLimiter`.
- `getSummary()` enrichi : fights, bots, sessions rate-limiter.
- Compteurs paquets branchés dans `Game.messageReceived()` et `messageSent()`.

---

### `PacketValidator` — wiring dans les parsers
- **`BasicParser.channelsMessage()`** : `validateChatMessage()` + `ChatFilter.allow()` avant toute diffusion.
- **`BoostParser.boost()`** : `validateStatBoost()` → `BN` si statId inconnu ou personnage sans points.
- **`ExchangeParser`** : `validateItemId()` sur chaque EA, `validateKamas()` sur Em.
- **`BankParser`** : `validateKamas()` sur Bk/Bq, `validateItemId()` sur Bi/Bo.

---

## [Stabilité, SQL & Anti-hack] — 2026-05-09

### Anti-hack — Limiteur de débit (`RateLimiter.java`)

Fenêtre glissante d'1 seconde par session MINA. Seuils :

| Seuil | Action |
|---|---|
| > 20 pkt/s | Log WARN (alerte sans bannissement) |
| > 30 pkt/s | Ban 5 secondes + compteur bans consécutifs |
| 3 bans consécutifs | Purge de l'état + signal `isTracked()` → déconnexion forcée |

- `allow(sessionId)` — à appeler avant tout traitement de paquet ; retourne `false` si la session est throttlée.
- `reset(sessionId)` — réinitialise le compteur de bans après une période calme.
- `remove(sessionId)` — nettoyage à la déconnexion.
- `isTracked(sessionId)` — retourne `false` après purge par 3 bans, signal pour fermer le socket.
- Nettoyage automatique toutes les 60 s des sessions inactives.

**Branchement `Game.java`** :
- `messageReceived()` — appelle `RateLimiter.allow()` avant `parse()`. Si non suivi → `session.close(false)` (déconnexion flood excessif). Si seulement throttlé → `return` silencieux.
- `sessionClosed()` — appelle `RateLimiter.remove()` pour libérer la mémoire.

---

### Anti-hack — Validateur de paquets (`PacketValidator.java`)

Validation centralisée réutilisable depuis n'importe quel parser. Toutes les méthodes sont statiques, retournent `boolean` et logguent un WARN tracé avec l'ID de session en cas d'échec.

| Méthode | Règle |
|---|---|
| `validateRaw(session, packet)` | Paquet non null, non vide, ≤ 512 caractères |
| `validateCellId(session, cellId)` | cellId dans [0, 559] (cartes 33×17 Dofus) |
| `validateChatMessage(session, msg)` | Non vide, ≤ 255 chars, pas de caractères de contrôle (`\0`, `\n`, `\r`) |
| `validateStatBoost(session, statId, amount)` | statId dans {10–15}, amount dans [1, 20 000] |
| `validateDialogActor(session, actorId)` | actorId ≥ 100 000 (offset NPC attendu) |
| `validateFightAction(session, packet)` | Paquet GA ≥ 5 caractères |
| `validateKamas(session, kamas)` | kamas dans [0, 1 000 000 000] |
| `validateItemId(session, itemId)` | itemId > 0 |

---

### Corrections de stabilité (portabilité Java 8)

- **`Fighter.java`** — `switch` expression (Java 14+) remplacé par `switch/case/break` classique pour le calcul des résistances par élément. Compatible Java 8+.
- **`BotLearning.java`** — `logger.debug("score={:.2f}", ...)` invalide (SLF4J ne supporte que `{}`). Remplacé par `String.format("%.2f", lp.score())` passé comme argument.
- **`RegenService.java`** — `character.getBreed().getLife()` (vie de base brute) remplacé par `character.getLifeMax()` qui applique correctement `base + 5*(level-1) + vitalité`. Résultat : la regen est proportionnelle aux vrais PV max du personnage.
- **`MapTemplate.java`** — Champ `monsters` (`ConcurrentHashMap<Integer, MonsterGroup>`) et méthodes `addMonsterGroup()`, `removeMonsterGroup()`, `getMonsterGroups()` ajoutés. Requis par `MonstersData.spawnAll()`.
- **`Initialisation.java`** — Chargeurs `ItemsData.load()`, `SpellsData.load()`, `MonstersData.load()`, `GuildsData.load()` ajoutés dans l'ordre de dépendance correct.

---

### Schémas SQL — Nouveaux systèmes

#### `sql/item_system.sql`
- `item_templates` — id, name, type (0-11), level, weight, price, two_handed, eth, effects CSV.
- `character_items` — uid auto, owner (charId), template_id, quantity, position (63=sac, 1-13=équipement), effects rollés.
- 10 items démo (armes, armures, ressources, consommable).

#### `sql/monster_system.sql`
- `monster_templates` — id, name, gfxId, colors, align, race, canTackle.
- `monster_grades` — (monsterId, grade) PK composite. Stats complètes : life, AP, MP, 6 caractéristiques, 5 résistances %, XP, kamas min/max.
- `monster_spawns` — mapId, cellId, orientation, composition multi-monstres (`monsterId~grade,…`).
- 7 monstres démo (Tofu, Larves x3, Bouftou, Bouftou Royal, Crabe) avec grades 1-3 et spawns sur map 7411.

#### `sql/spell_system.sql`
- `spell_templates` — id, name, sprite Flash, spriteArg, description.
- `spell_levels` — (spellId, level) PK composite. AP, portée min/max, CC/CF (1/N), lineOnly, lineOfSight, freeCell, modifiableRange, maxPerTurn, cooldown, effects CSV normaux + CC.
- Sorts démo : Épée Céleste (Iop niveaux 1-6), Torgnole (Iop niveaux 1-6), Mot Stimulant / Mot Ravivant (Eniripsa), Flèche Percante (Cra).

#### `sql/guild_system.sql`
- `guilds` — id, name (UNIQUE), emblem (bg/mg/fg shape+color), level, xp, leaderId, createdAt.
- `guild_members` — characterId PK, guildId FK cascade, rank (0-4), rights bitmask, xpGiven, xpPercent, joinedAt, lastLogin.
- Données démo : guilde "Les Aventuriers" niveau 3, chef + bras droit.

#### `sql/quest_system.sql`
- `quest_templates` — id, name, description, repeatable, levelRequired.
- `quest_steps` — id, questId, stepOrder, name, description.
- `quest_objectives` — id, stepId, type (0=PNJ/1=kill/2=collect/3=map), targetId, quantity, description.
- `quest_rewards` — id, stepId, kamas, xp, itemId, itemQty.
- `character_quests` — (characterId, questId) PK, stepId courant, status (0/1/2), timestamps.
- Données démo : quête "Première Mission" 3 étapes (parler garde → tuer 3 Tofus → retourner), quête répétable "Chasse aux Larves".

---

## [Scaffolding — Toutes les couches manquantes] — 2026-05-08

### Commandes admin — `AdminParser.java` (nouveau)

Commandes préfixées `.` dans le canal général (ex : `.kick Torvan`).
Guard : `Right.canMoveAllDirections()` (bit 8192 = GM).

| Commande | Effet |
|---|---|
| `.help` | Liste toutes les commandes |
| `.info` | Uptime, joueurs, RAM (via `ServerMetrics`) |
| `.kick <n>` | Déconnecte un joueur (closeNow) |
| `.ban <n>` | Kick + flag BDD (persistance TODO) |
| `.unban <n>` | Débannit (BDD TODO) |
| `.mute / .unmute <n>` | Retire / rend le droit de parole |
| `.goto <n>` | Téléporte le GM vers un joueur |
| `.bring <n>` | Amène un joueur vers le GM |
| `.tp <mapId> [cell]` | Téléporte le GM sur une map précise |
| `.kamas <n> <m>` | Donne des kamas + sauvegarde BDD |
| `.level <n> <lvl>` | Fixe le niveau (recalcul TODO) |
| `.god / .invis` | Invulnérabilité / invisibilité (TODO combat) |
| `.announce <msg>` | Diffuse `Im036;msg` à tous les joueurs |
| `.reload / .speed` | Rechargement / vitesse bots (TODO) |

Branchement : `BasicParser.channelsMessage()` → si message commence par `.` → `AdminParser.parse()`.
`RolePlayHandler` : `channelsMessage` passe désormais `client` en argument (refactoring mineur).

---

### Système d'inventaire

- **`ItemEffect.java`** — Effet d'objet (effectId, dice, min, max, special). Constantes des IDs courants. `roll()` pour valeur aléatoire.
- **`ItemTemplate.java`** — Template global (BDD `item_template`). `buildEffectsString()` pour le paquet OL.
- **`Item.java`** — Instance (uid unique, effets rollés, position slot). `toOLEntry()` pour OL/OA/OM. Stacking automatique des ressources.
- **`Inventory.java`** — Inventaire complet : bag, slots équipement, poids, mutations (add/remove/equip/unequip). Packets OL/OA/OM/OR/Ow.
- **`ItemsData.java`** — Chargement `item_template` + `character_items`. CRUD complet (insert, update, delete). Parsing d'effets depuis chaîne CSV/format Dofus.
- Alignement items avec la base `dofus_complete.sql` : lecture/ecriture des effets Dofus officiels `effect#p1#p2#p3#dice` separes par virgule, tout en gardant la compatibilite avec l'ancien CSV interne.
- Correction des type_id/slots d'equipement Dofus 1.29 : amulette 3, armes 4, anneaux 5/7, ceinture 6, bottes 8, chapeau 1, cape 2, familier 9, bouclier 10.
- Persistance des items crees par craft et drops de combat : creation d'items reels en inventaire, paquet `OAKO` officiel et sauvegarde/update BDD.
- Alignement inventaire sur StarLoco : les entrees item sont encodees en hex (`guid~template~qty~pos~stats;`), `OAKO` est utilise pour les ajouts et `OMuid|pos` pour les deplacements.
- Les items sont maintenant injectes dans le paquet `ASK` de selection personnage comme sur StarLoco, et le `OL` separe au login n'est plus envoye.
- Verification avec `ressources/Dofus-client-1.29-master` : le client lit les items du `ASK` via `items.split(";")` en commencant a l'index 1, donc la liste d'items envoyee dans `ASK` est prefixee par `;` pour que le premier objet ne soit pas ignore.

---

### Système de monstres

- **`MonsterTemplate.java`** — Template monstre + grades. `MonsterGrade` inner class (stats, résistances, XP par grade).
- **`MonsterGroup.java`** — Groupe sur carte (`IActor`). Composition multi-monstres. `toGMEntry()` format Dofus. `isDead()`, `getTotalXp()`.
- **`MonstersData.java`** — Chargement `monster_templates` + `monster_grades`. `spawnAll(MapTemplate)` depuis `monster_spawns`. TODO : ajouter `addMonsterGroup()` dans `MapTemplate`.

---

### Système de combat

- **`Fighter.java`** — Combattant (joueur/bot/monstre). Stats de combat, `takeDamage()` avec résistances %, `heal()`, `spendAP/MP()`, `resetTurn()`.
- **`FightTurn.java`** — Gestion du tour courant. Timer 30 s auto-pass. `startTurn()` → `fTH`. `endTurn()` → `fTN` + appel `Fight.nextTurn()`.
- **`Fight.java`** — Moteur principal. États PLACEMENT/ACTIVE/FINISHED. Registre statique `activeFights`. `startFight()`, `nextTurn()`, `handleAction()`, `endFight()`. `broadcast()` vers tous les participants.
- **`FightParser.java`** — Packets `fK` (quitter), `fN` (passer tour), `fP{cell}` (placement). `parseAction()` pour `GA` en combat.
- **`RolePlayHandler`** — `case 'f'` décommenté → `FightParser.parseFightPacket()`.

---

### Système de sorts

- **`SpellTemplate.java`** — Template sort + niveaux 1-6. `SpellLevel` (coûts, portée, zone, critique, cooldown). `SpellEffect` (effectId, dice, zone, élément, `roll()`).
- **`SpellsData.java`** — Chargement depuis `spell_templates` + `spell_levels`. `buildSLPacket(spellBook)` pour le paquet SL.

---

### Services utilitaires

- **`RegenService.java`** — Régénération de vie hors combat. Délai 10 s avant démarrage, tick toutes les 2 s, `floor(maxLife/10)` PV/tick. Arrêt automatique à vie pleine. `start()/stop()/shutdown()`.
- **`ServerMetrics.java`** — Métriques : uptime, joueurs en ligne, peak, connexions totales, paquets recv/sent, RAM. `getSummary()` pour logs. Utilisé par `AdminParser.cmdInfo()`.

---

### Système d'échange

- **`ExchangeParser.java`** — Squelette complet packets `E*`. Boutique PNJ (`EW`, `EB`, `ES`) et échange joueur-joueur (`EX`, `EA`, `ER`, `EK`). Tous retournent `BN` pour l'instant.
- **`RolePlayHandler`** — `case 'E'` → `ExchangeParser.parse()`.

---

### Système de guildes

- **`Guild.java`** — Guilde complète : membres, leader, XP. Packets `gI` (info), `gL` (membres).
- **`GuildMember.java`** — Membre avec rang, droits bitmask, XP guilde, online/offline.
- **`GuildsData.java`** — CRUD complet : `load()`, `create()`, `addMember()`, `removeMember()`, `save()`. Index `memberIndex` (charId → guildId) pour lookup O(1).
- **`GuildParser.java`** — Packets `gC` (créer), `gD` (dissoudre), `gI` (info), `gJ` (rejoindre), `gK` (exclure), `gL` (membres).
- **`RolePlayHandler`** — `case 'g'` → `GuildParser.parse()`.

---

### Système de quêtes

- **`QuestTemplate.java`** — Template quête multi-étapes. `QuestStep` (objectifs + récompenses). `QuestObjective` (TALK_NPC, KILL_MONSTER, COLLECT_ITEM, REACH_MAP). `QuestReward` (kamas, XP, item).
- **`QuestParser.java`** — Packets `QF` (démarrer), `Qf` (abandonner), `QL` (liste). Hooks `onMonsterKilled()`, `onNpcTalked()`, `onItemCollected()` pour progression automatique.
- **`RolePlayHandler`** — `case 'Q'` → `QuestParser.parse()`.

---

## [Bot IA — Personnalités, Social, OpenAI, Auto-apprentissage] — 2026-05-08

### Nouveau système complet de bots intelligents

#### BotPersonality.java (nouveau)
Enum à 4 valeurs (EXPLORER, SOCIAL, MERCHANT, WARRIOR) avec 5 poids comportementaux :
- `exploreWeight` — probabilité de chercher un changement de map
- `talkWeight` — fréquence de chat spontané
- `replyWeight` — probabilité de réagir à un message de map
- `groupWeight` — propension à rejoindre un groupe
- `followWeight` — probabilité de suivre un ami vers une nouvelle map

#### BotConversation.java (nouveau)
Pools de messages statiques par personnalité et par contexte :
- GENERAL, EXPLORE_START, ARRIVE, REACT, PM_REPLY — 5 contextes couverts
- `FOLLOW_ANNOUNCE[]` — templates avec %s pour le nom de l'ami suivi
- `pick(String[])` — tirage aléatoire, retourne "..." si pool vide

#### BotBehavior.java (réécriture complète)
FSM à deux états (WANDERING / EXPLORING) pilotée par les poids de personnalité :
- `tick()` — décide l'état à chaque tick selon `exploreWeight` et la présence de triggers
- `doWanderMove()` / `doExploreMove()` — directions isométriques, 1-3 pas
- `botChangeMap()` — annonce départ, retire de l'ancienne map, téléporte, annonce arrivée, notifie BotSocial
- `performMapChange()` — changement de map forcé (suivi d'ami, sans annonce d'exploration)
- `onMapMessage()` — réaction probabiliste aux messages des joueurs via BotAIService
- `scheduleMoves()` / `scheduleTalk()` — intervalles dynamiques selon personnalité
- Intégration `BotLearning.onBotSpoke()` dans `talkBot()` (toute phrase parlée entre dans le tracker)

#### BotSocial.java (nouveau)
Graphe social entre bots :
- `friendships` — ConcurrentHashMap botId → Set<botId> (bidirectionnel)
- `botMapCache` — position courante de chaque bot
- `addFriendship()` / `addFriendGroup()` — enregistrement des liens
- `onBotChangedMap()` — notifie les amis présents sur l'ancienne map pour décision de suivi
- `scheduleFollow()` — annonce puis exécute le suivi via `BotBehavior.performMapChange()`

#### BotAIService.java (nouveau)
Service OpenAI optionnel avec fallback automatique :
- Configuré via `config.properties` : `bot.ai.enabled`, `bot.ai.key`, `bot.ai.model`
- Rate-limiting : 1 appel par bot par minute maximum
- Appel HTTP asynchrone (`HttpURLConnection`, compatible Java 8, sans dépendance externe)
- Body JSON construit manuellement avec system prompt de personnalité
- Parsing de la réponse par regex (pas de bibliothèque JSON)
- Fallback sur `BotConversation.getPMReply()` en cas d'erreur ou rate-limit
- Soumission automatique à `BotLearning.submitPhrase()` après chaque réponse OpenAI réussie

#### BotLearning.java (nouveau) — Auto-apprentissage par renforcement
Moteur d'apprentissage collectif : les bots apprennent quels messages attirent des réponses.
- **Renforcement positif** : si un joueur parle dans les 20 secondes après un bot → +1 réaction sur la phrase du bot
- **Score de Laplace** : `(reactions + 1) / (uses + 2)` — stable même avec peu d'observations
- **Tirage pondéré** : phrases avec meilleur score sélectionnées plus souvent
- **Seuil de maturité** : une phrase doit avoir ≥ 3 utilisations pour entrer dans le pool de sélection
- **Taux dynamique** : la proportion de phrases apprises utilisées augmente avec la taille du pool (35% → max 70%)
- **Pruning** : les phrases au score le plus faible sont éliminées quand le pool atteint 100 entrées/personnalité
- **Persistance** : sauvegarde automatique toutes les 5 minutes dans `bot_memory.csv`, rechargé au démarrage
- **Format CSV** : `PERSONALITY|uses|reactions|phrase` — aucune dépendance externe
- `submitPhrase()` — permet à OpenAI d'alimenter le pool avec ses réponses générées

#### BotAI.java (complété)
- `getPersonality(int botId)` — accès global à la personnalité d'un bot
- `spawnAll()` — extrait la personnalité (10ème champ BOT_CONFIGS), l'enregistre dans `personalities`
- Groupes d'amis automatiques : les bots de même personnalité sont liés via `BotSocial.addFriendGroup()`
- Lien inter-personnalité : tous les SOCIAL ↔ tous les EXPLORER (curieux aiment discuter)

#### BasicParser.java (modifié)
- Canal général (`cM*`) : après diffusion, appelle `BotLearning.onPlayerSpoke(mapId)` et `BotBehavior.onMapMessage()` pour chaque bot présent sur la map

#### Main.java (modifié)
- `BotAIService.configure(config)` — lecture de la config OpenAI au démarrage
- `BotLearning.init()` — charge `bot_memory.csv` avant le spawn des bots
- `BotLearning.shutdown()` — sauvegarde finale à l'arrêt du serveur

#### config.properties (modifié)
- Ajout des clés `bot.ai.enabled`, `bot.ai.key`, `bot.ai.model` (désactivé par défaut)

---

## [Fix NPC GM format + Dialog complet] — 2026-05-08

### Correction écran noir — format GM des PNJ

- **`GProtocol.getNpcPattern()`** — Réécriture complète. Le format GM Dofus 1.29 nécessite 14 champs pour les PNJ. Les champs manquants `templateId` (champ 5) et `-4` (discriminant de type NPC, champ 6) provoquaient une exception Flash silencieuse → écran noir. Format correct : `cell;dir;0;actorId;templateId;-4;gfxId^scale;sex;c1;c2;c3;accessories;-1;0`. Scale : `gfxId^size` si scaleX==scaleY, sinon `gfxId^scaleXxscaleY` (lettre 'x', pas '^').

- **`GameParser.creation()`** — Restauration du `fC0` dans `creation()` : AncestraRemake l'envoie en phase GC ET en phase GI (après GDK). Son absence en GC causait un état Flash incohérent.

- **`GameParser.information()`** — Ordre des acteurs corrigé : joueurs + bots d'abord, puis PNJ. L'ordre inverse plantait le rendu sur certains clients.

### Fix dialogue NPC complet

- **`DialogParser.create()`** — `DCK|0` → `DCK{actorId}` : le client charge le sprite NPC par son ID. Sans l'actorId, fenêtre s'ouvre sans sprite.

- **`DialogParser.reply()`** — Le client envoie `DR{questionId}|{replyId}`. Le code parsait `packet.substring(2)` comme entier → NumberFormatException sur `"3|101"` → réponse silencieusement ignorée. Fix : split sur `|`, parseInt sur la partie droite.

- **`NpcData.java`** — Ajout de `allQuestions` et `allReplies` (ConcurrentHashMap globaux). Les questions/réponses Dofus 1.29 sont un pool global référencé par ID, pas par template. `DialogParser` utilise désormais `NpcData.getQuestion()` / `NpcData.getReply()` → résolution des chaînes de dialogue cross-templates.

---

## [BotClient — bots citoyens complets] — 2026-05-08

### Architecture BotSession / BotClient / BotPacketHandler

- **`BotSession.java`** (nouveau) — Implémente `IoSession` de MINA sans connexion réseau. `write(Object)` route le packet vers `BotPacketHandler`. `isConnected()` retourne `true`. `getAttribute/setAttribute` backé par `ConcurrentHashMap` (compatible avec le code existant). Toutes les méthodes réseau/statistiques sont stubées à 0/null/false — MINA ne les invoque jamais puisque `BotSession` n'est pas géré par un `IoProcessor`.

- **`BotClient.java`** (nouveau) — Étend `GameClient` (compatibilité totale : `instanceof`, `getCharacter()`, `getSession()`...). Le constructeur passe `null` pour le `Game` (non utilisé pour les bots) et une `BotSession` comme session. `register()` appelle `WorldData.addSessionByAccount()` et `WorldData.addController()` — le bot est vu comme un joueur connecté par toutes les boucles de broadcast.

- **`BotPacketHandler.java`** (nouveau) — Traite les packets que le serveur "envoie" au bot via `BotSession.write()`. Les bots appellent directement les méthodes serveur au lieu de passer par le réseau :
  - `PIK...` (invitation de groupe) → `bot.setInvitation()` puis `PartyParser.accept()` après 1-3 secondes de délai simulé
  - `cMKF...` (message privé reçu) → 40% de chance de répondre avec un message aléatoire après 2-8 secondes

- **`BotBehavior.java`** — Ajout de `schedule(Runnable, long, TimeUnit)` pour permettre à `BotPacketHandler` de planifier des réponses différées sur le même thread pool bot.

- **`BotAI.java`** — Création et enregistrement de `BotClient` dans `create()`. Le bot est maintenant dans `WorldData.getSessionByAccount()` → broadcast loops l'incluent automatiquement.

**Résultat** : les bots sont de vrais participants du serveur. On peut leur envoyer des invitations de groupe (auto-accepté), leur écrire en privé (réponse aléatoire). Architecture prête pour l'ajout du combat (il suffira d'ajouter un cas `GA` dans `BotPacketHandler`).

---

## [Système de bots] — 2026-05-08

### BotAI + BotBehavior — 10 bots actifs

- **`BotAI.java`** — Réécriture complète. `AtomicInteger BOT_ID` commençant à -1000 et décrémentant pour garantir des IDs uniques et négatifs (pas de collision avec les vrais personnages). 10 bots prédéfinis dans `BOT_CONFIGS[][]` avec nom, map, cellule, race, genre, 3 couleurs et niveau. `spawnAll()` itère les configs, crée chaque bot via `create()`, démarre `BotBehavior.start()` dessus et loggue les erreurs individuellement sans stopper les autres. La vie initiale tient compte du niveau : `baseLife + 5 * (level - 1)`.

- **`BotBehavior.java`** (nouveau) — `ScheduledExecutorService` à 2 threads daemon (`"bot-ai"`). Deux boucles indépendantes par bot :
  - **Déplacement** : toutes les 8-30 secondes, choisit une direction aléatoire parmi 4 (EAST +1, SOUTH_EAST +14, WEST -1, NORTH_WEST -14), 1-3 pas. Construit le chemin en format Dofus (`a{startEncoded}{dirChar}{cellEncoded}...`), diffuse `GA1;1;{id};{path}` à tous les joueurs sur la map, puis met à jour la position du bot après `steps * 300ms` via un nouveau timer (simulate end of animation).
  - **Message** : toutes les 40-120 secondes, choisit un message dans un pool de 24 phrases Dofus-style et diffuse `cMK|{id}|{name}|{message}` à tous les joueurs sur la map.
  - Délais initiaux aléatoires (5-25s move, 15-60s talk) pour que les bots ne démarrent pas tous en même temps.
  - `broadcastToMap()` utilise une copie `ArrayList` de `getActors().values()` pour éviter ConcurrentModificationException.
  - `shutdown()` appelé dans `Main.stop()`.

- **`Main.java`** — `BotAI.spawnAll()` remplace l'ancien `BotAI.botAI("BOT", ...)`. `BotBehavior.shutdown()` ajouté dans `stop()`.

---

## [Audit stabilité — vague 2] — 2026-05-08

### NPE critiques

- **`PartyParser.java`** — Réécriture complète : `accept()`, `invitation()`, `refuse()`, `leave()` avaient des NPE sur chaque lookup `WorldData.getCharacterByName().get()` et `WorldData.getSessionByAccount().get()` sans vérification null. Tous les accès sont maintenant gardés. `invitation()` retourne immédiatement si la cible est null/déconnectée. `refuse()` gère correctement le cas où `getInvitation()` est null. `leave()` protège le kick via try/catch sur le parseInt.

- **`Party.java`** — Tous les `WorldData.getSessionByAccount().get()` dans `leave()` manquaient de null checks → NPE si un joueur s'est déconnecté pendant la group. Corrigé avec `if(session != null && session.isConnected())` avant chaque write.

- **`AlignmentExperience.java`** — `add()` et `remove()` appelaient `session.write()` sans vérifier que la session existe. Condition du while dans `remove()` était inversée (`honor >= ExperiencesData.get(level-1)` → jamais true dans le bon sens) : corrigé en `honor < template.getAlignment()`. `max()` gère maintenant le cas null si ExperiencesData n'a pas de niveau suivant. Honor ne peut pas descendre sous 0.

- **`CharacterExperience.java`** — `add()` : null check sur session avant écriture. `onLevelUp()` accordait la vie mais **pas les points de stats ni les points de sorts** → les joueurs ne pouvaient pas booster leurs stats en montant de niveau. Corrigé : +5 statsPoint et +1 spellPoint par niveau. `max()` gère le null si niveau max atteint.

### Race conditions

- **`RolePlayHandler.onClosed()`** — Itération directe sur `getActors().values()` alors que `removeActor()` venait d'être appelé sur la même map → ConcurrentModificationException possible. Remplacé par une copie `new ArrayList<>(actors.values())` avant l'itération.

- **`RolePlayMovement.teleport()`** — Double boucle imbriquée (outer × inner sur la même map) envoyait `GM|-id` N² fois aux acteurs. Remplacé par une seule boucle propre avec `actor == client.getCharacter()` pour s'exclure.

### Sauvegarde différée (boost stats)

- **`DeferredSaveService.java`** (nouveau) — `ScheduledExecutorService` daemon thread unique. `schedule(character)` annule le timer précédent et en recrée un à 3 minutes. `cancel(characterId)` est appelé à la déconnexion (la sauvegarde immédiate dans `RolePlayHandler.onClosed()` prend le relais). `shutdown()` appelé dans `Main.stop()`. Résultat : un seul accès DB toutes les 3 minutes de boost continu, au lieu d'un accès par clic.

- **`BoostParser.java`** — Appel `DeferredSaveService.schedule(character)` après chaque boost réussi.
- **`RolePlayHandler.java`** — `DeferredSaveService.cancel(character.getId())` avant la sauvegarde immédiate à la déconnexion.

### Corrections mineures

- **`Channel.java`** — `removeChannel()` avait une boucle `for` qui incrémentait l'index mais appelait `remove(c)` (suppression par valeur) → comportement incohérent selon les occurrences. Remplacé par `channels.remove(c)` (ArrayList.remove retire la première occurrence, ce qui est le comportement attendu).

- **`StringUtils.random()`** — Formule `(int)(Math.random() * max + min)` donnait une plage `[min, min+max)` au lieu de `[min, max]`. `random(0, 1)` retournait toujours 0. `random(4, 8)` donnait des longueurs jusqu'à 12. Corrigé : `min + (int)(Math.random() * (max - min + 1))`. `random(String)` ajusté en `random(0, str.length() - 1)`.

- **`Account.java`** — `getCharacterById()`, `addCharacter()`, `removeCharacter()` utilisaient `containsKey()` + `get()`/`put()`/`remove()` séparés → TOCTOU sur ConcurrentHashMap. Remplacé par `get()` direct, `putIfAbsent()`, et `remove()` direct.

- **`Main.java`** — `e.printStackTrace()` dans le bloc BotAI → `logger.error()`. `DeferredSaveService.shutdown()` ajouté dans `stop()`.

---

## [Boost + Packets console] — 2026-05-08

### BoostParser — formule paliers complète + validation

- **`BoostParser.java`** — Réécriture propre. La lecture de la valeur courante (`currentValue`) est maintenant faite pour **tous** les stats (y compris Vitality et Wisdom) avant l'appel à `getReqPtsToBoostStatsByClass`. Les stat IDs inconnus retournent immédiatement avec un `logger.warn` au lieu de continuer silencieusement. La structure if/else imbriquée remplacée par un switch unique clair. Ajout du logger SLF4J.
- La formule par paliers et par classe est déjà complète dans `Statistic.getReqPtsToBoostStatsByClass()` (12 classes, 4 stats variables, seuils Dofus 1.29 officiels). Le Sacrieur conserve son bonus : 1 point de stats → 2 Vitalité.

### Affichage des packets reçus/envoyés en console

- **`simplelogger.properties`** (nouveau, dans `src/`) — Configuration SLF4J SimpleLogger. Niveau global : INFO. Les classes `Game` et `Server` passent en DEBUG → tous les packets reçus et envoyés s'affichent en console sous la forme `[Game-1] received GA1;1;...` / `[Game-1] sent GM|+...`. Les timestamps sont activés (`HH:mm:ss.SSS`). Aucune dépendance supplémentaire requise.

---

## [Stabilisation — Logging SLF4J complet] — 2026-05-08

### Point 3 : Migration complète vers SLF4J, suppression de tous les System.out.println

- **`RolePlayMovement.java`** — Suppression de deux `System.out.println` de debug ("teleport", "client is busy.") laissés par erreur dans `teleport()`. Aucun remplacement : ces messages n'apportent aucune valeur en production.

- **`GameParser.java`** — `System.out.println("Unknow actionType...")` → `logger.warn(...)`. Ajout de l'import SLF4J et du champ `logger`.

- **`BotAI.java`** — `System.out.println("Bot spawn")` → `logger.info("Bot {} spawned on map {}", name, mapId)`. Ajout de l'import SLF4J et du champ `logger`.

- **`VersionHandler.java`** — `System.out.println("VersionHandler : onClosed()")` → `logger.debug("VersionHandler closed")`. Ajout de l'import SLF4J et du champ `logger`.

- **`AuthentificationHandler.java`** — `System.out.println(...)` → `logger.debug("AuthentificationHandler closed")`. Ajout de l'import SLF4J et du champ `logger`.

- **`NicknameHandler.java`** — `System.out.println(...)` → `logger.debug("NicknameHandler closed")`. Ajout de l'import SLF4J et du champ `logger`.

- **`ServerChoiceHandler.java`** — `System.out.println(...)` → `logger.debug("ServerChoiceHandler closed")`. Ajout de l'import SLF4J et du champ `logger`.

Résultat : zéro `System.out.println` dans le projet. Tout le logging passe par SLF4J.

---

## [Stabilisation — Channel & RolePlayHandler] — 2026-05-08

### Point 2 : Channel null check + RolePlayHandler champs hors constructeur

- **`Channel.java`** — `channels.isEmpty() || channels == null` : le null check était après `isEmpty()`, ce qui aurait causé un NPE si `null` avait été passé. Déplacé en tête : `channels == null || channels.isEmpty()`. L'initialisation des canaux par défaut déplacée du `get()` vers le constructeur : si la liste passée est nulle ou vide, `getBase()` est ajouté immédiatement à la construction. La logique paresseuse dans `get()` supprimée.

- **`RolePlayHandler.java`** — `session` et `character` déclarés comme initialiseurs de champ (`IoSession session = client.getSession()`) hors du constructeur : trompeur car `client` est un champ hérité, l'initialisation dépend implicitement de l'ordre d'exécution de `super()`. Déplacés dans le corps du constructeur après `super()`, déclarés `private final` pour interdire toute réassignation accidentelle.

---

## [Stabilisation — Pool de connexions BDD] — 2026-05-08

### Point 1 : Thread-safety de la connexion base de données

- **`Connector.java`** — Réécriture complète. La connexion unique statique partagée entre tous les threads MINA a été remplacée par un pool de 5 connexions géré par `ArrayBlockingQueue<Connection>`. Deux nouvelles méthodes : `acquire()` bloque jusqu'à 5 secondes pour obtenir une connexion disponible (relance une connexion morte si nécessaire), `release()` la remet dans le pool. `System.exit(1)` remplacé définitivement par `RuntimeException`. Méthode `getConnection()` supprimée.

- **`AccountsData.java`** — Suppression du champ `connection` statique. Chaque méthode (`load`, `nicknameIsExist`, `updateNickname`) acquiert et libère une connexion via `Connector.acquire()` / `Connector.release()` dans un bloc `try/finally`. Les getters `containsKey` suivis de `get` remplacés par `get` direct + check null (élimine les TOCTOU).

- **`CharactersData.java`** — Même refactoring. `load`, `create`, `delete`, `update`, `nicknameIsExist` utilisent tous le pattern `acquire`/`release`. La requête `update` était la plus critique car appelée à chaque déconnexion depuis un thread MINA.

- **`MapsData.java`** — Même refactoring sur `load` et `loadTriggers`.

- **`BreedsData.java`** — Même refactoring sur `load`.

- **`ExperiencesData.java`** — Même refactoring sur `load`.

---

## [Stabilisation — Visibilité des acteurs] — 2026-05-08

### Bug de refresh/visibilité des personnages à la connexion et au changement de map

- **`WorldData.java`** — Ajout de `removeController(int characterId)` : la méthode n'existait pas, empêchant tout nettoyage à la déconnexion.

- **`GameScreenHandler.java`** — `WorldData.addController()` n'était **jamais appelé nulle part**. `GameParser.information()` utilise `WorldData.getController()` pour notifier les autres joueurs de l'arrivée d'un nouveau personnage, mais retournait toujours `null` → le bloc de notification était systématiquement sauté → les joueurs déjà présents sur la map ne voyaient jamais arriver un nouveau personnage. Fix : `WorldData.addController(character.getId(), client)` ajouté dans `characterSelectionSucessMessage()`, juste après l'enregistrement de la session.

- **`RolePlayHandler.java`** — `WorldData.removeController()` ajouté dans `onClosed()` pour nettoyer l'entrée à la déconnexion.

- **`GameParser.java`** — `information()` appelait `map.addActor()` inconditionnellement, alors que `teleport()` avait déjà ajouté le personnage à la nouvelle map. Double insertion inutile éliminée : `addActor` n'est maintenant appelé que si le personnage n'est pas déjà dans la map.

---

## [Stabilisation — Base] — 2026-05-08

### Bugs critiques corrigés (P0)

- **`Statistic.java`** — Le champ `statistics` était `static` : toutes les instances de `Statistic` partageaient la même map. Chaque chargement de personnage écrasait les stats de tous les autres. Passé en champ d'instance avec initialisation dans chaque constructeur.

- **`Account.java`** — Le champ `characters` était `static` : tous les comptes partageaient la même map de personnages. Passé en champ d'instance. Suppression du `setCharacters()` static devenu invalide.

- **`Characters.java`** — `getCellId()` s'appelait lui-même en récursion infinie → `StackOverflowError` garanti à chaque appel. Corrigé : `return getCurrentCell()`.

- **`BasicParser.java`** — NPE dans `channelsMessage()` : `target` (personnage destinataire d'un message privé) pouvait être `null` si le nom n'existait pas, utilisé sans vérification. Guard `null` ajouté avec renvoi de `cMEf<nom>` au client. Même correction pour `actorSession` (null pour les BotAI) dans tous les canaux de chat et dans `emoticons()`.

- **`GameParser.java`** — `EmptyStackException` : `pop()` et `peek()` appelés sans vérifier que la pile d'actions n'est pas vide. Guard `isEmpty()` ajouté. Également : `StringIndexOutOfBoundsException` si le paquet `GA` fait moins de 5 caractères — guard `packet.length() >= 5` ajouté. Fallback sur `getCurrentCell()` si `args` trop court dans `endAction()`.

- **`RolePlayHandler.java`** — NPE dans `onClosed()` : `actorSession` pouvait être `null` (acteurs sans session comme les BotAI) avant le `.equals()`. Guard `actorSession != null` ajouté.

---

### Sécurité et ressources (P1)

- **`AccountsData.java`** — SQL Injection sur `load()` (username) et `nicknameIsExist()` (nickname). Passage en `PreparedStatement` avec paramètre `?`. Fix de `updateNickname()` qui construisait la requête par concaténation avant de la passer à `prepareStatement()` (faux PreparedStatement). Tous les `ResultSet` sont maintenant fermés via `try-with-resources`.

- **`CharactersData.java`** — SQL Injection sur `load()` (owner id) et `nicknameIsExist()` (name). Passage en `PreparedStatement`. `ResultSet` et `Statement` fermés explicitement après usage.

- **`MapsData.java`** — SQL Injection sur `load()` (mapId) et `loadTriggers()` (map id). Passage en `PreparedStatement`. `ResultSet` fermés via `try-with-resources`. Refactoring de `load()` : suppression du parcours redondant de la map en mémoire après la requête SQL.

- **`BreedsData.java`** — `ResultSet` de `load()` jamais fermé. Passage en `try-with-resources`.

- **`ExperiencesData.java`** — `ResultSet` de `load()` jamais fermé. Passage en `try-with-resources`.

- **`WaypointParser.java`** — NPE si `MapsData.findById()` retourne `null` (map inconnue en BDD) : guard `map == null` ajouté avec return immédiat. Fix du décompte de kamas : `setKamas(-cost)` remplacé par `setKamas(character.getKamas() - cost)`. Guard `try/catch` sur le `Short.parseShort()` de l'ID.

- **`RolePlayMovement.java`** — `begin()` envoyait le paquet de mouvement uniquement au client lui-même (ligne commentée sur `actorSession`). Corrigé : le paquet est maintenant envoyé à tous les acteurs de la map via leur `actorSession`. Guard `actorSession != null && isConnected()` maintenu. Guard `path.length() < 3` ajouté dans `end()` pour éviter un `StringIndexOutOfBoundsException` sur un chemin malformé.

---

### Bugs silencieux (P2)

- **`BasicParser.java`** — `packet.replace("<", "")` : `String` est immutable, la valeur de retour était ignorée, le nettoyage n'avait aucun effet. Corrigé : `packet = packet.replace("<", "").replace(">", "")`.

- **`Characters.java`** — `getMaxPods()` : la formule passait `ADD_STRENGTH.getShort() * 5` comme ID de stat à `getEffect()` au lieu de la valeur de la stat Force. Résultat : le bonus pods lié à la Force était toujours 0. Corrigé : `getStats().getEffect(EConstants.ADD_STRENGTH.getInt()) * 5`.

- **`GameScreenHandler.java`** — `uniqueId()` générait un ID à partir de la date formatée (`ddhhmmssMs`) : deux créations de personnage à la même milliseconde produisaient le même ID → conflit de clé primaire en BDD. Remplacé par un `AtomicLong` initialisé à `System.currentTimeMillis()` et incrémenté à chaque appel.

- **`Connector.java`** — `System.exit(1)` en cas d'échec de connexion à la BDD tuait brutalement la JVM sans laisser les shutdown hooks s'exécuter ni les ressources se fermer. Remplacé par `throw new RuntimeException(...)`, l'arrêt est maintenant géré proprement depuis `Main`.

- **`Main.java`** — Credentials BDD et ports hardcodés (`"127.0.0.1"`, `"root"`, `""`, `"Dofus"`, `499`, `5555`). Remplacé par la lecture d'un fichier `config.properties` avec valeurs par défaut. Ajout d'un `Runtime.addShutdownHook` pour garantir l'arrêt propre sur Ctrl+C ou signal système. L'appel bloquant `System.in.read()` sert maintenant uniquement à maintenir le processus actif, l'arrêt réel étant délégué au hook.

- **`RolePlayHandler.java`** — Import `java.util.Date` inutilisé supprimé.

---

## [Système PNJ + Zaap/Zaapi + Téléport carte] — 2026-05-08

### Logs packets console (PacketLogger)

- **`PacketLogger.java`** (nouveau) — Contourne SLF4J (niveaux par classe non fiables en 1.6.6) avec des `System.out.println` directs horodatés (`HH:mm:ss.SSS`). Méthodes statiques `recv(server, sessionId, packet)` et `sent(...)`. Format : `14:26:02.344 [RECV] [Game-2] GI`.
- **`Game.java`** et **`Server.java`** — `logger.debug` pour les paquets reçus/envoyés remplacés par `PacketLogger.recv/sent`. Niveau SLF4J global revenu à `info` dans `Main.java`.

### Fix bots hors ligne (`Characters.connected`)

- **`BotAI.java`** — `bot.setConnected(true)` ajouté après `bot.setStats()`. Le champ `connected` valait `false` par défaut → `BasicParser` (messages privés) et `PartyParser` (invitations) considéraient les bots comme hors ligne et ignoraient toute interaction. Les bots sont maintenant visibles dans toutes les boucles de présence.

### Téléport double-clic carte monde (BaM)

- **`MapsData.java`** — Nouvelle méthode `findByCoord(int x, int y)` : cherche d'abord dans le cache en mémoire, puis requête SQL `WHERE abscissa=? AND ordinate=? LIMIT 1`.
- **`BasicParser.java`** — `moveByClickMap()` réécrit : vérifie `canMoveAllDirections()` (bit 8192 = droit GM), parse les coordonnées `x,y` du paquet `BaM`, trouve la carte via `findByCoord`, place le personnage sur la cellule zaap ou 200 par défaut, appelle `RolePlayMovement.teleport()`. Les non-GM sont ignorés silencieusement.

### Système Zaap / Zaapi (`WV`, `Wv`, `WU`, `Wu`)

- **`EConstants.java`** — Ajout de `AMAKNA_ZAAPS[][]` et `INCARNAM_ZAAPS[][]` : tableaux `{mapId, cellId}` de tous les zaaps officiels Dofus 1.29.
- **`WaypointParser.java`** — Réécriture complète :
  - `panelZaaps()` — met `displacement=true`, construit et envoie `WC{mapId|mapId|...}` (liste de tous les zaaps accessibles).
  - `panelZaapis()` — vérifie l'alignement ; si aligné envoie `Wc{mapIds}` filtrés par faction ; si non-aligné envoie `Wv` (fenêtre vide).
  - `use()` — gère `WU` (zaap, coût 200 kamas vérifié) et `Wu` (zaapi, gratuit) ; valide le zaap cible, téléporte, remet `displacement=false` et ferme le panneau.

### Système PNJ complet

#### Modèles de données

- **`NpcTemplate.java`** (nouveau → puis réécrit v2) — Stocke l'identité, l'apparence visuelle complète (gfxID, scaleX, scaleY, sex, color1-3, accessories, extraClip, customArtWork), et les données de dialogue (initQuestion, questionResponses). Maps `ConcurrentHashMap` pour les questions et réponses de l'arbre de dialogue. Méthode `buildAccessories()` : convertit les IDs décimaux en hex pour le paquet GM (`"0,275,0,0,0"` → `",113,,,"`).
- **`NpcQuestion.java`** (nouveau) — Stocke ID, texte et tableau d'IDs de réponses. `buildReplyList()` génère la chaîne `101;102;103` pour le paquet `DQ`.
- **`NpcReply.java`** (nouveau) — Enum `Action { NEXT, CLOSE, SHOP }`. `getNextQuestionId()` parse `params` en int.
- **`NPC.java`** (remplacé le stub) — Implémente `IActor`. `actorId = spawnId + 100_000` (offset évitant toute collision avec les IDs personnages positifs et bots négatifs).
- **`Characters.java`** — Champ `NpcTemplate dialogNpc = null` + getter/setter `getDialogNpc`/`setDialogNpc`.

#### Base de données

- **`sql/npc_system.sql`** (nouveau) — Tables `npc_templates`, `npc_questions`, `npc_replies`, `npc_spawns` + données démo (Marchand, Garde sur map 7411).
- **`NpcData.java`** (nouveau → puis réécrit v2) :
  - v1 : charge depuis 4 tables séparées (`npc_templates`, `npc_questions`, `npc_replies`, `npc_spawns`).
  - v2 (adaptation nouveau schéma) : `npc_templates` enrichi avec tous les champs visuels (`gfxID`, `scaleX`, `scaleY`, `sex`, `color1-3`, `accessories`, `extraClip`, `customArtWork`, `initQuestion`, `question_responses`). Les spawns sont maintenant embarqués en JSON dans la colonne `spawns_json` (format `[{"mapid":X,"cellid":Y,"orientation":Z}]`), parsés par regex sans dépendance JSON externe. `npc_questions` et `npc_replies` restent optionnelles (try-catch → warning si absentes). Compteur `AtomicInteger` pour des spawn IDs uniques.
- **`Initialisation.java`** — `NpcData.load()` ajouté après `BreedsData.load()`.

#### Intégration carte + réseau

- **`MapTemplate.java`** — Map `ConcurrentHashMap<Integer, NPC> npcs` distincte de `actors`. Méthodes `addNpc`, `getNpc(actorId)`, `getNpcs()`.
- **`GProtocol.java`** — `getNpcPattern(StringBuilder, NPC)` : génère l'entrée GM complète 12 champs Dofus 1.29 `cell;dir;0;actorId;gfxId^scaleX^scaleY;sex;color1;color2;color3;accessories;-1;0`. `extraClip` et `customArtWork` forcés à `-1`/`0` (valeurs DB non-neutres plantent le rendu Flash → écran noir).
- **`GameParser.java`** — `information()` : PNJ insérés en premier dans le paquet GM (entités statiques), puis les personnages. Suppression du `fC0` prématuré dans `creation()` : il était envoyé avant que le client envoie `BD` (carte chargée), causant une confusion d'état Flash → écran noir. `fC0` n'est plus envoyé qu'une seule fois, dans `information()` après `GDK`.
- **`RolePlayHandler.java`** — `case 'D'` décommenté ; `parseDialogPacket()` route `DC`, `DR`, `DV`. `character.setDialogNpc(null)` dans `onClosed()`.

#### Protocole dialogue

- **`DialogParser.java`** (nouveau) :
  - `create()` — Valide l'actorId, vérifie `initQuestion > 0` ; ouvre `DCK|0` ; si l'arbre complet est chargé (npc_questions) → `sendQuestion()` ; sinon mode simplifié → `DQ{initQuestion}|{questionResponses}` directement depuis le template.
  - `reply()` — Si données de réponse absentes → fermeture par défaut. Switch sur `Action` : `NEXT` cherche la question suivante, `SHOP` envoie `BN` (TODO boutique), `CLOSE` ferme.
  - `quit()` — Ferme proprement si un dialogue est en cours.

---

## [Fix NPE AuthentificationHandler + adaptation SQL PNJ] — 2026-05-08

### Fix NPE double-connexion (`AuthentificationHandler`)

- **`AuthentificationHandler.java`** — La branche `account.isConnected() == true` appelait `session.write("AlEa")` sans vérifier que la session existait. Si le compte avait un flag `connected=true` périmé (crash / coupure réseau sans déconnexion propre), `WorldData.getSessionByAccount().get(account)` retournait `null` → NPE.

  Deux cas gérés maintenant :
  1. **Session encore active** (`session != null && session.isConnected()`) → kick de l'ancienne session (`AlEa` + close), rejection de la nouvelle (`AlEd` + close) — comportement original préservé.
  2. **Flag périmé** (session null ou déconnectée) → `account.setConnected(false)`, nettoyage `WorldData.removeSessionByAccount()`, puis re-tentative de login normal (validation mot de passe, vérification ban, etc.).

### Adaptation au nouveau schéma SQL (`npc_actions_new.sql`)

Le fichier SQL fourni introduit un schéma `npc_templates` enrichi et supprime la table `npc_spawns` (spawns embarqués en JSON). Tous les fichiers Java concernés ont été mis à jour :

- **`NpcTemplate.java`** — Champs renommés (`gfx` → `gfxID`, `firstQuestionId` → `initQuestion`) et 8 nouveaux champs visuels ajoutés (`scaleX`, `scaleY`, `sex`, `color1`, `color2`, `color3`, `accessories`, `extraClip`, `customArtWork`, `questionResponses`). Méthode `buildAccessories()` pour la conversion décimal → hex du champ GM.
- **`NpcData.java`** — Requête `loadTemplates()` adaptée aux nouvelles colonnes. Méthode `spawnAll()` supprimée, remplacée par `parseAndSpawn()` inline (regex sur `spawns_json`). `loadQuestions()` et `loadReplies()` entourés de try-catch → optionnels, non-bloquants si les tables sont absentes.
- **`GProtocol.java`** — `getNpcPattern()` : utilise `gfxID`, `scaleX/Y`, `sex`, couleurs, `buildAccessories()`.
- **`DialogParser.java`** — `getFirstQuestionId()` → `getInitQuestion()` ; fallback `questionResponses` pour le mode simplifié.

### Correction écran noir au chargement de carte

Deux causes identifiées dans les logs :

1. **`fC0` prématuré** — `GameParser.creation()` envoyait `fC0` immédiatement après `GDM`, avant que le client réponde `BD` (fin de chargement carte). Le client Flash recevait ce paquet pendant une phase critique → corruption d'état → écran noir. Corrigé : `fC0` supprimé de `creation()`, désormais envoyé une seule fois dans `information()` après `GDK`.

2. **`extraClip` non-neutre** — Un PNJ sur la carte initiale avait `extraClip=4` en base. Le client Flash tentait de charger un clip d'animation inexistant pour ce GFX → exception non rattrapée → rendu de carte planté. Corrigé : `extraClip` et `customArtWork` forcés à `-1`/`0` dans `getNpcPattern()` indépendamment des valeurs BDD.

---
