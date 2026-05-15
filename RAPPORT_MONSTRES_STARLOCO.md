# Analyse comparée : système monstres Sandbox vs StarLoco / AncestraR

Document généré 2026-05-14. Sources analysées :
- `/tmp/starloco/StarLoco-Game-master/` (référence Dofus 1.29 moderne)
- `/tmp/ancestra/ancestrar-code-r54-trunk/` (référence Dofus 1.29 historique)
- `src/org/dofus/...` (Sandbox courant)

---

## 1. Apparition des monstres sur la map

### AncestraR
**Spawn initial** (`Carte.refreshSpawns()` ligne 1671) :
```java
public void refreshSpawns() {
    for (id : _mobGroups.keySet()) GAME_SEND_ERASE_ON_MAP_TO_MAP(this, id);
    _mobGroups.clear();
    _mobGroups.putAll(_fixMobGroups);
    for (MobGroup mg : _fixMobGroups.values()) GAME_SEND_MAP_MOBS_GM_PACKET(...);
    spawnGroup(ALIGNEMENT_NEUTRE, _maxGroup, ...);
    spawnGroup(ALIGNEMENT_BONTARIEN, 1, ...);
    spawnGroup(ALIGNEMENT_BRAKMARIEN, 1, ...);
}
```

**Composition aléatoire d'un groupe** (`MobGroup` constructor ligne 32) — distribution officielle du nombre de mobs par groupe selon `maxSize` :
| maxSize | Distribution | Notes |
|---------|-------------|-------|
| 1 | 100% → 1 | |
| 2 | 50/50 → 1 ou 2 | |
| 3 | 33% chaque | 1, 2, 3 |
| 4 | 22/26/26/26 | |
| 5 | 15/20/25/25/15 | |
| 6 | 10/15/20/20/20/15 | |
| 7 | 9/11/15/20/20/16/9 | |
| 8+ | 9/11/13/17/17/13/11/9 | |

### Sandbox actuel
`MonstersData.java` — utilise GROUP_SIZE_MIN=2, GROUP_SIZE_MAX=5 avec random uniforme. **Ne suit pas la distribution officielle**.

### Reco
Aligner `MonsterGroup` création sur la distribution AncestraR (table ci-dessus). Variable selon `maxSize` de la map.

---

## 2. Réapparition après combat

### AncestraR
**Pas de respawn par-combat. Respawn global toutes les 5 heures** (`Ancestra.CONFIG_RELOAD_MOB_DELAY = 300 * 60000`).

`World.RefreshAllMob()` :
- Loop sur toutes les maps.
- `Carte.refreshSpawns()` : clear all _mobGroups, re-add _fixMobGroups, spawnGroup neutre.
- Message broadcast "Recharge des Mobs en cours / finie. Prochaine dans 5h."

### Sandbox actuel
`MapRespawnService.scheduleRespawn()` appelé dans `Fight.endFight` pour respawn par groupe avec un délai. **Plus granulaire qu'AncestraR mais non standard**.

### Reco
- Garder le respawn par groupe (mieux que AncestraR pour l'XP/farm) MAIS ajouter un timer global de 5h en backup pour les groupes qui auraient été perdus.
- Vérifier que `scheduleRespawn` utilise un délai officiel : Dofus 1.29 = entre 5 et 30 min selon zone.

---

## 3. Clic sur un groupe de monstres (entrée combat)

### Flow officiel AncestraR + StarLoco
1. Client → `GA001<path>` (mouvement RP vers groupe)
2. Serveur :
   - Calcule cellule d'arrêt avant contact
   - Envoie `GA1;1;<id>;<correctedPath>` au client
3. Client → `GKK1` (fin animation)
4. Serveur :
   - **map.removeMonsterGroup(group)** + broadcast `GM|-<groupId>` à la map old
   - **map.removeActor(player)** + broadcast `GM|-<playerId>` à la map old
   - Stop regen player
   - **new Fight(map, player, group)** → construit Fight + Fighters
   - Place fighters sur cellules placement
   - `fight.startPlacement()` → broadcast `GJK2|GP|GM|GIC|GDK` au joueur initiateur

### Sandbox actuel
`FightParser.initiateFightVsMonsters` ligne 157 — flow identique. ✅ OK.

### Reco
Vérifier que les autres joueurs sur la map old reçoivent bien `GM|-` du group + du player initiateur. Voir log `packets.log` pour confirmer.

---

## 4. Sprite player + sprite mobs en combat (le bug actuel)

### Format `GM|+` combat officiel (StarLoco + AncestraR)

**Player** (PlayerFighter.getGMPacketParts) :
```
cell;dir;0;id;name;classe;gfx^size;sex;level;
  align,0,grade,(level+id);
  col1Hex;col2Hex;col3Hex;
  accessories;
  currentPdv;baseAP;baseMP;
  resN;resE;resF;resW;resA;
  dodgePA;dodgePM;
  team
```
= 26 champs

**Mob** (MobFighter.getGMPacketParts) :
```
cell;dir;0;id;templateId;-2;gfx^size;
  GRADE_NUMBER(1-5);
  col1;col2;col3;
  0,0,0,0;
  maxPdv;PA;PM;
  team
```
= 16 champs (pas de resists, pas de dodge — MOBS PLUS COURT)

⚠️ **Le champ 8 du mob est le NUMÉRO DE GRADE (1-5), pas le level absolu.**

### Sandbox état Phase 32
Format aligné. Reste à valider visuellement après Clean & Build.

---

## 5. Animations en combat

### Flow officiel
1. Serveur reçoit action (ex: `GA001<path>`)
2. Serveur envoie `GA1;1;<id>;<path>` (déclenche animation client)
3. **ATTENDRE** que le client envoie `GKK1` (fin animation côté client)
4. Serveur envoie les conséquences : `GA;129` (perte PM), `GIC` (position), `GTM` (état)
5. Si plus d'actions disponibles, envoyer `GTF<id>` puis `GTS<nextId>`

### Sandbox actuel
Phase 30 fix : délai fixe basé sur nombre de cases (`max(400, steps × 300)` ms) au lieu d'attendre `GKK1`. **Approximation qui peut être trop courte ou trop longue selon la machine du client**.

### Reco
Implémenter l'attente du `GKK1` réel :
- Stocker l'`actionId` en cours sur le fighter
- Au `GKK1` reçu, exécuter le callback (envoyer GA;129 + GIC + GTM + GTF)
- Timeout 5s pour ne pas bloquer si `GKK1` perdu

---

## 6. Drops et XP fin de combat

### Algorithme officiel AncestraR (`Fight.java:2743-2980`)

**Étape 1 — PP de groupe** :
```java
int groupPP = 0;
for (Fighter F : team0_winners) {
    if (!F.isInvocation()) groupPP += F.getTotalStats().getEffect(STATS_ADD_PROS);
}
groupPP = Math.max(0, groupPP);
// Bonus challenges + starBonus :
groupPP = groupPP * (100 + challengesBonus + starBonus) / 100;
```

**Étape 2 — Drops possibles** :
```java
List<Drop> possibleDrops = new ArrayList<>();
int minKamas = 0, maxKamas = 0;
for (Fighter F : team1_losers) {
    if (F.isInvocation() || F.getMob() == null) continue;
    minKamas += F.getMob().getTemplate().getMinKamas();
    maxKamas += F.getMob().getTemplate().getMaxKamas();
    for (Drop D : F.getMob().getDrops()) {
        if (D.getMinProsp() <= groupPP) {
            int taux = (int)((groupPP * D.taux * RATE_DROP) / 100);
            possibleDrops.add(new Drop(D.itemId, 0, taux, D.max));
        }
    }
}
```

**Étape 3 — Tri winners par PP DESC** :
```java
ArrayList<Fighter> temp = new ArrayList<>();
while (temp.size() < team0.size()) {
    Fighter curMax = null; int curPP = -1;
    for (Fighter F : team0) {
        if (F.getTotalStats().getEffect(STATS_ADD_PROS) > curPP && !temp.contains(F)) {
            curMax = F;
            curPP = F.getTotalStats().getEffect(STATS_ADD_PROS);
        }
    }
    temp.add(curMax);
}
team0 = temp;  // trié PP DESC
```

**Étape 4 — Distribution drop par joueur** :
```java
for (Fighter i : team0_sorted_by_PP_DESC) {
    List<Drop> shuffled = new ArrayList<>(possibleDrops);
    Collections.shuffle(shuffled);
    int itemsWonPerPerso = floor(possibleDrops.size() / team0.size());
    int k = 0;
    for (Drop D : shuffled) {
        if (k > itemsWonPerPerso) continue;
        int t = D.taux * 100;  // taux × 100 pour 2 décimales
        int jet = rand(0, 10000);
        if (jet < t) {
            give(D.itemId, +1) to player i;
            D.max--;
            if (D.max == 0) possibleDrops.remove(D);
        }
        k++;
    }
}
```

**Étape 5 — XP & kamas** :
```java
long winXP = Formulas.getXpWinPvm2(player, team0, team1, totalXP, starBonus);
int winKamas = Formulas.getKamasWin(player, team0, minKamas, maxKamas);
```

Formule simplifiée :
- XP partagée selon level (joueurs hauts level reçoivent moins, joueurs bas level reçoivent plus, jusqu'à la limite XP du palier)
- Kamas : `(rand(minKamas, maxKamas) × PP_player / groupPP) / nbWinners`

### Sandbox actuel
`Fight.calculateAndApplyRewards` :
- ❌ XP divisée également entre joueurs (pas selon level)
- ❌ Tous les drops au winner[0] (pas tri PP)
- ❌ Pas de `maxItemsPerPlayer`
- ❌ Pas de `groupPP` agrégé (chaque drop roll indépendamment)
- ✅ Bonus PP appliqué au taux

### Reco
Réécrire `calculateAndApplyRewards` pour suivre l'algo AncestraR. Patch implémenté Phase 33 (voir CHANGELOG).

---

## 7. Synthèse priorités de fix

| Bug observé | Cause | Phase fix |
|-------------|-------|-----------|
| Sprite mob invisible | Champ 8 = level absolu au lieu de grade 1-5 | 32 (déjà fait) |
| Couleurs perso fausses | Format align `0,100,...` vs `0,0,0,levelPlusId` | 30 (déjà fait) |
| Anim déplacement TP | GIC immédiat après GA1;1 | 30 (délai 400ms, à améliorer avec GKK1) |
| Fenêtre fin combat vide | Format GE incomplet | 30 (defaultGfx ajouté) |
| Drops tous au 1er joueur | Pas de tri PP, pas de cycle | **33 (ce patch)** |
| XP non répartie selon level | Division uniforme | À faire (Phase 34+) |
| Pas d'animation entre paquets IA | GA1+GA129 trop rapprochés | 30 (steps × 300ms) |

---

## Annexes — Constantes officielles
- `RATE_DROP = 1` (AncestraR base ; multiplicateur serveur configurable)
- `RATE_XP = 1` (idem)
- `RATE_KAMAS = 1` (idem)
- `CONFIG_RELOAD_MOB_DELAY = 5 heures` (respawn global)
- `Aggro distance = Constants.getAggroByLevel(maxLevel)` — fonction des level mob
- `Aggro PvP alignement = 15 cellules`
- `maxGroup` map = configuré par carte (typique 4-10)
- `maxSize` group = configuré par carte (typique 5-8)
