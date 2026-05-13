# Audit technique StarLoco + Ancestrar pour transmission à Claude

Date du rapport : 2026-05-13

Objectif : donner à Claude une carte rapide du code, des responsabilités par classe/fonction, et des zones à fouiller en priorité pour corriger ou compléter une base Anka-like / Dofus 1.xx.

Important : analyse statique locale. Je n’ai pas lancé le serveur ni exécuté de tests gameplay. Les signatures de fonctions sont extraites automatiquement ; elles servent d’index de navigation et peuvent contenir quelques faux positifs dans les très gros fichiers anciens.

## 1. Résumé chiffré

| Projet | Fichiers Java | Lignes | Classes/interfaces/enums | Fonctions détectées |
|---|---:|---:|---:|---:|
| Ancestrar Game | 39 | 46212 | 39 | 4475 |
| Ancestrar Realm | 12 | 2201 | 12 | 195 |
| StarLoco Game | 263 | 83444 | 269 | 8416 |

Conclusion rapide : Ancestrar sert de référence historique compacte ; StarLoco est la base active la plus structurée, avec DAO, packages métiers, scripts, IA séparées, et modèle Player/GameObject/GameMap plus moderne.

## 2. Architecture générale

### Ancestrar Game
Structure ancienne en trois pôles : `common` contient les singletons, constantes, SQL, pathfinding, formulas, socket packets et commandes ; `objects` contient presque tout le modèle métier ; `game` et `communication` gèrent serveur, threads et liaison avec realm. Le code est monolithique : beaucoup de logique gameplay est directement dans `Personnage`, `Fight`, `Carte`, `Objet`, `SQLManager`, `SocketManager`.

### Ancestrar Realm
Petit serveur login/realm. Il gère comptes, liste serveurs, communication game/realm, sélection de serveur. À utiliser surtout comme référence pour le protocole login et l’échange inter-serveur.

### StarLoco Game
Refonte plus modulaire. Les grandes zones sont : `client` pour compte/joueur, `area.map` pour cartes/cellules, `fight` pour combat, `object` pour items, `entity` pour maisons/montures/pets/npc/percos, `database.data` pour DAO, `command` pour commandes, `script` pour Lua, `common` pour formulas/pathfinding/packets/conditions, `kernel` pour config/boot/constants.

## 3. Zones prioritaires à donner à Claude

1. **Level up / récupération complète de vie.** StarLoco : `client/Player.java`, fonctions `levelUp`, `addXp`, `refreshStats`, `refreshLife`. Ancestrar : `objects/Personnage.java`, `levelUp`, `addXp`. Dans les deux bases, le comportement historique met les PDV au max au moment du level up. StarLoco le fait déjà explicitement dans `levelUp` via `this.maxPdv += 5; this.setPdv(this.getMaxPdv());`. Si le bug existe encore en jeu, vérifier que le level-up passe bien par cette méthode et qu’un `refreshStats()` ou packet de stats ne réécrase pas les PDV juste après.
2. **Cellules walkable / puits / objets interactifs.** StarLoco : `area/map/GameCase.java`, `CellsDataProvider.java`, `common/PathFinding.java`, `entity/map/InteractiveObjectTemplate.java`. La logique actuelle dit : movement 0 = non walkable, movement 1 = seulement hors combat, movement 2+ = walkable ; puis les objets interactifs peuvent bloquer si `checkObject` est vrai, surtout si l’objet est prêt et que la cellule est la destination. Si le joueur marche sur un puits, la piste probable est : template interactif absent, `object2` non reconnu, `isWalkable()` appelé sans `checkObject`, ou donnée `movement` de la cellule considérée walkable.
3. **Commande item / jets maximum.** Ancestrar : `common/Commands.java` accepte `ITEM id quantité MAX` hors commande officielle `!getitem`. StarLoco : `command/CommandAdmin.java` accepte `ITEM`/`getitem` avec `MAX` en 4e argument et appelle `ObjectTemplate.createNewItem(qua, useMax)`. Attention : si une cible joueur est ajoutée en 5e argument, `MAX` peut être ignoré, car `useMax` n’est lu que quand `infos.length == 4`.
4. **Familiers version Anka-like.** StarLoco possède déjà `entity/pet/Pet.java`, `PetEntry.java`, `database/data/game/PetTemplateData.java`, `database/data/login/PetData.java`. À comparer avec Ancestrar `objects/Pets.java` et `PetsEntry.java`. Axes : horaires de repas, corpulence, perte/gain PDV, nourriture par monstre/item/catégorie, EPO, dead template, max stats, sérialisation txtStats.
5. **Risques globaux.** Beaucoup de code catch `Exception` avec `printStackTrace` ou ignore silencieusement ; plusieurs gros singletons (`World`, `SocketManager`, `DatabaseManager`) concentrent de l’état global ; les commandes admin sont permissives ; la cohérence DB/cache doit être vérifiée après chaque ajout d’objet, pet, monture, guilde ou maison.

## 4. Audit fonctionnel par modules StarLoco

- **Boot/config** — `kernel/Main`, `Config`, `Constant`, `Logging`, `Reboot`. Démarrage serveur, lecture config, constantes Dofus, reboot. `Constant` est critique pour IDs stats/items/classes et sorts acquis au level-up.
- **Réseau jeu** — `game/GameServer`, `GameClient`, `GameHandler`, `SocketManager`. Accepte connexions, parse packets, dispatch vers logique métier, construit les réponses client. Toute anomalie packet doit partir d’ici.
- **Compte/Joueur** — `client/Account`, `Player`, `Stats`, `Restriction`, `Party`, `SpellHelper`, `Stalk`. Profil joueur, stats, XP, inventaire, sorts, métiers, montures, état online, party, traque, restrictions.
- **Cartes/cellules** — `area/Area`, `SubArea`, `area/map/*`. Décodage mapdata, cellules, acteurs, objets interactifs, groupes de mobs, triggers, placement. Zone clé pour bugs de déplacement.
- **Combat** — `fight/*`, `fight/spells/*`, `fight/traps/*`, `fight/turn/*`. Moteur de combat : tours, combattants, effets, IA, pièges/glyphes, drops/xp fin de combat.
- **IA** — `fight/ia/*`. Profils IA séparés par type/boss/invocations. La nouvelle IA utilitaire est dans `util/newia`.
- **Objets/items** — `object/*`, `object/entity/*`. Templates, instances, jets, stats textuelles, panoplies, fragments, pierres d’âme.
- **Entités monde** — `entity/*`. Percepteurs, prismes, maisons, coffres, enclos, montures, familiers, PNJ, échanges.
- **Métiers/craft/FM** — `job/*`, `job/maging/*`. Métiers, recettes, récoltes, craft sécurisé, runes et brisage.
- **HDV/auction** — `hdv/*`, `auction/*`. Vente/achat, lots, listings, système d’enchères/auction spécifique.
- **Base de données** — `database/*`. DAO séparés login/game, chargement au boot, sauvegarde objets/joueurs/monde.
- **Scripts Lua** — `script/*`, `dynamic/*`. VM Lua, proxies scriptables, événements mappés, scripts dynamiques.
- **Commandes** — `command/*`. Commandes admin/joueur. Gros point de debug et de risque : création item, reload, teleport, morph, stats.

## 5. Audit fonctionnel par modules Ancestrar

- **common** — `Ancestra`, `Constants`, `World`, `SQLManager`, `SocketManager`, `Commands`, `Pathfinding`, `Formulas`, `IA`. Cœur historique très concentré. Utile pour retrouver un comportement officiel ancien ou une mécanique manquante.
- **objects** — `Personnage`, `Objet`, `Carte`, `Fight`, `Sort`, `SpellEffect`, `Monstre`, `Pets`, `PetsEntry`, `Dragodinde`, `Guild`, `Hdv`, `House`, `Metier`. Modèle métier monolithique. Les équivalents StarLoco sont souvent plus éclatés par packages.
- **game/communication** — `GameServer`, `GameThread`, `ComServer`. Thread client et communication avec realm.
- **Realm** — `RealmServer`, `RealmThread`, `Account`, `GameServer`, `SendManager`, `SQLManager`. Login, sélection de serveur, état des comptes/serveurs.

## 6. Correspondances Ancestrar -> StarLoco

| Ancestrar | StarLoco | Commentaire |
|---|---|---|
| `objects/Personnage.java` | `client/Player.java` | Joueur/personnage. À comparer pour XP, stats, sorts, inventaire, packets. |
| `objects/Objet.java` | `object/GameObject.java` + `object/ObjectTemplate.java` | StarLoco sépare instance et template plus clairement. |
| `objects/Carte.java` | `area/map/GameMap.java` + `GameCase.java` + `CellsDataProvider.java` | StarLoco a proxy cellule et provider de données. |
| `objects/Fight.java` | `fight/Fight.java` + `Fighter.java` + sous-classes | StarLoco découpe combattants par nature. |
| `objects/Sort.java` | `fight/spells/Spell.java` | Définition sort/niveaux. |
| `objects/SpellEffect.java` | `fight/spells/SpellEffect.java` | Application des effets. |
| `common/IA.java` | `fight/ia/*` | Ancestrar centralisé ; StarLoco typé par IA. |
| `objects/Pets.java`, `PetsEntry.java` | `entity/pet/Pet.java`, `PetEntry.java` | Mécanique familiers. |
| `objects/Dragodinde.java` | `entity/mount/Mount.java`, `Generation.java` | Montures. |
| `common/SQLManager.java` | `database/DatabaseManager.java` + `database/data/*` | StarLoco DAO modulaires. |
| `common/Commands.java` | `command/CommandAdmin.java`, `CommandPlayer.java` | Commandes découpées. |
| `common/SocketManager.java` | `common/SocketManager.java` | Même rôle : packets client. |

## 7. Points de contrôle détaillés
### 7.1 Level-up / vie complète
Ancestrar fait `_PDVMAX += 5; _PDV = _PDVMAX;` dans `Personnage.levelUp`. StarLoco fait `this.maxPdv += 5; this.setPdv(this.getMaxPdv());` dans `Player.levelUp`. Donc si le comportement demandé est “au level up on récupère toute sa vie”, le code de référence existe déjà des deux côtés. À vérifier : appels directs à `setLevel`, scripts/admin qui modifient le niveau sans passer par `levelUp`, recalcul `refreshStats()` qui garde le pourcentage de vie, et sauvegarde DB juste après le changement.

### 7.2 Cellules walkable
StarLoco : `GameCase.isWalkable(boolean inFight)` ne regarde que `active` et `movement`. La surcharge `isWalkable(checkObject, inFight, targetCell)` ajoute la couche objet interactif. Si un puits est franchissable alors qu’il ne devrait pas, Claude doit vérifier tous les appels qui utilisent `isWalkable(false)` ou `isWalkable(inFight)` au lieu de `isWalkable(true, false, targetCell)`, puis vérifier que le sprite du puits a un `InteractiveObjectTemplate` et que `isWalkable()` vaut false quand il faut bloquer.

### 7.3 Commande item et jets maximum
Ancestrar et StarLoco ont déjà un booléen `useMax`. StarLoco a une extension `lier` quand `infos.length == 5`, mais attention : dans ce cas, `MAX` n’est reconnu que quand `infos.length == 4`. Une commande de type `ITEM id qte MAX joueur` risque donc de ne pas mettre `useMax=true`, car `lier` prend la main sur la longueur 5. Correction probable : parser les arguments par mots-clés plutôt que par longueur fixe, ou accepter `MAX` en 4e argument + cible en 5e.

### 7.4 Familiers
Le cœur StarLoco est déjà présent. `Pet` décompile les règles d’alimentation depuis `statsUp`; `PetEntry` gère l’instance, la date du dernier repas, quantité mangée, PDV, corpulence, EPO et update stats. Pour une version Anka-like, Claude doit comparer les seuils de corpulence, les timings `gap`, les messages client, le fantôme/dead template, le nourrissage par monstres tués et par objets consommés, et le plafonnement max par stat.

## 8. Signaux de dette technique détectés

- Ancestrar Game — printStackTrace : 83 occurrence(s). Top fichiers : common/SQLManager.java (56), game/GameThread.java (7), objects/Fight.java (5), common/IA.java (3), objects/Monstre.java (3).
- Ancestrar Game — catch Exception : 245 occurrence(s). Top fichiers : common/Commands.java (56), game/GameThread.java (50), objects/SpellEffect.java (25), objects/Action.java (21), objects/Fight.java (20).
- Ancestrar Game — catch ignored : 0 occurrence(s). Top fichiers : aucun.
- Ancestrar Game — TODO/FIXME : 51 occurrence(s). Top fichiers : objects/Personnage.java (9), game/GameThread.java (9), objects/Fight.java (7), objects/Dragodinde.java (6), objects/Metier.java (5).
- Ancestrar Realm — printStackTrace : 13 occurrence(s). Top fichiers : common/SQLManager.java (13).
- Ancestrar Realm — catch Exception : 18 occurrence(s). Top fichiers : common/Ancestra.java (7), communication/ComThread.java (3), realm/RealmThread.java (2), realm/RealmServer.java (2), common/Realm.java (1).
- Ancestrar Realm — catch ignored : 0 occurrence(s). Top fichiers : aucun.
- Ancestrar Realm — TODO/FIXME : 1 occurrence(s). Top fichiers : realm/RealmThread.java (1).
- StarLoco Game — printStackTrace : 287 occurrence(s). Top fichiers : org/starloco/locos/game/GameClient.java (75), org/starloco/locos/other/Action.java (55), org/starloco/locos/fight/spells/SpellEffect.java (20), org/starloco/locos/client/Player.java (15), org/starloco/locos/object/GameObject.java (14).
- StarLoco Game — catch Exception : 382 occurrence(s). Top fichiers : org/starloco/locos/command/CommandAdmin.java (73), org/starloco/locos/other/Action.java (60), org/starloco/locos/game/GameClient.java (54), org/starloco/locos/fight/spells/SpellEffect.java (24), org/starloco/locos/object/GameObject.java (14).
- StarLoco Game — catch ignored : 66 occurrence(s). Top fichiers : org/starloco/locos/dynamic/Start.java (39), org/starloco/locos/game/GameClient.java (6), org/starloco/locos/fight/spells/SpellEffect.java (4), org/starloco/locos/command/CommandAdmin.java (3), org/starloco/locos/entity/map/MountPark.java (3).
- StarLoco Game — TODO/FIXME : 48 occurrence(s). Top fichiers : org/starloco/locos/client/Player.java (9), org/starloco/locos/game/GameClient.java (7), org/starloco/locos/fight/Fight.java (5), org/starloco/locos/area/map/CellsDataProvider.java (3), org/starloco/locos/common/PathFinding.java (3).

Lecture : ce ne sont pas forcément des bugs, mais les `catch` larges et erreurs ignorées peuvent masquer les vrais problèmes de gameplay, surtout dans commandes, parsing packets, DB et maps.

## 9. Index détaillé classes/fonctions
L’index complet est aussi fourni en CSV (`index_classes_fonctions.csv`) pour recherche/filtrage. Ci-dessous : fichier, rôle, classes, puis fonctions détectées.

### Ancestrar Game

#### `common/Ancestra.java` — 486 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Ancestra
Fonctions :
- L97 `static void(main)`
- L102 ` void(run)`
- L167 `static void(loadConfiguration)`
- L179 ` 	(if)`
- L198 ` 	(if)`
- L204 ` 	(if)`
- L210 ` 	(if)`
- L219 ` 	(if)`
- L289 ` 	(if)`
- L315 ` 	(if)`
- L333 ` 	(for)`
- L339 ` 	(for)`
- L351 ` 	(if)`
- L367 ` 	(if)`
- L414 `static void(closeServers)`
- L418 ` 	(if)`
- L435 `static void(addToMjLog)`
- L446 `static void(addToShopLog)`
- L457 `static String(makeHeader)`
- L467 `static void(try_ComServer)`
- L470 ` 	(if)`
- L475 ` 	(while)`

#### `common/Commands.java` — 2242 lignes
Rôle : Commandes MJ Ancestrar : item, kamas, teleport, reload, spawn.
Classe(s) : class Commands
Fonctions :
- L41 ` Timer(createTimer)`
- L47 ` void(actionPerformed)`
- L50 `  (if)`
- L57 `  (if)`
- L59 ` 	(for)`
- L70 ` public(Commands)`
- L77 ` void(consoleCommand)`
- L80 ` 	(if)`
- L91 ` 	(if)`
- L96 ` 	(if)`
- L101 ` 	(if)`
- L105 ` else(if)`
- L110 ` else(if)`
- L116 ` void(commandGmOne)`
- L119 ` 	(if)`
- L124 ` 	(if)`
- L143 ` 	(if)`
- L155 ` 	(for)`
- L162 ` 	(for)`
- L168 ` 	(if)`
- L179 ` 	(if)`
- L192 ` 	(switch)`
- L241 ` 	(if)`
- L250 ` 	(if)`
- L255 ` 	(if)`
- L287 ` 	(if)`
- L290 ` 	(if)`
- L294 ` 	(if)`
- L300 ` 	(if)`
- L307 ` 	(if)`
- L318 ` 	(if)`
- L329 ` 	(if)`
- L342 ` 	(if)`
- L349 ` 	(if)`
- L355 ` 	(if)`
- L361 ` 	(if)`
- L375 ` else(if)`
- L379 ` 	(if)`
- L392 ` 	(if)`
- L398 ` 	(if)`
- L409 ` 	(if)`
- L412 ` 	(if)`
- L418 ` 	(if)`
- L428 ` 	(if)`
- L435 ` 	(if)`
- L448 ` 	(if)`
- L455 ` 	(if)`
- L464 ` 	(if)`
- L470 ` 	(if)`
- L480 ` 	(if)`
- L491 ` 	(if)`
- L505 ` 	(if)`
- L511 ` 	(if)`
- L521 ` 	(if)`
- L527 ` 	(if)`
- L538 ` 	(if)`
- L541 ` 	(if)`
- L573 ` void(commandGmTwo)`
- L576 ` 	(if)`
- L581 ` 	(if)`
- L597 ` 	(if)`
- L604 ` 	(if)`
- L612 ` 	(if)`
- L623 ` 	(if)`
- L634 ` 	(if)`
- L644 ` 	(if)`
- L651 ` 	(if)`
- L660 ` 	(if)`
- L666 ` 	(if)`
- L678 ` 	(if)`
- L685 ` 	(if)`
- L695 ` 	(if)`
- L707 ` 	(if)`
- L714 ` 	(if)`
- L724 ` 	(if)`
- L737 ` 	(if)`
- L744 ` 	(if)`
- L754 ` 	(if)`
- L767 ` 	(if)`
- L769 ` 	(if)`
- L782 ` 	(if)`
- L795 ` 	(if)`
- L804 ` 	(if)`
- L814 ` 	(if)`
- L825 ` 	(if)`
- L833 ` 	(if)`
- L844 ` 	(if)`
- L853 ` 	(if)`
- L863 ` 	(if)`
- L871 ` 	(if)`
- L883 ` 	(if)`
- L890 ` 	(if)`
- L900 ` 	(if)`
- L913 ` 	(if)`
- L920 ` 	(if)`
- L930 ` 	(if)`
- L942 ` 	(if)`
- L949 ` 	(if)`
- L959 ` 	(if)`
- L972 ` 	(if)`
- L979 ` 	(if)`
- L989 ` 	(if)`
- L1009 ` 	(if)`
- L1035 ` 	(if)`
- L1043 ` 	(if)`
- L1051 ` 	(for)`
- L1063 ` 	(if)`
- L1079 ` 	(if)`
- L1081 ` 	(while)`
- L1085 ` 	(if)`
- L1100 ` 	(if)`
- L1128 ` 	(if)`
- L1161 ` 	(if)`
- L1164 ` 	(if)`
- L1174 ` 	(if)`
- L1194 ` 	(if)`
- L1209 ` 	(if)`
- L1219 ` 	(if)`
- L1228 ` 	(if)`
- L1245 ` void(commandGmThree)`
- L1248 ` 	(if)`
- L1253 ` 	(if)`
- L1258 ` 	(if)`
- L1266 ` 	(if)`
- L1273 ` 	(if)`
- L1308 ` 	(if)`
- L1311 ` 	(if)`
- L1316 ` 	(if)`
- L1327 ` 	(if)`
- L1330 ` 	(if)`
- L1335 ` 	(if)`
- L1345 ` 	(if)`
- L1354 ` 	(if)`
- L1360 ` 	(if)`
- L1381 ` 	(if)`
- L1396 ` 	(if)`
- L1404 ` 	(if)`
- L1416 ` 	(if)`
- L1428 ` 	(if)`
- L1441 ` 	(if)`
- L1451 ` 	(if)`
- L1465 ` 	(if)`
- L1478 ` 	(if)`
- L1491 ` 	(if)`
- L1503 ` 	(if)`
- L1510 ` 	(if)`
- L1530 ` 	(if)`
- L1538 ` 	(if)`
- L1555 ` 	(if)`
- L1562 ` 	(if)`
- L1576 ` 	(if)`
- L1586 ` 	(if)`
- L1600 ` 	(if)`
- L1611 ` 	(if)`
- L1624 ` 	(if)`
- L1636 ` 	(if)`
- L1649 ` 	(if)`
- L1662 ` 	(if)`
- L1674 ` 	(if)`
- L1692 ` 	(if)`
- L1712 ` void(commandGmFour)`
- L1715 ` 	(if)`
- L1720 ` 	(if)`
- L1728 ` 	(if)`
- L1738 ` 	(if)`
- L1750 ` 	(if)`
- L1766 ` 	(if)`
- L1778 ` 	(if)`
- L1790 ` 	(if)`
- L1792 ` 	(for)`
- L1800 ` 	(if)`
- L1807 ` 	(if)`
- L1816 ` 	(if)`
- L1822 ` 	(if)`
- L1831 ` 	(if)`
- L1855 ` void(fullHdv)`
- L1907 ` int(getHdv)`
- L1912 ` 	(switch)`
- L1925 ` 	(if)`
- L1929 ` 	(if)`
- L1939 ` 	(if)`
- L1943 ` 	(if)`
- L1958 ` 	(if)`
- L1969 ` 	(if)`
- L1973 ` 	(if)`
- L1977 ` 	(if)`
- L1987 ` 	(if)`
- L1991 ` 	(if)`
- L2003 ` 	(if)`
- L2007 ` 	(if)`
- L2020 ` 	(if)`
- L2024 ` 	(if)`
- L2034 ` 	(if)`
- L2038 ` 	(if)`
- L2051 ` 	(if)`
- L2067 ` 	(if)`
- L2071 ` 	(if)`
- L2084 ` 	(if)`
- L2088 ` 	(if)`
- L2097 ` 	(if)`
- L2108 ` 	(if)`
- L2112 ` 	(if)`
- L2123 ` 	(if)`
- L2127 ` 	(if)`
- L2131 ` 	(if)`
- L2162 ` 	(if)`
- L2166 ` 	(if)`
- L2175 ` 	(if)`
- L2186 ` 	(if)`
- L2190 ` 	(if)`
- L2201 ` 	(if)`
- L2205 ` 	(if)`
- L2214 ` 	(if)`
- L2228 ` int(calculPrice)`
- L2232 ` 	(for)`

#### `common/ConditionParser.java` — 315 lignes
Rôle : Évaluation conditions Dofus : stats, classe, quête, alignement, map, niveau.
Classe(s) : class ConditionParser
Fonctions :
- L13 `static boolean(validConditions)`
- L83 ` 	(if)`
- L86 ` 	(for)`
- L88 ` 	(if)`
- L90 ` 	(for)`
- L92 ` 	(if)`
- L97 ` 	(if)`
- L103 ` 	(if)`
- L120 ` 	(if)`
- L122 ` 	(for)`
- L124 ` 	(if)`
- L129 ` 	(if)`
- L135 ` 	(if)`
- L156 ` 	(for)`
- L158 ` 	(if)`
- L160 ` 	(for)`
- L162 ` 	(if)`
- L167 ` 	(if)`
- L173 ` 	(if)`
- L190 ` 	(if)`
- L192 ` 	(for)`
- L194 ` 	(if)`
- L199 ` 	(if)`
- L205 ` 	(if)`
- L227 ` 	(if)`
- L229 ` 	(for)`
- L231 ` 	(if)`
- L245 ` 	(if)`
- L247 ` 	(for)`
- L249 ` 	(if)`
- L264 ` 	(if)`
- L276 ` 	(for)`
- L278 ` 	(if)`
- L283 ` 	(if)`
- L297 ` 	(for)`
- L299 ` 	(if)`

#### `common/Constants.java` — 3429 lignes
Rôle : Constantes Ancestrar : stats, classes, sorts, métiers, formules statiques.
Classe(s) : class Constants
Fonctions :
- L389 `static int(getProtectorLvl)`
- L519 `static short(getStartMap)`
- L522 ` 	(switch)`
- L526 ` 	(if)`
- L532 `static int(getStartCell)`
- L536 ` 	(switch)`
- L540 ` 	(if)`
- L546 `static short(getClassStatueMap)`
- L550 ` 	(switch)`
- L579 `static int(getClassStatueCell)`
- L583 ` 	(switch)`
- L612 `static TreeMap<Integer, Character>(getStartSortsPlaces)`
- L616 ` 	(switch)`
- L681 `static TreeMap<Integer,SortStats>(getStartSorts)`
- L685 ` 	(switch)`
- L750 `static int(getBasePdv)`
- L755 `static int(getReqPtsToBoostStatsByClass)`
- L758 ` 	(switch)`
- L765 ` 	(switch)`
- L882 ` 	(switch)`
- L1006 ` 	(switch)`
- L1130 ` 	(switch)`
- L1254 `static int(getAggroByLevel)`
- L1263 `static boolean(isValidPlaceForItem)`
- L1266 ` 	(switch)`
- L1354 `static void(onLevelUpSpells)`
- L1357 ` 	(switch)`
- L1828 `static int(getGlyphColor)`
- L1831 ` 	(switch)`
- L1853 `static int(getTrapsColor)`
- L1856 ` 	(switch)`
- L1879 `static int(getTotalCaseByJobLevel)`
- L1886 `static int(getChanceForMaxCase)`
- L1892 `static int(calculXpWinCraft)`
- L1896 ` 	(switch)`
- L1925 `static ArrayList<JobAction>(getPosActionsToJob)`
- L1931 ` 	(switch)`
- L2103 ` 	(if)`
- L2108 ` 	(if)`
- L2113 ` 	(if)`
- L2118 ` 	(if)`
- L2123 ` 	(if)`
- L2130 ` 	(if)`
- L2135 ` 	(if)`
- L2140 ` 	(if)`
- L2145 ` 	(if)`
- L2159 ` 	(if)`
- L2164 ` 	(if)`
- L2169 ` 	(if)`
- L2174 ` 	(if)`
- L2179 ` 	(if)`
- L2184 ` 	(if)`
- L2202 ` 	(if)`
- L2209 ` 	(if)`
- L2214 ` 	(if)`
- L2219 ` 	(if)`
- L2224 ` 	(if)`
- L2236 ` 	(if)`
- L2241 ` 	(if)`
- L2246 ` 	(if)`
- L2253 ` 	(if)`
- L2258 ` 	(if)`
- L2263 ` 	(if)`
- L2268 ` 	(if)`
- L2275 ` 	(if)`
- L2280 ` 	(if)`
- L2287 ` 	(if)`
- L2292 ` 	(if)`
- L2297 ` 	(if)`
- L2309 ` 	(if)`
- L2314 ` 	(if)`
- L2319 ` 	(if)`
- L2326 ` 	(if)`
- L2331 ` 	(if)`
- L2336 ` 	(if)`
- L2341 ` 	(if)`
- L2356 `static boolean(isJobAction)`
- L2365 `static int(getObjectByJobSkill)`
- L2371 `static int(getChanceByNbrCaseByLvl)`
- L2377 `static boolean(isMageJob)`
- L2383 `static Stats(getMountStats)`
- L2387 ` 	(switch)`
- L2773 `static ObjTemplate(getParchoTemplateByMountColor)`
- L2776 ` 	(switch)`
- L2923 `static int(getMountColorByParchoTemplate)`
- L2929 `static void(applyPlotIOAction)`
- L2933 ` 	(switch)`
- L2937 ` 	(if)`
- L2952 `static int(getNearCellidUnused)`
- L2959 ` 	(if)`
- L2999 `static String(isValidPlaceToInviteCraft)`
- L3003 ` 	(if)`
- L3007 ` 	(if)`
- L3011 ` 	(if)`
- L3015 ` 	(if)`
- L3019 ` 	(if)`
- L3023 ` 	(if)`
- L3027 ` 	(if)`
- L3031 ` 	(if)`
- L3035 ` 	(if)`
- L3039 ` 	(if)`
- L3043 ` 	(if)`
- L3047 ` 	(if)`
- L3051 ` else(if)`
- L3058 ` 	(switch)`
- L3145 ` 	(if)`
- L3147 ` 	(for)`
- L3160 ` 	(if)`
- L3162 ` 	(for)`
- L3165 ` 	(for)`
- L3167 ` 	(if)`
- L3169 ` 	(if)`
- L3178 ` 	(for)`
- L3180 ` 	(if)`
- L3182 ` 	(if)`
- L3191 `static String(getSkillIDbyJobID)`
- L3195 ` 	(switch)`
- L3300 `static int(getJobIDbySkillID)`
- L3304 ` 	(switch)`

#### `common/CryptManager.java` — 179 lignes
Rôle : Encodage/décodage Dofus : hash, cellules, IP, packet utils.
Classe(s) : class CryptManager
Fonctions :
- L12 `static String(CryptIP)`
- L37 `static String(CryptPort)`
- L52 `static String(cellID_To_Code)`
- L62 `static int(cellCode_To_ID)`
- L70 ` 	(while)`
- L72 ` 	(if)`
- L76 ` 	(if)`
- L84 `static int(getIntByHashedValue)`
- L92 ` 	(if)`
- L99 `static char(getHashedValueByInt)`
- L107 `static ArrayList<Case>(parseStartCell)`
- L112 ` 	(if)`
- L117 ` 	(while)`
- L125 `static Map<Integer, Case>(DecompileMapData)`
- L146 `static String(toUtf)`
- L162 `static String(toUnicode)`

#### `common/Formulas.java` — 850 lignes
Rôle : Formules de jeu : xp, drops, dégâts, tacles, prospection, jets aléatoires.
Classe(s) : class Formulas
Fonctions :
- L14 `static int(getRandomValue)`
- L51 `static int(getTacleChance)`
- L56 ` 	(for)`
- L67 `static int(calculFinalHeal)`
- L75 `static int(calculFinalDommage)`
- L84 ` 	(if)`
- L93 ` 	(switch)`
- L177 ` 	(if)`
- L185 ` 	(if)`
- L227 ` 	(if)`
- L229 ` 	(switch)`
- L240 ` 	(if)`
- L245 ` 	(if)`
- L257 ` 	(if)`
- L262 ` 	(if)`
- L273 ` 	(if)`
- L278 ` 	(if)`
- L288 ` 	(if)`
- L310 ` 	(if)`
- L331 `static int(calculZaapCost)`
- L336 `static int(getArmorResist)`
- L340 ` 	(for)`
- L343 ` 	(switch)`
- L377 ` 	(switch)`
- L397 ` 	(for)`
- L401 ` 	(switch)`
- L423 `static int(getPointsLost)`
- L434 ` 	(if)`
- L461 ` 	(if)`
- L468 `static long(getXpWinPerco)`
- L477 ` 	(for)`
- L483 ` 	(for)`
- L522 `static long(getXpWinPvm2)`
- L533 ` 	(for)`
- L539 ` 	(for)`
- L594 `static long(getGuildXpWin)`
- L608 ` 	(if)`
- L612 ` else(if)`
- L616 ` else(if)`
- L627 `static long(getMountXpWin)`
- L662 `static int(getKamasWin)`
- L669 `static int(getKamasWinPerco)`
- L676 `static int(calculElementChangeChance)`
- L685 `static int(calculHonorWin)`
- L692 ` 	(for)`
- L699 ` 	(for)`
- L713 `static Couple<Integer, Integer>(decompPierreAme)`
- L724 `static int(totalCaptChance)`
- L728 ` 	(switch)`
- L753 `static String(parseReponse)`
- L773 `static int(spellCost)`
- L784 `static int(ChanceFM)`
- L798 `static int(getTraqueXP)`
- L824 `static int(getLoosEnergy)`
- L832 `static int(getRandomChallenge)`
- L836 `  (if)`
- L843 `  (if)`

#### `common/IA.java` — 1416 lignes
Rôle : IA historique Ancestrar : décisions mobs/invocations.
Classe(s) : class IA
Fonctions :
- L25 ` public(IAThread)`
- L34 ` void(run)`
- L37 ` 	(if)`
- L39 `  (if)`
- L47 ` else(if)`
- L63 ` 	(if)`
- L68 ` 	(switch)`
- L105 `static void(apply_type0)`
- L110 `static void(apply_type1)`
- L113 ` 	(while)`
- L120 ` 	(if)`
- L126 ` 	(if)`
- L161 ` 	(if)`
- L176 `static void(apply_type2)`
- L179 ` 	(while)`
- L192 ` 	(if)`
- L210 `static void(apply_type3)`
- L213 ` 	(while)`
- L224 ` 	(if)`
- L240 ` 	(while)`
- L256 ` 	(if)`
- L273 ` 	(while)`
- L284 `static void(apply_type6)`
- L287 ` 	(while)`
- L289 ` 	(if)`
- L298 ` 	(if)`
- L314 `static void(apply_type7)`
- L317 ` 	(while)`
- L321 ` 	(if)`
- L323 ` 	(if)`
- L330 `static void(apply_typePerco)`
- L333 ` 	(while)`
- L360 `static boolean(moveFarIfPossible)`
- L367 ` 	(for)`
- L377 ` 	(if)`
- L382 ` 	(if)`
- L427 ` 	(if)`
- L444 ` 	(if)`
- L461 ` 	(if)`
- L486 ` 	(for)`
- L490 ` 	(if)`
- L513 ` 	(if)`
- L518 ` 	(if)`
- L523 ` 	(if)`
- L529 ` 	(if)`
- L536 `static boolean(invocIfPossible)`
- L553 `static SortStats(getInvocSpell)`
- L557 ` 	(for)`
- L561 ` 	(for)`
- L575 ` 	(if)`
- L585 ` 	(for)`
- L589 ` 	(if)`
- L592 ` 	(if)`
- L595 ` 	(if)`
- L608 ` 	(for)`
- L617 ` 	(if)`
- L636 `static boolean(buffIfPossible)`
- L647 `static SortStats(getBuffSpell)`
- L652 ` 	(if)`
- L665 ` 	(for)`
- L676 `static SortStats(getHealSpell)`
- L681 ` 	(if)`
- L694 ` 	(for)`
- L706 `static boolean(moveNearIfPossible)`
- L718 ` 	(if)`
- L721 ` 	(for)`
- L724 ` 	(if)`
- L737 ` 	(for)`
- L753 ` 	(for)`
- L757 ` 	(if)`
- L777 `static Fighter(getNearestFriend)`
- L782 ` 	(for)`
- L789 ` 	(if)`
- L798 `static Fighter(getNearestEnnemy)`
- L803 ` 	(for)`
- L809 ` 	(if)`
- L818 `static Map<Integer,Fighter>(getLowHpEnnemyList)`
- L823 ` 	(for)`
- L827 ` 	(if)`
- L834 ` 	(while)`
- L838 ` 	(for)`
- L857 ` 	(for)`
- L860 ` 	(if)`
- L868 ` 	(if)`
- L879 ` 	(if)`
- L888 ` 	(for)`
- L895 ` 	(if)`
- L903 ` 	(if)`
- L919 `static boolean(moveToAttackIfPossible)`
- L935 ` 	(for)`
- L937 ` 	(for)`
- L939 ` 	(for)`
- L947 ` 	(if)`
- L951 ` 	(if)`
- L977 ` 	(for)`
- L981 ` 	(if)`
- L1002 `static ArrayList <SortStats>(getLaunchableSort)`
- L1008 ` 	(for)`
- L1026 `static ArrayList <SortStats>(TriInfluenceSorts)`
- L1034 ` 	(for)`
- L1041 ` 	(while)`
- L1046 ` 	(for)`
- L1049 ` 	(if)`
- L1063 `static ArrayList <Fighter>(getPotentialTarget)`
- L1068 ` 	(for)`
- L1075 ` 	(for)`
- L1084 `static SortStats(getBestSpellForTarget)`
- L1089 ` 	(if)`
- L1100 ` 	(if)`
- L1114 ` 	(if)`
- L1128 ` 	(if)`
- L1138 ` 	(for)`
- L1146 ` 	(if)`
- L1153 ` 	(for)`
- L1160 ` 	(if)`
- L1167 ` 	(for)`
- L1173 ` 	(if)`
- L1184 `static int(getBestTargetZone)`
- L1193 ` 	(if)`
- L1196 ` 	(if)`
- L1206 ` 	(if)`
- L1221 ` 	(if)`
- L1227 ` 	(for)`
- L1236 ` 	(for)`
- L1246 ` 	(for)`
- L1256 ` 	(if)`
- L1262 ` 	(catch)`
- L1269 `static int(calculInfluenceHeal)`
- L1273 ` 	(for)`
- L1281 `static int(calculInfluence)`
- L1286 ` 	(for)`
- L1289 ` 	(switch)`

#### `common/Pathfinding.java` — 762 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Pathfinding
Fonctions :
- L16 `static int(isValidPath)`
- L19 ` 	(synchronized)`
- L33 ` 	(if)`
- L39 ` 	(if)`
- L41 ` 	(for)`
- L44 ` 	(if)`
- L54 ` 	(if)`
- L77 `static ArrayList<Fighter>(getEnemyFighterArround)`
- L82 ` 	(for)`
- L86 ` 	(if)`
- L97 `static boolean(isNextTo)`
- L105 `static String(ValidSinglePath)`
- L116 `  (if)`
- L128 `  (if)`
- L133 `  (if)`
- L139 ` 	(for)`
- L142 ` 	(if)`
- L153 `static int(GetCaseIDFromDirrection)`
- L156 ` 	(switch)`
- L177 `static int(getDistanceBetween)`
- L186 `static int(newCaseAfterPush)`
- L193 ` 	(if)`
- L211 `static char(getOpositeDirection)`
- L214 ` 	(switch)`
- L235 `static boolean(casesAreInSameLine)`
- L254 ` 	(for)`
- L267 `static ArrayList<Fighter>(getCiblesByZoneByWeapon)`
- L272 ` 	(if)`
- L278 ` 	(switch)`
- L323 `static Fighter(get1StFighterOnCellFromDirection)`
- L332 `static Fighter(getFighter2CellBefore)`
- L338 `static char(getDirBetweenTwoCase)`
- L346 ` 	(if)`
- L353 ` 	(for)`
- L365 `static ArrayList<Case>(getCellListFromAreaString)`
- L374 ` 	(switch)`
- L382 ` 	(for)`
- L384 ` 	(for)`
- L397 ` 	(for)`
- L427 `static int(getCellXCoord)`
- L434 `static int(getCellYCoord)`
- L443 `static boolean(checkLoS)`
- L451 ` 	(if)`
- L453 ` 	(for)`
- L459 ` 	(if)`
- L468 `static int(getNearestCellAround)`
- L476 ` 	(for)`
- L489 `static ArrayList<Case>(getShortestPathBetween)`
- L500 ` 	(while)`
- L504 ` 	(if)`
- L507 ` 	(if)`
- L535 ` 	(if)`
- L539 ` 	(while)`
- L544 ` 	(if)`
- L547 ` 	(if)`
- L577 `static String(getShortestStringPathBetween)`
- L586 ` 	(for)`
- L590 ` 	(if)`
- L599 ` 	(if)`
- L606 `static ArrayList<Integer>(getListCaseFromFighter)`
- L619 ` 	(while)`
- L622 ` 	(if)`
- L630 ` 	(if)`
- L632 ` 	(if)`
- L644 `static int(getCellFromPath)`
- L648 ` 	(while)`
- L662 `static ArrayList<Integer>(triCellList)`
- L670 ` 	(while)`
- L673 ` 	(for)`
- L676 ` 	(if)`
- L689 `static boolean(isBord1)`
- L694 ` 	(for)`
- L704 `static boolean(isBord2)`
- L709 ` 	(for)`
- L719 `static ArrayList<Integer>(getLoS)`
- L726 ` 	(for)`
- L733 ` 	(while)`
- L739 ` 	(if)`
- L747 `static ArrayList<Fighter>(getFightersAround)`
- L752 ` 	(for)`

#### `common/SQLManager.java` — 3038 lignes
Rôle : Accès SQL historique Ancestrar : chargements/sauvegardes directs.
Classe(s) : class SQLManager
Fonctions :
- L35 `static ResultSet(executeQuery)`
- L52 `static PreparedStatement(newTransact)`
- L59 `static void(commitTransacts)`
- L63 ` 	(if)`
- L78 `static void(closeCons)`
- L92 `final boolean(setUpConnexion)`
- L101 ` 	(if)`
- L119 `static void(closeResultSet)`
- L128 `static void(closePreparedStatement)`
- L135 `static void(UPDATE_ACCOUNT_DATA)`
- L162 `static void(UPDATE_LASTCONNECTION_INFO)`
- L186 `static void(UPDATE_ACCOUNT_SUBSCRIBE)`
- L208 `static void(LOAD_ACCOUNTS_DATA)`
- L213 ` 	(while)`
- L227 `static void(LOAD_CRAFTS)`
- L233 ` 	(while)`
- L238 ` 	(for)`
- L263 `static void(LOAD_GUILDS)`
- L268 ` 	(while)`
- L292 `static void(LOAD_GUILD_MEMBERS)`
- L297 ` 	(while)`
- L310 `static void(LOAD_MOUNTS)`
- L315 ` 	(while)`
- L346 `static int(LOAD_DROPS)`
- L352 ` 	(while)`
- L371 `static void(LOAD_ITEMSETS)`
- L376 ` 	(while)`
- L394 `static void(LOAD_IOTEMPLATE)`
- L399 ` 	(while)`
- L419 `static int(LOAD_MOUNTPARKS)`
- L425 ` 	(while)`
- L450 `static void(LOAD_JOBS)`
- L455 ` 	(while)`
- L472 `static void(LOAD_AREA)`
- L478 ` 	(while)`
- L497 `static void(LOAD_SUBAREA)`
- L502 ` 	(while)`
- L524 `static int(LOAD_NPCS)`
- L530 ` 	(while)`
- L547 `static int(LOAD_PERCEPTEURS)`
- L553 ` 	(while)`
- L582 `static int(LOAD_HOUSES)`
- L588 ` 	(while)`
- L619 `static int(getNextPersonnageGuid)`
- L637 `static void(LOAD_PERSO_BY_ACCOUNT)`
- L642 ` 	(while)`
- L696 ` 	(if)`
- L712 `static void(LOAD_PERSO)`
- L718 ` 	(while)`
- L774 ` 	(if)`
- L790 `static void(LOAD_PERSOS)`
- L795 ` 	(while)`
- L859 `static boolean(DELETE_PERSO_IN_BDD)`
- L869 ` 	(if)`
- L878 ` 	(if)`
- L886 ` 	(if)`
- L905 `static boolean(ADD_PERSO_IN_BDD)`
- L943 `static void(LOAD_EXP)`
- L956 `static int(LOAD_TRIGGERS)`
- L962 ` 	(while)`
- L966 ` 	(switch)`
- L988 `static void(LOAD_MAPS)`
- L994 ` 	(while)`
- L1014 ` 	(while)`
- L1028 `static void(SAVE_PERSONNAGE)`
- L1127 ` 	(if)`
- L1136 ` 	(for)`
- L1179 `static void(LOAD_SORTS)`
- L1184 ` 	(while)`
- L1213 `static void(LOAD_OBJ_TEMPLATE)`
- L1218 ` 	(while)`
- L1244 `static SortStats(parseSortStats)`
- L1280 ` 	(for)`
- L1289 `static void(LOAD_MOB_TEMPLATE)`
- L1293 ` 	(while)`
- L1310 ` 	(if)`
- L1348 `static void(LOAD_NPC_TEMPLATE)`
- L1353 ` 	(while)`
- L1397 `static void(SAVE_NEW_ITEM)`
- L1414 `static boolean(SAVE_NEW_FIXGROUP)`
- L1431 `static void(LOAD_NPC_QUESTIONS)`
- L1436 ` 	(while)`
- L1457 `static void(LOAD_NPC_ANSWERS)`
- L1462 ` 	(while)`
- L1479 `static int(LOAD_ENDFIGHT_ACTIONS)`
- L1485 ` 	(while)`
- L1502 `static int(LOAD_ITEM_ACTIONS)`
- L1508 ` 	(while)`
- L1526 `static void(LOAD_ITEMS)`
- L1532 ` 	(while)`
- L1560 `static void(DELETE_ITEM)`
- L1574 `static void(SAVE_ITEM)`
- L1593 `static void(CREATE_MOUNT)`
- L1624 `static void(REMOVE_MOUNT)`
- L1638 `static void(UPDATE_MOUNT_INFOS)`
- L1682 `static void(SAVE_MOUNTPARK)`
- L1704 `static void(UPDATE_MOUNTPARK)`
- L1722 `static boolean(SAVE_TRIGGER)`
- L1745 `static boolean(REMOVE_TRIGGER)`
- L1764 `static boolean(SAVE_MAP_DATA)`
- L1786 `static boolean(DELETE_NPC_ON_MAP)`
- L1803 `static boolean(DELETE_PERCO)`
- L1819 `static boolean(ADD_NPC_ON_MAP)`
- L1840 `static boolean(ADD_PERCO_ON_MAP)`
- L1865 `static void(UPDATE_PERCO)`
- L1887 `static boolean(ADD_ENDFIGHTACTION)`
- L1909 `static boolean(DEL_ENDFIGHTACTION)`
- L1931 `static void(SAVE_NEWGUILD)`
- L1950 `static void(DEL_GUILD)`
- L1965 `static void(DEL_ALL_GUILDMEMBER)`
- L1980 `static void(DEL_GUILDMEMBER)`
- L1995 `static void(UPDATE_GUILD)`
- L2023 `static void(UPDATE_GUILDMEMBER)`
- L2049 `static int(isPersoInGuild)`
- L2071 `static int[](isPersoInGuild)`
- L2079 ` 	(if)`
- L2095 `static boolean(ADD_REPONSEACTION)`
- L2129 `static boolean(UPDATE_INITQUESTION)`
- L2148 `static boolean(UPDATE_NPCREPONSES)`
- L2167 `static void(LOAD_ACTION)`
- L2184 ` 	(while)`
- L2188 ` 	(if)`
- L2193 ` 	(if)`
- L2198 ` 	(if)`
- L2203 ` 	(if)`
- L2213 ` 	(switch)`
- L2318 `static void(LOAD_ITEMS)`
- L2322 `  (while)`
- L2336 `static void(TIMER)`
- L2338 ` 	(if)`
- L2342 ` void(run)`
- L2355 `static boolean(persoExist)`
- L2363 ` 	(if)`
- L2377 `static void(HOUSE_BUY)`
- L2404 ` 	(for)`
- L2422 `static void(HOUSE_SELL)`
- L2441 `static void(HOUSE_CODE)`
- L2460 `static void(HOUSE_GUILD)`
- L2480 `static void(HOUSE_GUILD_REMOVE)`
- L2496 `static void(UPDATE_HOUSE)`
- L2526 `static int(GetNewIDPercepteur)`
- L2534 ` 	(while)`
- L2547 `static int(LOAD_ZAAPIS)`
- L2556 ` 	(while)`
- L2558 ` 	(if)`
- L2563 ` else(if)`
- L2586 `static int(LOAD_ZAAPS)`
- L2592 ` 	(while)`
- L2605 `static int(getNextObjetID)`
- L2627 `static int(LOAD_HDVS)`
- L2633 ` 	(while)`
- L2653 `static int(LOAD_HDVS_ITEMS)`
- L2659 ` 	(while)`
- L2682 `static void(SAVE_HDVS_ITEMS)`
- L2696 ` 	(for)`
- L2717 `static void(LOAD_ANIMATIONS)`
- L2722 ` 	(while)`
- L2740 `static int(LOAD_TRUNK)`
- L2746 `  (while)`
- L2770 `static void(TRUNK_CODE)`
- L2787 `static void(UPDATE_TRUNK)`
- L2805 `static void(ADD_ACCOUNT_DATA)`
- L2823 `static void(UPDATE_BANK)`
- L2839 `static void(UPDATE_FL_AND_EL)`
- L2855 `static int(LOAD_PETS)`
- L2861 ` 	(while)`
- L2883 `static int(LOAD_PETS_ENTRY)`
- L2889 ` 	(while)`
- L2910 `static void(ADD_PETS_DATA)`
- L2929 `static void(UPDATE_PETS_DATA)`
- L2950 `static void(REMOVE_PETS_DATA)`
- L2964 `static int(LOAD_CHALLENGES)`
- L2970 ` 	(while)`
- L2990 `static int(LOAD_GIFTS)`
- L2996 ` 	(while)`
- L3010 ` 	(catch)`
- L3017 `static void(DELETE_GIFT_BY_ACCOUNT)`

#### `common/SocketManager.java` — 2611 lignes
Rôle : Constructeur central de packets réseau envoyés au client.
Classe(s) : class SocketManager
Fonctions :
- L33 `static void(send)`
- L46 `static void(send)`
- L56 `static void(GAME_SEND_Af_PACKET)`
- L66 `static void(GAME_SEND_HELLOGAME_PACKET)`
- L74 `static void(GAME_SEND_ATTRIBUTE_FAILED)`
- L82 `static void(GAME_SEND_ATTRIBUTE_SUCCESS)`
- L90 `static void(GAME_SEND_AV0)`
- L98 `static void(GAME_SEND_HIDE_GENERATE_NAME)`
- L106 `static void(GAME_SEND_PERSO_LIST)`
- L111 ` 	(for)`
- L121 `static void(GAME_SEND_NAME_ALREADY_EXIST)`
- L130 `static void(GAME_SEND_CREATE_PERSO_FULL)`
- L138 `static void(GAME_SEND_CREATE_OK)`
- L146 `static void(GAME_SEND_DELETE_PERSO_FAILED)`
- L154 `static void(GAME_SEND_CREATE_FAILED)`
- L163 `static void(GAME_SEND_PERSO_SELECTION_FAILED)`
- L171 `static void(GAME_SEND_STATS_PACKET)`
- L179 `static void(GAME_SEND_Rx_PACKET)`
- L187 `static void(GAME_SEND_Rn_PACKET)`
- L195 `static void(GAME_SEND_Re_PACKET)`
- L205 `static void(GAME_SEND_ASK)`
- L220 `static void(GAME_SEND_ALIGNEMENT)`
- L228 `static void(GAME_SEND_ADD_CANAL)`
- L236 `static void(GAME_SEND_ZONE_ALLIGN_STATUT)`
- L244 `static void(GAME_SEND_SEESPELL_OPTION)`
- L252 `static void(GAME_SEND_RESTRICTIONS)`
- L260 `static void(GAME_SEND_Ow_PACKET)`
- L268 `static void(GAME_SEND_OT_PACKET)`
- L277 `static void(GAME_SEND_SEE_FRIEND_CONNEXION)`
- L285 `static void(GAME_SEND_GAME_CREATE)`
- L293 `static void(GAME_SEND_SERVER_HOUR)`
- L301 `static void(GAME_SEND_SERVER_DATE)`
- L309 `static void(GAME_SEND_MAPDATA)`
- L317 `static void(GAME_SEND_GDK_PACKET)`
- L325 `static void(GAME_SEND_MAP_MOBS_GMS_PACKETS)`
- L334 `static void(GAME_SEND_MAP_OBJECTS_GDS_PACKETS)`
- L343 `static void(GAME_SEND_MAP_NPCS_GMS_PACKETS)`
- L352 `static void(GAME_SEND_MAP_PERCO_GMS_PACKETS)`
- L361 `static void(GAME_SEND_MAP_GMS_PACKETS)`
- L369 `static void(GAME_SEND_ERASE_ON_MAP_TO_MAP)`
- L373 ` 	(for)`
- L381 `static void(GAME_SEND_ERASE_ON_MAP_TO_FIGHT)`
- L398 `static void(GAME_SEND_ON_FIGHTER_KICK)`
- L402 ` 	(for)`
- L410 `static void(GAME_SEND_ALTER_FIGHTER_MOUNT)`
- L415 ` 	(for)`
- L420 ` 	(if)`
- L422 ` 	(for)`
- L431 `static void(GAME_SEND_ADD_PLAYER_TO_MAP)`
- L439 `static void(GAME_SEND_DUEL_Y_AWAY)`
- L447 `static void(GAME_SEND_DUEL_E_AWAY)`
- L455 `static void(GAME_SEND_MAP_NEW_DUEL_TO_MAP)`
- L463 `static void(GAME_SEND_CANCEL_DUEL_TO_MAP)`
- L471 `static void(GAME_SEND_MAP_START_DUEL_TO_MAP)`
- L479 `static void(GAME_SEND_MAP_FIGHT_COUNT)`
- L487 `static void(GAME_SEND_FIGHT_GJK_PACKET_TO_FIGHT)`
- L492 ` 	(for)`
- L500 `static void(GAME_SEND_FIGHT_PLACES_PACKET_TO_FIGHT)`
- L504 ` 	(for)`
- L513 `static void(GAME_SEND_MAP_FIGHT_COUNT_TO_MAP)`
- L521 `static void(GAME_SEND_GAME_ADDFLAG_PACKET_TO_MAP)`
- L530 `static void(GAME_SEND_GAME_ADDFLAG_PACKET_TO_PLAYER)`
- L539 `static void(GAME_SEND_GAME_REMFLAG_PACKET_TO_MAP)`
- L547 `static void(GAME_SEND_ADD_IN_TEAM_PACKET_TO_MAP)`
- L556 `static void(GAME_SEND_ADD_IN_TEAM_PACKET_TO_PLAYER)`
- L565 `static void(GAME_SEND_REMOVE_IN_TEAM_PACKET_TO_MAP)`
- L574 `static void(GAME_SEND_MAP_MOBS_GMS_PACKETS_TO_MAP)`
- L582 `static void(GAME_SEND_MAP_MOBS_GM_PACKET)`
- L591 `static void(GAME_SEND_MAP_GMS_PACKETS)`
- L600 `static void(GAME_SEND_ON_EQUIP_ITEM)`
- L608 `static void(GAME_SEND_ON_EQUIP_ITEM_FIGHT)`
- L625 `static void(GAME_SEND_FIGHT_CHANGE_PLACE_PACKET_TO_FIGHT)`
- L629 ` 	(for)`
- L638 `static void(GAME_SEND_FIGHT_CHANGE_OPTION_PACKET_TO_MAP)`
- L646 `static void(GAME_SEND_FIGHT_PLAYER_READY_TO_FIGHT)`
- L651 ` 	(for)`
- L660 `static void(GAME_SEND_GJK_PACKET)`
- L669 `static void(GAME_SEND_FIGHT_PLACES_PACKET)`
- L677 `static void(GAME_SEND_Im_PACKET_TO_ALL)`
- L686 `static void(GAME_SEND_Im_PACKET)`
- L694 `static void(GAME_SEND_ILS_PACKET)`
- L702 `static void(GAME_SEND_ILF_PACKET)`
- L710 `static void(GAME_SEND_Im_PACKET_TO_MAP)`
- L718 `static void(GAME_SEND_eUK_PACKET_TO_MAP)`
- L726 `static void(GAME_SEND_Im_PACKET_TO_FIGHT)`
- L730 ` 	(for)`
- L739 `static void(GAME_SEND_MESSAGE)`
- L747 `static void(GAME_SEND_MESSAGE_TO_MAP)`
- L755 `static void(GAME_SEND_GA903_ERROR_PACKET)`
- L763 `static void(GAME_SEND_GIC_PACKETS_TO_FIGHT)`
- L768 ` 	(for)`
- L773 ` 	(for)`
- L782 `static void(GAME_SEND_GIC_PACKET_TO_FIGHT)`
- L787 ` 	(for)`
- L797 `static void(GAME_SEND_GS_PACKET_TO_FIGHT)`
- L801 ` 	(for)`
- L811 `static void(GAME_SEND_GS_PACKET)`
- L819 `static void(GAME_SEND_GTL_PACKET_TO_FIGHT)`
- L822 ` 	(for)`
- L831 `static void(GAME_SEND_GTL_PACKET)`
- L839 `static void(GAME_SEND_GTM_PACKET_TO_FIGHT)`
- L844 ` 	(for)`
- L847 ` 	(if)`
- L857 ` 	(for)`
- L866 `static void(GAME_SEND_GAMETURNSTART_PACKET_TO_FIGHT)`
- L870 ` 	(for)`
- L879 `static void(GAME_SEND_GAMETURNSTART_PACKET)`
- L887 `static void(GAME_SEND_GV_PACKET)`
- L895 `static void(GAME_SEND_PONG)`
- L903 `static void(GAME_SEND_QPONG)`
- L911 `static void(GAME_SEND_GAS_PACKET_TO_FIGHT)`
- L915 ` 	(for)`
- L924 `static void(GAME_SEND_GA_PACKET_TO_FIGHT)`
- L930 ` 	(for)`
- L940 `static void(GAME_SEND_GA_PACKET)`
- L953 `static void(GAME_SEND_GA_PACKET_TO_FIGHT)`
- L957 ` 	(for)`
- L966 `static void(GAME_SEND_GAMEACTION_TO_FIGHT)`
- L969 ` 	(for)`
- L978 `static void(GAME_SEND_GAF_PACKET_TO_FIGHT)`
- L982 ` 	(for)`
- L990 `static void(GAME_SEND_BN)`
- L998 `static void(GAME_SEND_BN)`
- L1006 `static void(GAME_SEND_GAMETURNSTOP_PACKET_TO_FIGHT)`
- L1010 ` 	(for)`
- L1019 `static void(GAME_SEND_GTR_PACKET_TO_FIGHT)`
- L1023 ` 	(for)`
- L1032 `static void(GAME_SEND_EMOTICONE_TO_MAP)`
- L1040 `static void(GAME_SEND_SPELL_UPGRADE_FAILED)`
- L1048 `static void(GAME_SEND_SPELL_UPGRADE_SUCCED)`
- L1056 `static void(GAME_SEND_SPELL_LIST)`
- L1064 `static void(GAME_SEND_FIGHT_PLAYER_DIE_TO_FIGHT)`
- L1068 ` 	(for)`
- L1077 `static void(GAME_SEND_FIGHT_GE_PACKET_TO_FIGHT)`
- L1081 ` 	(for)`
- L1090 `static void(GAME_SEND_FIGHT_GE_PACKET)`
- L1098 `static void(GAME_SEND_FIGHT_GIE_TO_FIGHT)`
- L1103 ` 	(for)`
- L1112 `static void(GAME_SEND_MAP_FIGHT_GMS_PACKETS_TO_FIGHT)`
- L1116 ` 	(for)`
- L1126 `static void(GAME_SEND_MAP_FIGHT_GMS_PACKETS)`
- L1135 `static void(GAME_SEND_FIGHT_PLAYER_JOIN)`
- L1139 ` 	(for)`
- L1142 ` 	(if)`
- L1153 `static void(GAME_SEND_cMK_PACKET)`
- L1161 `static void(GAME_SEND_cMK_PACKET_TO_INCARNAM)`
- L1165 ` 	(for)`
- L1168 ` 	(if)`
- L1176 `static void(GAME_SEND_FIGHT_LIST_PACKET)`
- L1181 ` 	(for)`
- L1183 ` 	(if)`
- L1193 `static void(GAME_SEND_cMK_PACKET_TO_MAP)`
- L1201 `static void(GAME_SEND_cMK_PACKET_TO_GUILD)`
- L1205 ` 	(for)`
- L1213 `static void(GAME_SEND_cMK_PACKET_TO_ALL)`
- L1222 `static void(GAME_SEND_cMK_PACKET_TO_ALIGN)`
- L1226 ` 	(for)`
- L1228 ` 	(if)`
- L1236 `static void(GAME_SEND_cMK_PACKET_TO_ADMIN)`
- L1244 `static void(GAME_SEND_cMK_PACKET_TO_FIGHT)`
- L1248 ` 	(for)`
- L1257 `static void(GAME_SEND_GDZ_PACKET_TO_FIGHT)`
- L1261 ` 	(for)`
- L1271 `static void(GAME_SEND_GDC_PACKET_TO_FIGHT)`
- L1275 ` 	(for)`
- L1285 `static void(GAME_SEND_GA2_PACKET)`
- L1293 `static void(GAME_SEND_CHAT_ERROR_PACKET)`
- L1301 `static void(GAME_SEND_eD_PACKET_TO_MAP)`
- L1309 `static void(GAME_SEND_ECK_PACKET)`
- L1318 `static void(GAME_SEND_ECK_PACKET)`
- L1327 `static void(GAME_SEND_ITEM_VENDOR_LIST_PACKET)`
- L1335 `static void(GAME_SEND_ITEM_LIST_PACKET_PERCEPTEUR)`
- L1343 `static void(GAME_SEND_ITEM_LIST_PACKET_SELLER)`
- L1351 `static void(GAME_SEND_EV_PACKET)`
- L1359 `static void(GAME_SEND_DCK_PACKET)`
- L1367 `static void(GAME_SEND_QUESTION_PACKET)`
- L1375 `static void(GAME_SEND_END_DIALOG_PACKET)`
- L1383 `static void(GAME_SEND_CONSOLE_MESSAGE_PACKET)`
- L1391 `static void(GAME_SEND_BUY_ERROR_PACKET)`
- L1399 `static void(GAME_SEND_SELL_ERROR_PACKET)`
- L1407 `static void(GAME_SEND_BUY_OK_PACKET)`
- L1415 `static void(GAME_SEND_OBJECT_QUANTITY_PACKET)`
- L1423 `static void(GAME_SEND_OAKO_PACKET)`
- L1431 `static void(GAME_SEND_ESK_PACKET)`
- L1439 `static void(GAME_SEND_REMOVE_ITEM_PACKET)`
- L1447 `static void(GAME_SEND_DELETE_OBJECT_FAILED_PACKET)`
- L1455 `static void(GAME_SEND_OBJET_MOVE_PACKET)`
- L1466 `static void(GAME_SEND_EMOTICONE_TO_FIGHT)`
- L1470 ` 	(for)`
- L1479 `static void(GAME_SEND_OAEL_PACKET)`
- L1487 `static void(GAME_SEND_NEW_LVL_PACKET)`
- L1495 `static void(GAME_SEND_MESSAGE_TO_ALL)`
- L1499 ` 	(for)`
- L1506 `static void(GAME_SEND_EXCHANGE_REQUEST_OK)`
- L1514 `static void(GAME_SEND_EXCHANGE_REQUEST_ERROR)`
- L1522 `static void(GAME_SEND_EXCHANGE_CONFIRM_OK)`
- L1530 `static void(GAME_SEND_EXCHANGE_MOVE_OK)`
- L1540 `static void(GAME_SEND_EXCHANGE_OTHER_MOVE_OK)`
- L1550 `static void(GAME_SEND_EXCHANGE_OK)`
- L1558 `static void(GAME_SEND_EXCHANGE_VALID)`
- L1566 `static void(GAME_SEND_GROUP_INVITATION_ERROR)`
- L1573 `static void(GAME_SEND_GROUP_INVITATION)`
- L1581 `static void(GAME_SEND_GROUP_CREATE)`
- L1589 `static void(GAME_SEND_PL_PACKET)`
- L1597 `static void(GAME_SEND_PR_PACKET)`
- L1605 `static void(GAME_SEND_PV_PACKET)`
- L1613 `static void(GAME_SEND_ALL_PM_ADD_PACKET)`
- L1619 ` 	(for)`
- L1629 `static void(GAME_SEND_PM_ADD_PACKET_TO_GROUP)`
- L1637 `static void(GAME_SEND_PM_MOD_PACKET_TO_GROUP)`
- L1645 `static void(GAME_SEND_PM_DEL_PACKET_TO_GROUP)`
- L1653 `static void(GAME_SEND_cMK_PACKET_TO_GROUP)`
- L1661 `static void(GAME_SEND_FIGHT_DETAILS)`
- L1674 `static void(GAME_SEND_IQ_PACKET)`
- L1682 `static void(GAME_SEND_JN_PACKET)`
- L1690 `static void(GAME_SEND_GDF_PACKET_TO_MAP)`
- L1700 `static void(GAME_SEND_GA_PACKET_TO_MAP)`
- L1710 `static void(GAME_SEND_EL_BANK_PACKET)`
- L1718 `static void(GAME_SEND_EL_TRUNK_PACKET)`
- L1726 `static void(GAME_SEND_JX_PACKET)`
- L1731 ` 	(for)`
- L1739 `static void(GAME_SEND_JO_PACKET)`
- L1742 ` 	(for)`
- L1750 `static void(GAME_SEND_JO_PACKET)`
- L1758 `static void(GAME_SEND_JS_PACKET)`
- L1762 ` 	(for)`
- L1770 `static void(GAME_SEND_EsK_PACKET)`
- L1778 `static void(GAME_SEND_FIGHT_SHOW_CASE)`
- L1782 ` 	(for)`
- L1789 `static void(GAME_SEND_Ea_PACKET)`
- L1797 `static void(GAME_SEND_EA_PACKET)`
- L1805 `static void(GAME_SEND_Ec_PACKET)`
- L1813 `static void(GAME_SEND_Em_PACKET)`
- L1821 `static void(GAME_SEND_IO_PACKET_TO_MAP)`
- L1829 `static void(GAME_SEND_FRIENDLIST_PACKET)`
- L1834 ` 	(if)`
- L1844 `static void(GAME_SEND_FRIEND_ONLINE)`
- L1852 `static void(GAME_SEND_FA_PACKET)`
- L1860 `static void(GAME_SEND_FD_PACKET)`
- L1868 `static void(GAME_SEND_Rp_PACKET)`
- L1878 ` 	(if)`
- L1891 `static void(GAME_SEND_OS_PACKET)`
- L1902 ` 	(if)`
- L1906 ` 	(for)`
- L1923 `static void(GAME_SEND_MOUNT_DESCRIPTION_PACKET)`
- L1931 `static void(GAME_SEND_Rr_PACKET)`
- L1939 `static void(GAME_SEND_ALTER_GM_PACKET)`
- L1947 `static void(GAME_SEND_Ee_PACKET)`
- L1955 `static void(GAME_SEND_cC_PACKET)`
- L1963 `static void(GAME_SEND_ADD_NPC_TO_MAP)`
- L1971 `static void(GAME_SEND_ADD_PERCO_TO_MAP)`
- L1979 `static void(GAME_SEND_GDO_PACKET_TO_MAP)`
- L1987 `static void(GAME_SEND_GDO_PACKET)`
- L1995 `static void(GAME_SEND_ZC_PACKET)`
- L2003 `static void(GAME_SEND_GIP_PACKET)`
- L2011 `static void(GAME_SEND_gn_PACKET)`
- L2019 `static void(GAME_SEND_gC_PACKET)`
- L2027 `static void(GAME_SEND_gV_PACKET)`
- L2035 `static void(GAME_SEND_gIM_PACKET)`
- L2039 ` 	(switch)`
- L2049 `static void(GAME_SEND_gIB_PACKET)`
- L2057 `static void(GAME_SEND_gIH_PACKET)`
- L2065 `static void(GAME_SEND_gS_PACKET)`
- L2074 `static void(GAME_SEND_gJ_PACKET)`
- L2082 `static void(GAME_SEND_gK_PACKET)`
- L2090 `static void(GAME_SEND_gIG_PACKET)`
- L2108 `static void(MESSAGE_BOX)`
- L2116 `static void(GAME_SEND_WC_PACKET)`
- L2124 `static void(GAME_SEND_WV_PACKET)`
- L2132 `static void(GAME_SEND_ZAAPI_PACKET)`
- L2138 `static void(GAME_SEND_CLOSE_ZAAPI_PACKET)`
- L2144 `static void(GAME_SEND_WUE_PACKET)`
- L2152 `static void(GAME_SEND_EMOTE_LIST)`
- L2160 `static void(GAME_SEND_NO_EMOTE)`
- L2168 `static void(REALM_SEND_TOO_MANY_PLAYER_ERROR)`
- L2176 `static void(REALM_SEND_REQUIRED_APK)`
- L2193 `static void(GAME_SEND_ADD_ENEMY)`
- L2202 `static void(GAME_SEND_iAEA_PACKET)`
- L2211 `static void(GAME_SEND_ENEMY_LIST)`
- L2220 `static void(GAME_SEND_iD_COMMANDE)`
- L2228 `static void(GAME_SEND_BWK)`
- L2236 `static void(GAME_SEND_KODE)`
- L2244 `static void(GAME_SEND_hOUSE)`
- L2253 `static void(GAME_SEND_FORGETSPELL_INTERFACE)`
- L2261 `static void(GAME_SEND_R_PACKET)`
- L2269 `static void(GAME_SEND_gIF_PACKET)`
- L2277 `static void(GAME_SEND_gITM_PACKET)`
- L2285 `static void(GAME_SEND_gITp_PACKET)`
- L2293 `static void(GAME_SEND_gITP_PACKET)`
- L2301 `static void(GAME_SEND_IH_PACKET)`
- L2309 `static void(GAME_SEND_FLAG_PACKET)`
- L2317 `static void(GAME_SEND_DELETE_FLAG_PACKET)`
- L2325 `static void(GAME_SEND_gT_PACKET)`
- L2333 `static void(GAME_SEND_PERCO_INFOS_PACKET)`
- L2344 `static void(GAME_SEND_GUILDHOUSE_PACKET)`
- L2352 `static void(GAME_SEND_GUILDENCLO_PACKET)`
- L2362 `static void(GAME_SEND_HDVITEM_SELLING)`
- L2367 ` 	(for)`
- L2381 `static void(GAME_SEND_EHm_PACKET)`
- L2390 `static void(GAME_SEND_EHM_PACKET)`
- L2429 `static void(GAME_SEND_WEDDING)`
- L2438 `static void(GAME_SEND_PF)`
- L2446 `static void(GAME_SEND_MERCHANT_LIST)`
- L2454 `  (if)`
- L2464 `static void(GAME_SEND_UPDATE_OBJECT_DISPLAY_PACKET)`
- L2473 `static void(GAME_SEND_WELCOME)`
- L2482 `static void(GAME_SEND_TAXE)`
- L2489 `static void(GAME_SEND_INFO_HIGHLIGHT_PACKET)`
- L2497 `static void(GAME_SEND_CHALLENGE_FIGHT)`
- L2502 ` 	(for)`
- L2512 `static void(GAME_SEND_CHALLENGE_PERSO)`
- L2521 `static void(GAME_SEND_Im_PACKET_TO_CHALLENGE)`
- L2526 ` 	(for)`
- L2535 `static void(GAME_SEND_SUBSCRIBE_MESSAGE)`
- L2544 `static void(GAME_SEND_CRAFT_PUBLIC_MODE)`
- L2553 `static void(GAME_SEND_CRAFT_PUBLIC_MODE)`
- L2562 `static void(GAME_SEND_Ej_PACKET)`
- L2571 `static void(GAME_SEND_EJ_PACKET)`
- L2580 `static void(GAME_SEND_ATTRIBUTE_GIFT_SUCCESS)`
- L2589 `static void(GAME_SEND_GIFT)`
- L2597 `static void(GAME_SEND_GA_CLEAR_PACKET_TO_FIGHT)`
- L2601 ` 	(for)`

#### `common/World.java` — 1971 lignes
Rôle : Registre global : maps, joueurs, comptes, templates, mobs, sorts, guildes, maisons, cache.
Classe(s) : class World
Fonctions :
- L79 ` public(Drop)`
- L87 ` void(setMax)`
- L91 ` int(get_itemID)`
- L94 ` int(getMinProsp)`
- L98 ` float(get_taux)`
- L102 ` int(get_max)`
- L113 ` public(ItemSet)`
- L118 ` 	(for)`
- L131 ` 	(for)`
- L135 ` 	(for)`
- L150 ` int(getId)`
- L155 ` Stats(getBonusStatByItemNumb)`
- L161 ` ArrayList<ObjTemplate>(getItemTemplates)`
- L172 ` public(SuperArea)`
- L177 ` void(addArea)`
- L182 ` int(get_id)`
- L195 ` public(Area)`
- L202 ` 	(if)`
- L208 ` String(get_name)`
- L212 ` int(get_id)`
- L216 ` SuperArea(get_superArea)`
- L221 ` void(addSubArea)`
- L226 ` ArrayList<Carte>(getMaps)`
- L243 ` public(SubArea)`
- L252 ` String(get_name)`
- L256 ` int(get_id)`
- L260 ` Area(get_area)`
- L264 ` int(get_alignement)`
- L268 ` ArrayList<Carte>(getMaps)`
- L272 ` void(addMap)`
- L276 ` boolean(get_subscribe)`
- L287 ` public(Couple)`
- L302 ` public(IOTemplate)`
- L311 ` int(getId)`
- L315 ` boolean(isWalkable)`
- L318 ` int(getRespawnTime)`
- L322 ` int(getDuration)`
- L325 ` int(getUnk)`
- L340 ` public(Exchange)`
- L346 `synchronized public long(getKamas)`
- L361 `synchronized public void(toogleOK)`
- L369 ` 	(if)`
- L376 ` else(if)`
- L389 `synchronized public void(setKamas)`
- L404 ` 	(if)`
- L417 `synchronized public void(cancel)`
- L427 `synchronized public void(apply)`
- L433 ` 	(for)`
- L458 ` 	(for)`
- L497 `synchronized public void(addItem)`
- L517 ` 	(if)`
- L520 ` 	(if)`
- L533 ` 	(if)`
- L545 `synchronized public void(removeItem)`
- L565 ` 	(if)`
- L598 `synchronized private Couple<Integer, Integer>(getCoupleInList)`
- L601 ` 	(for)`
- L608 `synchronized int(getQuaItem)`
- L616 ` 	(for)`
- L619 ` 	(if)`
- L637 ` public(ExpLevel)`
- L648 `static void(createWorld)`
- L771 `static Area(getArea)`
- L776 `static SuperArea(getSuperArea)`
- L781 `static SubArea(getSubArea)`
- L786 `static void(addArea)`
- L791 `static void(addSuperArea)`
- L796 `static void(addSubArea)`
- L801 `static void(addNPCreponse)`
- L806 `static NPC_reponse(getNPCreponse)`
- L811 `static int(getExpLevelSize)`
- L816 `static void(addExpLevel)`
- L821 `static Compte(getCompte)`
- L826 `static void(addNPCQuestion)`
- L831 `static NPC_question(getNPCQuestion)`
- L836 `static NPC_tmpl(getNPCTemplate)`
- L840 `static void(addNpcTemplate)`
- L845 `static Carte(getCarte)`
- L850 `static void(addCarte)`
- L856 `static void(delCarte)`
- L862 `static Compte(getCompteByName)`
- L867 `static Personnage(getPersonnage)`
- L872 `static void(addAccount)`
- L878 `static void(removeAccount)`
- L881 ` 	(if)`
- L887 `static void(addPersonnage)`
- L893 `static Personnage(getPersoByName)`
- L898 `static void(deletePerso)`
- L901 ` 	(if)`
- L910 ` 	(for)`
- L913 ` 	(if)`
- L929 `static String(getSousZoneStateString)`
- L936 `static long(getPersoXpMin)`
- L943 `static long(getPersoXpMax)`
- L950 `static void(addSort)`
- L955 `static void(addObjTemplate)`
- L960 `static Sort(getSort)`
- L965 `static ObjTemplate(getObjTemplate)`
- L970 `static Collection<ObjTemplate>(getObjTemplates)`
- L975 `static int(getNewItemGuid)`
- L981 `static void(addMobTemplate)`
- L986 `static Monstre(getMonstre)`
- L991 `static List<Personnage>(getOnlinePersos)`
- L995 ` 	(for)`
- L997 ` 	(if)`
- L999 ` 	(if)`
- L1007 `static void(addObjet)`
- L1014 `static Objet(getObjet)`
- L1018 `static void(removeItem)`
- L1021 ` 	(if)`
- L1029 `static void(addIOTemplate)`
- L1034 `static Dragodinde(getDragoByID)`
- L1039 `static void(addDragodinde)`
- L1043 `static void(removeDragodinde)`
- L1047 `static void(saveAll)`
- L1065 ` 	(for)`
- L1075 ` 	(for)`
- L1084 ` 	(for)`
- L1093 ` 	(for)`
- L1103 ` 	(for)`
- L1105 ` 	(if)`
- L1115 ` 	(for)`
- L1117 ` 	(if)`
- L1127 ` 	(for)`
- L1129 ` 	(if)`
- L1139 ` 	(for)`
- L1149 ` 	(for)`
- L1163 ` 	(if)`
- L1193 `static void(RefreshAllMob)`
- L1196 ` 	(for)`
- L1202 `static ExpLevel(getExpLevel)`
- L1207 `static IOTemplate(getIOTemplate)`
- L1211 `static Metier(getMetier)`
- L1215 `static void(addJob)`
- L1220 `static void(addCraft)`
- L1225 `static ArrayList<Couple<Integer,Integer>>(getCraft)`
- L1230 `static int(getObjectByIngredientForJob)`
- L1234 ` 	(for)`
- L1237 ` 	(if)`
- L1244 ` 	(for)`
- L1253 `static Compte(getCompteByPseudo)`
- L1258 `static void(addItemSet)`
- L1263 `static ItemSet(getItemSet)`
- L1268 `static int(getItemSetNumber)`
- L1273 `static int(getNextIdForMount)`
- L1280 `static Carte(getCarteByPosAndCont)`
- L1283 ` 	(for)`
- L1292 `static void(addGuild)`
- L1297 `static int(getNextHighestGuildID)`
- L1304 `static boolean(guildNameIsUsed)`
- L1310 `static boolean(guildEmblemIsUsed)`
- L1312 ` 	(for)`
- L1318 `static Guild(getGuild)`
- L1322 `static long(getGuildXpMax)`
- L1328 `static void(ReassignAccountToChar)`
- L1333 ` 	(for)`
- L1335 ` 	(if)`
- L1341 `static int(getZaapCellIdByMapId)`
- L1344 ` 	(for)`
- L1350 `static int(getEncloCellIdByMapId)`
- L1352 ` 	(if)`
- L1354 ` 	(if)`
- L1362 `static void(delDragoByID)`
- L1367 `static void(removeGuild)`
- L1381 `static boolean(ipIsUsed)`
- L1387 `static void(unloadPerso)`
- L1391 ` 	(if)`
- L1393 ` 	(for)`
- L1400 `static boolean(isArenaMap)`
- L1403 ` 	(for)`
- L1410 `static Objet(newObjet)`
- L1412 ` 	(if)`
- L1423 `static byte(getGmAccess)`
- L1428 `static void(setGmAccess)`
- L1434 `static Hdv(getHdv)`
- L1438 `static void(addHdv)`
- L1443 `static String(parse_EHl)`
- L1448 ` 	(for)`
- L1458 `static int(get_averagePrice)`
- L1462 ` 	(for)`
- L1470 `static String(get_HdvsTemplate)`
- L1476 ` 	(for)`
- L1482 `static Map<Integer, ArrayList<HdvEntry>>(getMyItems)`
- L1490 `static HdvEntry(get_HdvEntry)`
- L1495 `static void(addHdvItem)`
- L1517 `static void(removeHdvItem)`
- L1530 `static Personnage(getMarried)`
- L1534 `static void(AddMarried)`
- L1538 ` 	(if)`
- L1556 `static void(PriestRequest)`
- L1561 ` 	(if)`
- L1565 ` 	(if)`
- L1572 `static void(Wedding)`
- L1575 ` 	(if)`
- L1588 `static Animations(getAnimation)`
- L1593 `static void(addAnimation)`
- L1598 `static void(addHouse)`
- L1603 `static Map<Integer, House>(getHouses)`
- L1608 `static House(getHouse)`
- L1613 `static void(addPerco)`
- L1618 `static Percepteur(getPerco)`
- L1623 `static Map<Integer, Percepteur>(getPercos)`
- L1628 `static void(addTrunk)`
- L1633 `static Trunk(getTrunk)`
- L1638 `static Map<Integer, Trunk>(getTrunks)`
- L1643 `static void(addMountPark)`
- L1648 `static Map<Short, Carte.MountPark>(getMountPark)`
- L1653 `static String(parseMPtoGuild)`
- L1660 ` 	(for)`
- L1663 ` 	(if)`
- L1673 `static int(totalMPGuild)`
- L1677 ` 	(for)`
- L1679 ` 	(if)`
- L1689 `static void(addSeller)`
- L1706 `static Collection<Integer>(getSeller)`
- L1711 `static void(removeSeller)`
- L1716 `static Bank(GetBank)`
- L1721 `static void(AddBank)`
- L1726 `static FriendList(GetFriends)`
- L1731 `static void(AddFriendList)`
- L1736 `static EnemyList(GetEnemys)`
- L1741 `static void(AddEnemyList)`
- L1746 `static Collection<Compte>(getComptes)`
- L1751 `static void(addPets)`
- L1756 `static Pets(get_Pets)`
- L1761 `static Map<Integer, objects.Pets>(get_Pets)`
- L1766 `static void(addPetsEntry)`
- L1771 `static PetsEntry(get_PetsEntry)`
- L1776 `static Percepteur(getPercepteur)`
- L1781 `static void(addChallenge)`
- L1789 `static String(getChallengeFromConditions)`
- L1805 ` 	(for)`
- L1847 `static ArrayList<String>(getRandomChallenge)`
- L1864 ` 	(while)`
- L1880 ` 	(if)`
- L1887 ` 	(if)`
- L1894 ` 	(if)`
- L1901 ` 	(if)`
- L1913 `static void(addCrafterOnBook)`
- L1916 ` 	(if)`
- L1930 `static Collection<Integer>(getCrafterOnBook)`
- L1935 `static void(removeCrafterOnBook)`
- L1940 `static void(removeCrafterOnBook)`
- L1943 ` 	(for)`
- L1945 ` 	(for)`
- L1952 `static void(MoveMobsOnMaps)`
- L1955 ` 	(for)`
- L1960 `static Gift(getGift)`
- L1965 `static void(addGift)`

#### `communication/ComServer.java` — 184 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ComServer implements Runnable
Fonctions :
- L16 ` public(ComServer)`
- L33 ` void(run)`
- L56 ` 	(while)`
- L58 ` 	(if)`
- L77 ` void(sendChangeState)`
- L83 ` void(addBanIP)`
- L89 ` void(lockGMlevel)`
- L95 ` void(sendGetOnline)`
- L101 ` void(parsePacket)`
- L104 `  (switch)`
- L107 ` 	(switch)`
- L157 ` 	(switch)`
- L163 ` 	(if)`
- L174 ` 	(switch)`

#### `game/GameServer.java` — 245 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class GameServer implements Runnable
Fonctions :
- L30 ` public(GameServer)`
- L38 ` void(run)`
- L43 ` 	(if)`
- L45 ` 	(if)`
- L52 ` 	(if)`
- L58 ` 	(if)`
- L64 ` 	(for)`
- L66 ` 	(if)`
- L68 ` 	(if)`
- L95 ` void(run)`
- L97 ` 	(if)`
- L105 ` ArrayList<GameThread>(getClients)`
- L109 ` long(getStartTime)`
- L114 ` int(getMaxPlayer)`
- L119 ` int(getPlayerNumber)`
- L124 ` void(run)`
- L140 ` 	(catch)`
- L144 ` void(kickAll)`
- L152 ` 	(for)`
- L160 `static void(addToLog)`
- L164 ` 	(if)`
- L174 `static void(addToSockLog)`
- L178 ` 	(if)`
- L188 ` void(delClient)`
- L194 `synchronized Compte(getWaitingCompte)`
- L204 `synchronized void(delWaitingCompte)`
- L209 `synchronized void(addWaitingCompte)`
- L214 `static String(getServerTime)`
- L220 `static String(getServerDate)`
- L225 ` 	(while)`
- L231 ` 	(while)`
- L239 ` Thread(getThread)`

#### `game/GameThread.java` — 4632 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class GameThread implements Runnable
Fonctions :
- L47 ` public(GameAction)`
- L55 ` public(GameThread)`
- L67 ` 	(catch)`
- L75 ` void(run)`
- L83 `  (while)`
- L85 ` 	(if)`
- L103 ` 	(if)`
- L120 ` void(parsePacket)`
- L123 ` 	(if)`
- L126 ` 	(if)`
- L132 ` 	(if)`
- L137 ` 	(switch)`
- L199 ` void(parse_JobPacket)`
- L202 ` 	(switch)`
- L223 ` void(parseHousePacket)`
- L226 ` 	(switch)`
- L250 ` void(parseHouseKodePacket)`
- L253 ` 	(switch)`
- L263 ` void(House_code)`
- L266 ` 	(switch)`
- L284 ` void(parse_enemyPacket)`
- L287 ` 	(switch)`
- L300 ` void(Enemy_add)`
- L305 ` 	(switch)`
- L310 ` 	(if)`
- L320 ` 	(if)`
- L330 ` 	(if)`
- L338 ` 	(if)`
- L345 ` void(Enemy_delete)`
- L350 ` 	(switch)`
- L355 ` 	(if)`
- L365 ` 	(if)`
- L375 ` 	(if)`
- L383 ` 	(if)`
- L390 ` void(parseWaypointPacket)`
- L393 ` 	(switch)`
- L409 ` void(Zaapi_close)`
- L414 ` void(Zaapi_use)`
- L417 ` 	(if)`
- L424 ` void(Waypoint_quit)`
- L429 ` void(Waypoint_use)`
- L440 ` void(parseGuildPacket)`
- L443 ` 	(switch)`
- L449 ` 	(switch)`
- L484 ` 	(if)`
- L531 ` void(guild_perco_join_fight)`
- L543 ` 	(switch)`
- L546 ` 	(if)`
- L560 ` 	(if)`
- L568 ` void(guild_remove_perco)`
- L579 ` 	(for)`
- L581 ` 	(if)`
- L592 ` void(guild_add_perco)`
- L623 ` 	(for)`
- L625 ` 	(if)`
- L636 ` void(guild_enclo)`
- L639 ` 	(if)`
- L648 ` 	(if)`
- L654 ` 	(if)`
- L664 ` void(guild_house)`
- L667 ` 	(if)`
- L677 ` 	(if)`
- L682 ` 	(if)`
- L687 ` 	(if)`
- L697 ` void(guild_promote)`
- L792 ` void(guild_CancelCreate)`
- L797 ` void(guild_kick)`
- L805 ` 	(if)`
- L826 ` 	(if)`
- L832 ` 	(if)`
- L838 ` 	(if)`
- L864 ` void(guild_join)`
- L867 ` 	(switch)`
- L871 ` 	(if)`
- L876 ` 	(if)`
- L881 ` 	(if)`
- L886 ` 	(if)`
- L891 ` 	(if)`
- L935 ` void(guild_infos)`
- L938 ` 	(switch)`
- L962 ` void(guild_create)`
- L966 ` 	(if)`
- L981 ` 	(if)`
- L991 ` 	(if)`
- L999 ` 	(if)`
- L1002 ` 	(for)`
- L1010 ` 	(if)`
- L1012 ` 	(if)`
- L1025 ` 	(if)`
- L1032 ` 	(if)`
- L1058 ` void(parseChanelPacket)`
- L1061 ` 	(switch)`
- L1068 ` void(Chanels_change)`
- L1072 ` 	(switch)`
- L1083 ` void(parseMountPacket)`
- L1086 ` 	(switch)`
- L1092 ` 	(if)`
- L1097 ` 	(if)`
- L1102 ` 	(if)`
- L1107 ` 	(if)`
- L1114 ` 	(if)`
- L1119 ` 	(if)`
- L1126 ` 	(if)`
- L1131 ` 	(if)`
- L1142 ` 	(for)`
- L1171 ` 	(if)`
- L1176 ` 	(if)`
- L1181 ` 	(if)`
- L1190 ` 	(for)`
- L1203 ` void(Mount_changeXpGive)`
- L1215 ` void(Mount_name)`
- L1222 ` void(Mount_ride)`
- L1225 ` 	(if)`
- L1232 ` void(Mount_description)`
- L1246 ` void(parse_friendPacket)`
- L1249 ` 	(switch)`
- L1261 ` 	(switch)`
- L1278 ` void(FriendLove)`
- L1283 ` 	(if)`
- L1291 ` 	(switch)`
- L1300 ` 	(if)`
- L1301 ` 	(if)`
- L1316 ` void(Friend_delete)`
- L1320 ` 	(switch)`
- L1335 ` 	(if)`
- L1353 ` 	(if)`
- L1360 ` void(Friend_add)`
- L1365 ` 	(switch)`
- L1380 ` 	(if)`
- L1398 ` 	(if)`
- L1405 ` void(parseGroupPacket)`
- L1408 ` 	(switch)`
- L1432 ` 	(if)`
- L1467 ` 	(for)`
- L1470 ` 	(if)`
- L1482 ` 	(for)`
- L1509 ` void(group_locate)`
- L1517 ` 	(for)`
- L1525 ` void(group_quit)`
- L1550 ` void(group_invite)`
- L1557 ` 	(if)`
- L1562 ` 	(if)`
- L1567 ` 	(if)`
- L1577 ` void(group_refuse)`
- L1589 ` void(group_accept)`
- L1597 ` 	(if)`
- L1618 ` void(parseObjectPacket)`
- L1621 ` 	(switch)`
- L1637 ` void(Object_drop)`
- L1652 ` 	(if)`
- L1657 ` 	(if)`
- L1669 ` 	(if)`
- L1687 ` void(Object_use)`
- L1708 ` 	(if)`
- L1728 `synchronized void(Object_move)`
- L1747 ` 	(if)`
- L1750 ` 	(if)`
- L1783 ` 	(if)`
- L1795 ` 	(if)`
- L1808 ` 	(if)`
- L1826 ` 	(if)`
- L1883 ` 	(if)`
- L1901 ` 	(if)`
- L1919 ` 	(if)`
- L1923 ` 	(for)`
- L1932 ` 	(if)`
- L1937 ` 	(if)`
- L1940 ` 	(if)`
- L1946 ` 	(if)`
- L1959 ` void(Object_delete)`
- L1972 ` 	(if)`
- L1978 ` 	(if)`
- L1996 ` void(parseDialogPacket)`
- L1999 ` 	(switch)`
- L2014 ` void(Dialog_response)`
- L2024 ` 	(if)`
- L2035 ` void(Dialog_end)`
- L2042 ` void(Dialog_start)`
- L2050 `  (if)`
- L2056 ` 	(if)`
- L2061 ` 	(if)`
- L2071 ` void(parseExchangePacket)`
- L2074 ` 	(switch)`
- L2088 ` 	(switch)`
- L2097 ` 	(if)`
- L2099 ` 	(for)`
- L2133 `  (if)`
- L2138 `  (if)`
- L2152 ` 	(if)`
- L2159 ` 	(if)`
- L2164 `  (if)`
- L2169 `  (if)`
- L2181 `  (for)`
- L2206 ` void(Job_Public_Activation)`
- L2209 ` 	(switch)`
- L2219 ` void(Exchange_HDV)`
- L2226 ` 	(switch)`
- L2254 ` 	(if)`
- L2321 ` void(Exchange_mountPark)`
- L2325 ` 	(if)`
- L2336 ` 	(switch)`
- L2340 ` 	(if)`
- L2350 ` 	(if)`
- L2436 ` 	(if)`
- L2460 ` void(Exchange_doAgain)`
- L2466 ` void(Exchange_isOK)`
- L2469 ` 	(if)`
- L2478 ` void(Exchange_onMoveItem)`
- L2482 ` 	(if)`
- L2484 ` 	(switch)`
- L2487 ` 	(if)`
- L2529 ` 	(if)`
- L2533 ` 	(switch)`
- L2540 ` 	(if)`
- L2567 ` 	(if)`
- L2583 ` 	(if)`
- L2585 ` 	(switch)`
- L2635 ` 	(if)`
- L2676 ` 	(if)`
- L2682 ` 	(if)`
- L2713 ` 	(if)`
- L2724 ` 	(if)`
- L2727 ` 	(switch)`
- L2765 ` 	(switch)`
- L2781 `  (if)`
- L2786 `  (switch)`
- L2811 `  (for)`
- L2813 `  (if)`
- L2830 ` 	(switch)`
- L2846 ` 	(switch)`
- L2849 ` 	(if)`
- L2900 ` void(Exchange_accept)`
- L2903 ` 	(if)`
- L2913 ` 	(if)`
- L2926 ` void(Exchange_onSellItem)`
- L2934 ` 	(if)`
- L2945 ` void(Exchange_onBuyItem)`
- L2949 `  (if)`
- L2953 `  (if)`
- L2963 `  (if)`
- L2974 `  (if)`
- L2998 `  (if)`
- L3053 ` void(Exchange_finish_buy)`
- L3068 ` 	(if)`
- L3076 ` 	(if)`
- L3083 ` 	(if)`
- L3086 ` 	(if)`
- L3088 ` 	(if)`
- L3097 ` 	(if)`
- L3103 ` 	(if)`
- L3123 ` 	(if)`
- L3143 ` void(Exchange_start)`
- L3150 ` 	(switch)`
- L3165 ` 	(switch)`
- L3170 ` 	(if)`
- L3176 ` 	(if)`
- L3202 ` 	(if)`
- L3208 ` 	(if)`
- L3239 ` 	(if)`
- L3251 ` 	(if)`
- L3272 ` 	(if)`
- L3282 ` 	(if)`
- L3333 ` void(parse_environementPacket)`
- L3336 ` 	(switch)`
- L3347 ` void(Environement_emote)`
- L3374 ` void(Environement_change_direction)`
- L3385 ` void(parseSpellPacket)`
- L3388 ` 	(switch)`
- L3401 ` void(addToSpellBook)`
- L3409 ` 	(if)`
- L3418 ` void(boostSort)`
- L3425 ` 	(if)`
- L3438 ` void(forgetSpell)`
- L3446 ` 	(if)`
- L3455 ` void(parseFightPacket)`
- L3460 ` 	(switch)`
- L3496 ` void(parseBasicsPacket)`
- L3499 ` 	(switch)`
- L3521 ` void(Basic_state)`
- L3524 ` 	(switch)`
- L3527 ` 	(if)`
- L3542 ` 	(if)`
- L3555 ` Personnage(getPerso)`
- L3560 ` void(Basic_console)`
- L3566 ` void(Basic_chatMessage)`
- L3570 ` 	(if)`
- L3578 ` 	(switch)`
- L3585 ` 	(if)`
- L3588 ` 	(if)`
- L3593 ` 	(if)`
- L3599 ` 	(if)`
- L3618 ` 	(if)`
- L3638 ` 	(if)`
- L3711 ` 	(if)`
- L3740 ` 	(if)`
- L3761 ` void(Basic_send_Date_Hour)`
- L3767 ` void(Basic_infosmessage)`
- L3775 ` void(parseGamePacket)`
- L3778 ` 	(switch)`
- L3820 ` void(Game_onLeftFight)`
- L3824 ` 	(if)`
- L3844 ` void(Game_on_showCase)`
- L3858 ` void(Game_on_Ready)`
- L3867 ` void(Game_on_ChangePlace_packet)`
- L3877 ` void(Game_on_GK_packet)`
- L3890 ` 	(switch)`
- L3894 ` 	(if)`
- L3897 ` 	(if)`
- L3911 ` 	(if)`
- L3915 ` 	(if)`
- L3920 ` else(if)`
- L3960 ` void(Game_on_GI_packet)`
- L3963 ` 	(if)`
- L3988 ` void(parseGameActionPacket)`
- L3998 ` 	(if)`
- L4004 ` 	(switch)`
- L4030 ` 	(if)`
- L4034 ` 	(if)`
- L4065 ` void(house_action)`
- L4071 ` 	(switch)`
- L4085 ` void(game_perco)`
- L4093 ` 	(if)`
- L4108 ` 	(if)`
- L4116 ` 	(if)`
- L4126 ` void(game_aggro)`
- L4141 ` 	(if)`
- L4147 ` 	(if)`
- L4159 ` void(game_action)`
- L4179 ` void(game_tryCac)`
- L4194 ` void(game_tryCastSpell)`
- L4202 ` 	(if)`
- L4210 ` void(game_join_fight)`
- L4214 ` 	(if)`
- L4226 ` 	(if)`
- L4240 ` 	(if)`
- L4249 ` void(game_accept_duel)`
- L4261 ` void(game_cancel_duel)`
- L4274 ` void(game_ask_duel)`
- L4277 ` 	(if)`
- L4285 ` 	(if)`
- L4288 ` 	(if)`
- L4293 ` 	(if)`
- L4305 ` void(game_parseDeplacementPacket)`
- L4309 ` 	(if)`
- L4311 ` 	(if)`
- L4324 ` 	(if)`
- L4335 ` 	(if)`
- L4355 ` PrintWriter(get_out)`
- L4359 ` void(kick)`
- L4365 ` 	(if)`
- L4376 ` void(parseAccountPacket)`
- L4379 ` 	(switch)`
- L4383 ` 	(if)`
- L4392 ` 	(if)`
- L4400 ` 	(if)`
- L4405 ` 	(for)`
- L4412 ` 	(if)`
- L4417 ` 	(if)`
- L4422 ` 	(if)`
- L4424 ` 	(if)`
- L4437 ` 	(if)`
- L4442 ` 	(if)`
- L4470 ` 	(if)`
- L4507 ` 	(for)`
- L4543 ` 	(if)`
- L4547 ` 	(if)`
- L4562 ` 	(if)`
- L4568 ` 	(if)`
- L4571 ` 	(if)`
- L4584 ` 	(if)`
- L4608 ` Thread(getThread)`
- L4613 ` void(removeAction)`
- L4621 ` void(addAction)`

#### `objects/Action.java` — 719 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Action
Fonctions :
- L27 ` public(Action)`
- L34 ` void(apply)`
- L39 ` 	(if)`
- L46 ` 	(switch)`
- L50 ` 	(if)`
- L60 ` 	(if)`
- L66 ` 	(if)`
- L74 `  (if)`
- L78 ` else(if)`
- L112 ` 	(if)`
- L125 ` 	(if)`
- L158 ` 	(if)`
- L174 ` 	(if)`
- L176 ` 	(if)`
- L179 ` else(if)`
- L192 ` 	(if)`
- L218 ` 	(if)`
- L226 ` 	(if)`
- L271 ` 	(switch)`
- L294 ` 	(if)`
- L332 ` 	(if)`
- L364 ` 	(if)`
- L370 ` 	(if)`
- L376 ` 	(if)`
- L385 ` else(if)`
- L402 ` 	(if)`
- L415 ` 	(if)`
- L505 ` 	(for)`
- L512 ` 	(if)`
- L531 ` 	(if)`
- L544 ` 	(if)`
- L549 ` 	(if)`
- L568 ` 	(if)`
- L593 ` 	(switch)`
- L612 ` 	(if)`
- L628 ` 	(if)`
- L634 ` 	(if)`
- L642 ` 	(if)`
- L668 ` 	(if)`
- L712 ` int(getID)`

#### `objects/Animations.java` — 59 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Animations
Fonctions :
- L11 ` public(Animations)`
- L21 ` int(getId)`
- L26 ` String(getName)`
- L31 ` int(getArea)`
- L36 ` int(getAction)`
- L41 ` int(getSize)`
- L46 ` int(getAnimationId)`
- L51 `static String(PrepareToGA)`

#### `objects/Carte.java` — 1985 lignes
Rôle : Carte Ancestrar : maps/cellules/IO/groupes/fights.
Classe(s) : class Carte
Fonctions :
- L55 ` public(MountPark)`
- L77 ` int(get_owner)`
- L81 ` void(set_owner)`
- L85 ` InteractiveObject(get_door)`
- L89 ` int(get_size)`
- L93 ` Guild(get_guild)`
- L97 ` void(set_guild)`
- L101 ` Carte(get_map)`
- L105 ` int(get_cellid)`
- L109 ` int(get_price)`
- L113 ` void(set_price)`
- L117 ` int(getObjectNumb)`
- L124 ` String(parseData)`
- L130 ` 	(for)`
- L144 ` String(parseDBData)`
- L149 ` 	(for)`
- L157 ` void(addData)`
- L162 ` void(removeData)`
- L167 ` Map<Integer, Integer>(getData)`
- L172 ` int(MountParkDATASize)`
- L177 `static void(removeMountPark)`
- L182 ` 	(if)`
- L184 ` 	(if)`
- L186 ` 	(for)`
- L197 ` 	(for)`
- L215 ` public(InteractiveObject)`
- L229 ` void(actionPerformed)`
- L240 ` int(getID)`
- L245 ` boolean(isInteractive)`
- L250 ` void(setInteractive)`
- L255 ` int(getState)`
- L260 ` void(setState)`
- L265 ` int(getUseDuration)`
- L269 ` 	(if)`
- L275 ` void(startTimer)`
- L282 ` int(getUnknowValue)`
- L286 ` 	(if)`
- L292 ` boolean(isWalkable)`
- L311 ` public(Case)`
- L321 ` InteractiveObject(getObject)`
- L326 ` Objet(getDroppedItem)`
- L330 ` boolean(canDoAction)`
- L333 ` 	(switch)`
- L341 ` 	(switch)`
- L349 ` 	(switch)`
- L358 ` 	(switch)`
- L366 ` 	(switch)`
- L375 ` 	(switch)`
- L383 ` 	(switch)`
- L391 ` 	(switch)`
- L399 ` 	(switch)`
- L408 ` 	(switch)`
- L419 ` 	(switch)`
- L427 ` 	(switch)`
- L435 ` 	(switch)`
- L443 ` 	(switch)`
- L451 ` 	(switch)`
- L459 ` 	(switch)`
- L467 ` 	(switch)`
- L475 ` 	(switch)`
- L483 ` 	(switch)`
- L491 ` 	(switch)`
- L499 ` 	(switch)`
- L507 ` 	(switch)`
- L515 ` 	(switch)`
- L523 ` 	(switch)`
- L531 ` 	(switch)`
- L539 ` 	(switch)`
- L547 ` 	(switch)`
- L561 ` 	(switch)`
- L569 ` 	(switch)`
- L577 ` 	(switch)`
- L585 ` 	(switch)`
- L593 ` 	(switch)`
- L601 ` 	(switch)`
- L609 ` 	(switch)`
- L617 ` 	(switch)`
- L625 ` 	(switch)`
- L633 ` 	(switch)`
- L641 ` 	(switch)`
- L652 ` 	(switch)`
- L660 ` 	(switch)`
- L668 ` 	(switch)`
- L676 ` 	(switch)`
- L684 ` 	(switch)`
- L695 ` 	(switch)`
- L703 ` 	(switch)`
- L711 ` 	(switch)`
- L719 ` 	(switch)`
- L727 ` 	(switch)`
- L735 ` 	(switch)`
- L743 ` 	(switch)`
- L751 ` 	(switch)`
- L759 ` 	(switch)`
- L767 ` 	(switch)`
- L790 ` 	(switch)`
- L805 ` 	(switch)`
- L818 ` 	(switch)`
- L933 ` int(getID)`
- L938 ` void(addOnCellStopAction)`
- L945 ` void(applyOnCellStopActions)`
- L949 ` 	(for)`
- L955 ` void(addPerso)`
- L961 ` void(addFighter)`
- L966 ` void(removeFighter)`
- L970 ` boolean(isWalkable)`
- L975 ` boolean(blockLoS)`
- L979 ` 	(for)`
- L985 ` boolean(isLoS)`
- L989 ` void(removePlayer)`
- L995 ` Map<Integer, Personnage>(getPersos)`
- L1000 ` Map<Integer, Fighter>(getFighters)`
- L1005 ` Fighter(getFirstFighter)`
- L1008 ` 	(for)`
- L1014 ` void(startAction)`
- L1025 ` 	(if)`
- L1030 ` 	(switch)`
- L1070 ` 	(if)`
- L1073 ` 	(for)`
- L1117 ` 	(if)`
- L1122 ` 	(if)`
- L1130 ` 	(if)`
- L1162 `  (if)`
- L1172 `  (if)`
- L1198 ` void(finishAction)`
- L1207 ` 	(if)`
- L1213 ` 	(switch)`
- L1245 ` void(clearOnCellAction)`
- L1250 ` void(addDroppedItem)`
- L1255 ` void(clearDroppedItem)`
- L1261 ` public(Carte)`
- L1292 ` 	(if)`
- L1299 ` 	(for)`
- L1313 ` 	(if)`
- L1323 ` 	(for)`
- L1340 ` 	(if)`
- L1349 ` void(applyEndFightAction)`
- L1355 ` void(addEndFightAction)`
- L1362 ` void(delEndFightAction)`
- L1369 ` void(setMountPark)`
- L1373 ` MountPark(getMountPark)`
- L1377 ` public(Carte)`
- L1387 ` SubArea(getSubArea)`
- L1392 ` int(getX)`
- L1396 ` int(getY)`
- L1400 ` Map<Integer, NPC>(get_npcs)`
- L1404 ` NPC(addNpc)`
- L1415 ` void(spawnGroup)`
- L1425 ` 	(if)`
- L1433 ` void(spawnNewGroup)`
- L1450 ` void(spawnGroupOnCommand)`
- L1463 ` void(addStaticGroup)`
- L1473 ` void(setPlaces)`
- L1478 ` void(removeFight)`
- L1482 ` NPC(getNPC)`
- L1487 ` NPC(RemoveNPC)`
- L1492 ` Case(getCase)`
- L1497 ` ArrayList<Personnage>(getPersos)`
- L1504 ` short(get_id)`
- L1507 ` String(get_date)`
- L1511 ` byte(get_w)`
- L1515 ` byte(get_h)`
- L1519 ` String(get_key)`
- L1523 ` String(get_placesStr)`
- L1527 ` void(addPlayer)`
- L1533 ` String(getGMsPackets)`
- L1540 ` String(getFightersGMsPackets)`
- L1543 ` 	(for)`
- L1545 ` 	(for)`
- L1552 ` String(getMobGroupGMsPackets)`
- L1559 ` 	(for)`
- L1572 ` String(getNpcsGMsPackets)`
- L1580 ` 	(for)`
- L1593 ` String(getObjectsGDsPackets)`
- L1598 ` 	(for)`
- L1600 ` 	(if)`
- L1611 ` int(getNbrFight)`
- L1616 ` Map<Integer, Fight>(get_fights)`
- L1621 ` Fight(newFight)`
- L1633 ` int(getRandomFreeCellID)`
- L1637 ` 	(for)`
- L1643 ` 	(for)`
- L1651 ` 	(for)`
- L1662 ` 	(if)`
- L1670 ` void(refreshSpawns)`
- L1673 ` 	(for)`
- L1685 ` void(onPlayerArriveOnCell)`
- L1690 ` 	(if)`
- L1703 ` 	(if)`
- L1706 ` 	(for)`
- L1722 ` 	(if)`
- L1728 ` 	(if)`
- L1734 ` void(startFigthVersusMonstres)`
- L1746 ` void(startFigthVersusPercepteur)`
- L1758 ` 	(if)`
- L1767 ` Carte(getMapCopy)`
- L1787 ` void(setCases)`
- L1792 ` InteractiveObject(getMountParkDoor)`
- L1795 ` 	(for)`
- L1808 ` Map<Integer, MobGroup>(getMobGroups)`
- L1813 ` void(removeNpcOrMobGroup)`
- L1819 ` int(getMaxGroupNumb)`
- L1824 ` void(setMaxGroup)`
- L1829 ` Fight(getFight)`
- L1834 ` void(sendFloorItems)`
- L1837 ` 	(for)`
- L1843 ` Map<Integer, Case>(GetCases)`
- L1847 ` int(getStoreCount)`
- L1852 ` int(get_maxTeam1)`
- L1856 ` int(get_maxTeam0)`
- L1860 ` boolean(hasEndFightAction)`
- L1905 ` 	(for)`
- L1913 ` 	(for)`
- L1921 ` 	(for)`
- L1932 ` 	(if)`
- L1940 ` void(onMap_MonstersDisplacement)`
- L1946 `  (for)`
- L1957 `  (for)`
- L1964 `  (if)`
- L1978 `  (for)`

#### `objects/Challenge.java` — 797 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Challenge
Fonctions :
- L28 ` public(Challenge)`
- L44 ` int(getXp)`
- L49 ` int(getDrop)`
- L54 ` boolean(get_win)`
- L59 ` void(challenge_win)`
- L66 ` void(challenge_loose)`
- L76 ` String(parsePacket)`
- L81 ` 	(if)`
- L88 ` void(show_cibleToPerso)`
- L97 ` void(show_cibleToFight)`
- L103 ` 	(for)`
- L115 ` 	(switch)`
- L127 `  (for)`
- L139 ` 	(if)`
- L151 ` 	(if)`
- L165 ` 	(switch)`
- L170 `  (for)`
- L186 ` 	(switch)`
- L193 ` 	(if)`
- L196 ` 	(for)`
- L199 ` 	(if)`
- L219 `  (switch)`
- L222 ` 	(if)`
- L224 ` 	(for)`
- L226 ` 	(if)`
- L244 ` 	(for)`
- L246 ` 	(if)`
- L258 ` 	(if)`
- L260 ` 	(if)`
- L264 ` 	(if)`
- L272 ` 	(if)`
- L274 ` 	(if)`
- L278 ` 	(if)`
- L286 ` 	(if)`
- L288 ` 	(if)`
- L292 ` 	(if)`
- L302 ` 	(for)`
- L304 ` 	(if)`
- L315 ` 	(for)`
- L317 ` 	(if)`
- L328 ` 	(for)`
- L330 ` 	(if)`
- L341 ` 	(for)`
- L343 ` 	(if)`
- L355 ` 	(for)`
- L357 ` 	(if)`
- L367 ` 	(for)`
- L369 ` 	(if)`
- L384 ` 	(for)`
- L394 ` 	(for)`
- L396 ` 	(if)`
- L405 ` 	(if)`
- L407 ` 	(for)`
- L409 ` 	(if)`
- L418 ` void(onMob_die)`
- L422 ` 	(if)`
- L429 ` 	(switch)`
- L434 ` 	(if)`
- L445 ` 	(if)`
- L451 ` 	(if)`
- L453 ` 	(if)`
- L460 ` 	(if)`
- L462 ` 	(if)`
- L485 ` 	(if)`
- L488 ` 	(for)`
- L491 ` 	(if)`
- L500 ` 	(if)`
- L510 `  (for)`
- L520 ` 	(if)`
- L526 `  (for)`
- L529 ` 	(if)`
- L539 ` 	(if)`
- L545 `  (for)`
- L548 ` 	(if)`
- L558 ` void(onPlayer_move)`
- L562 ` 	(switch)`
- L572 ` void(onPlayer_action)`
- L582 ` 	(switch)`
- L597 ` void(onPlayer_cac)`
- L601 ` 	(switch)`
- L619 ` void(onPlayer_spell)`
- L623 ` 	(switch)`
- L630 ` void(onfight_StartTurn)`
- L634 ` 	(switch)`
- L651 `  (for)`
- L664 ` 	(for)`
- L668 ` 	(for)`
- L675 ` 	(for)`
- L682 ` 	(if)`
- L692 ` void(onfight_EndTurn)`
- L700 ` 	(switch)`
- L711 ` 	(if)`
- L720 ` 	(if)`
- L726 ` 	(if)`
- L732 ` 	(if)`
- L739 ` 	(if)`
- L741 ` 	(for)`
- L749 ` 	(if)`
- L751 ` 	(for)`
- L758 ` 	(if)`
- L760 ` 	(for)`
- L767 ` 	(if)`
- L769 ` 	(for)`
- L781 ` 	(for)`

#### `objects/Compte.java` — 700 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Compte
Fonctions :
- L21 ` public(FriendList)`
- L33 ` 	(catch)`
- L38 ` ArrayList<String>(GetFriends)`
- L43 ` String(parseFriends)`
- L48 ` 	(for)`
- L62 ` public(EnemyList)`
- L72 ` 	(catch)`
- L77 ` ArrayList<String>(GetEnemys)`
- L82 ` String(parseEnemys)`
- L87 ` 	(for)`
- L122 ` public(Compte)`
- L143 ` 	(if)`
- L157 ` 	(if)`
- L159 ` 	(if)`
- L161 ` 	(for)`
- L177 ` Map<Integer, Objet>(get_bank)`
- L181 ` Bank(getBank)`
- L186 ` int(getBankCost)`
- L191 ` long(GetBankKamas)`
- L196 ` void(setBankKamas)`
- L201 ` String(getBankItemsIDSplitByChar)`
- L205 ` 	(for)`
- L214 ` FriendList(GetFriends)`
- L218 ` EnemyList(GetEnemys)`
- L223 ` String(parseFriendList)`
- L227 ` 	(for)`
- L239 ` String(parseEnemyList)`
- L243 ` 	(for)`
- L255 ` void(SendOnline)`
- L258 ` 	(for)`
- L264 ` void(addFriend)`
- L267 ` 	(if)`
- L272 ` 	(if)`
- L280 ` void(addEnemy)`
- L283 ` 	(if)`
- L288 ` 	(if)`
- L296 ` void(removeFriend)`
- L301 ` void(removeEnemy)`
- L307 ` boolean(isFriendWith)`
- L310 ` 	(for)`
- L317 ` boolean(isEnemyWith)`
- L320 ` 	(for)`
- L329 ` boolean(isMuted)`
- L333 ` void(mute)`
- L342 ` 	(if)`
- L346 ` void(actionPerformed)`
- L366 ` int(countHdvItems)`
- L373 ` HdvEntry[](getHdvItems)`
- L385 ` boolean(recoverItem)`
- L408 ` 	(if)`
- L417 ` int(get_GUID)`
- L421 ` String(get_name)`
- L426 ` String(get_pass)`
- L431 ` String(get_pseudo)`
- L436 ` void(setLastIP)`
- L441 ` String(get_lastIP)`
- L446 ` String(get_question)`
- L451 ` String(get_reponse)`
- L456 ` boolean(isBanned)`
- L461 ` void(setBanned)`
- L466 ` int(get_gmLvl)`
- L471 ` void(setGmLvl)`
- L476 ` int(get_subscriber)`
- L481 ` 	(if)`
- L502 ` boolean(get_subscriberMessage)`
- L507 ` void(set_subscriberMessage)`
- L512 ` void(setCurIP)`
- L517 ` String(get_curIP)`
- L522 ` String(getLastConnectionDate)`
- L527 ` void(setLastConnectionDate)`
- L532 ` GameThread(getGameThread)`
- L537 ` void(setGameThread)`
- L542 ` void(setClientKey)`
- L547 ` String(getClientKey)`
- L552 ` boolean(isOnline)`
- L558 ` Map<Integer, Personnage>(get_persos)`
- L563 ` Personnage(get_curPerso)`
- L568 ` int(GET_PERSO_NUMBER)`
- L573 ` void(addPerso)`
- L578 ` boolean(createPerso)`
- L583 ` 	(if)`
- L590 ` void(deletePerso)`
- L597 ` void(setCurPerso)`
- L602 ` void(deconnexion)`
- L611 ` void(resetAllChars)`
- L614 ` 	(for)`
- L621 ` 	(if)`
- L627 ` 	(if)`
- L630 ` 	(if)`
- L632 ` 	(if)`
- L641 ` 	(if)`
- L666 ` Map<Integer,Gift>(getGifts)`
- L671 ` Gift(getGift)`
- L676 ` void(addGift)`
- L681 ` void(sendListGift)`
- L684 ` 	(if)`
- L686 ` 	(for)`
- L694 ` void(removeGift)`

#### `objects/Dragodinde.java` — 242 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Dragodinde
Fonctions :
- L31 ` public(Dragodinde)`
- L50 ` public(Dragodinde)`
- L70 ` 	(for)`
- L79 ` int(get_id)`
- L83 ` int(get_color)`
- L87 ` int(get_sexe)`
- L91 ` int(get_amour)`
- L95 ` String(get_ancetres)`
- L99 ` int(get_endurance)`
- L103 ` int(get_level)`
- L106 ` long(get_exp)`
- L110 ` String(get_nom)`
- L114 ` int(get_fatigue)`
- L118 ` int(get_energie)`
- L122 ` int(get_reprod)`
- L126 ` int(get_maturite)`
- L130 ` int(get_serenite)`
- L134 ` Stats(get_stats)`
- L138 ` ArrayList<Objet>(get_items)`
- L142 ` String(parse)`
- L169 ` String(parseStats)`
- L173 ` 	(for)`
- L181 ` int(getMaxEnergie)`
- L187 ` int(getMaxMatu)`
- L193 ` int(getTotalPod)`
- L200 ` String(parseXpString)`
- L205 ` boolean(isMountable)`
- L213 ` String(getItemsId)`
- L220 ` void(setName)`
- L226 ` void(addXp)`
- L235 ` void(levelUp)`

#### `objects/Fight.java` — 4487 lignes
Rôle : Combat : timeline, tours, actions, fin de combat, drop/xp, placement, challenges.
Classe(s) : class Fight
Fonctions :
- L48 ` public(Piege)`
- L59 ` Case(get_cell)`
- L63 ` byte(get_size)`
- L67 ` Fighter(get_caster)`
- L71 ` void(set_isunHide)`
- L77 ` boolean(get_isunHide)`
- L82 ` void(desappear)`
- L95 ` 	(if)`
- L104 ` void(appear)`
- L116 ` void(onTraped)`
- L135 ` 	(for)`
- L137 ` 	(for)`
- L141 ` 	(if)`
- L157 ` int(get_color)`
- L191 ` Fighter(get_oldCible)`
- L195 ` void(set_oldCible)`
- L198 ` public(Fighter)`
- L209 ` public(Fighter)`
- L213 ` 	(if)`
- L227 ` public(Fighter)`
- L237 ` ArrayList<LaunchedSort>(getLaunchedSorts)`
- L242 ` void(ActualiseLaunchedSort)`
- L248 ` 	(for)`
- L251 ` 	(if)`
- L259 ` void(addLaunchedSort)`
- L265 ` int(getGUID)`
- L270 ` Fighter(get_isHolding)`
- L273 ` void(set_isHolding)`
- L277 ` Fighter(get_holdedBy)`
- L281 ` void(set_holdedBy)`
- L285 ` int(get_gfxID)`
- L289 ` void(set_gfxID)`
- L293 ` ArrayList<SpellEffect>(get_fightBuff)`
- L298 ` void(set_fightCell)`
- L302 ` boolean(isHide)`
- L306 ` Case(get_fightCell)`
- L310 ` void(setTeam)`
- L314 ` boolean(isDead)`
- L317 ` void(setDead)`
- L321 ` boolean(hasLeft)`
- L325 ` void(setLeft)`
- L329 ` Personnage(getPersonnage)`
- L336 ` Percepteur(getPerco)`
- L343 ` boolean(testIfCC)`
- L354 ` Stats(getTotalStats)`
- L370 ` void(initBuffStats)`
- L374 ` 	(if)`
- L376 ` 	(for)`
- L382 ` Stats(getFightBuffStats)`
- L386 ` 	(for)`
- L392 ` String(getGmPacket)`
- L403 ` 	(switch)`
- L415 ` 	(if)`
- L493 ` void(setState)`
- L500 ` boolean(isState)`
- L506 ` void(decrementStates)`
- L512 ` 	(for)`
- L530 ` int(getPDV)`
- L536 ` void(removePDV)`
- L541 ` void(applyBeginningTurnBuff)`
- L544 ` 	(synchronized)`
- L546 ` 	(for)`
- L551 ` 	(for)`
- L553 ` 	(if)`
- L562 ` SpellEffect(getBuff)`
- L565 ` 	(for)`
- L567 ` 	(if)`
- L574 ` boolean(hasBuff)`
- L577 ` 	(for)`
- L579 ` 	(if)`
- L586 ` int(getBuffValue)`
- L590 ` 	(for)`
- L597 ` int(getMaitriseDmg)`
- L601 ` 	(for)`
- L608 ` boolean(getSpellValueBool)`
- L612 ` 	(for)`
- L619 ` void(refreshfightBuff)`
- L624 ` 	(for)`
- L632 ` 	(switch)`
- L635 ` 	(if)`
- L642 ` 	(if)`
- L673 ` void(addBuff)`
- L676 ` 	(if)`
- L717 ` 	(switch)`
- L747 ` 	(if)`
- L759 ` 	(if)`
- L764 ` int(getInitiative)`
- L778 ` int(getPDVMAX)`
- L782 ` int(get_lvl)`
- L795 ` String(xpString)`
- L797 ` 	(if)`
- L805 ` String(getPacketsName)`
- L818 ` MobGrade(getMob)`
- L825 ` int(getTeam)`
- L829 ` int(getTeam2)`
- L833 ` int(getOtherTeam)`
- L837 ` boolean(canPlay)`
- L841 ` void(setCanPlay)`
- L845 ` ArrayList<SpellEffect>(getBuffsByEffectID)`
- L848 ` 	(for)`
- L855 ` Stats(getTotalStatsLessBuff)`
- L869 ` int(getPA)`
- L882 ` int(getPM)`
- L895 ` int(getCurPA)`
- L899 ` int(getCurPM)`
- L904 ` void(setCurPM)`
- L909 ` void(setCurPA)`
- L914 ` void(setInvocator)`
- L919 ` Fighter(getInvocator)`
- L924 ` boolean(isInvocation)`
- L929 ` boolean(isPerco)`
- L934 ` boolean(isDouble)`
- L939 ` void(debuff)`
- L944 ` 	(for)`
- L948 ` 	(switch)`
- L966 ` void(fullPDV)`
- L971 ` void(setIsDead)`
- L976 ` boolean(canLaunchSpell)`
- L982 ` void(unHide)`
- L988 ` 	(switch)`
- L1001 ` 	(for)`
- L1010 ` int(getPdvMaxOutFight)`
- L1017 ` Map<Integer, Integer>(get_chatiValue)`
- L1021 ` int(getDefaultGfx)`
- L1028 ` long(getXpGive)`
- L1034 ` void(addPDV)`
- L1040 ` void(removePDVMAX)`
- L1059 ` public(Glyphe)`
- L1071 ` Case(get_cell)`
- L1075 ` byte(get_size)`
- L1079 ` Fighter(get_caster)`
- L1083 ` byte(get_duration)`
- L1087 ` int(decrementDuration)`
- L1093 ` void(onTraped)`
- L1101 ` void(desapear)`
- L1107 ` int(get_color)`
- L1119 ` public(LaunchedSort)`
- L1126 ` void(ActuCooldown)`
- L1131 ` int(getCooldown)`
- L1136 ` int(getId)`
- L1141 ` Fighter(getTarget)`
- L1146 `static boolean(coolDownGood)`
- L1149 ` 	(for)`
- L1157 `static int(getNbLaunch)`
- L1161 ` 	(for)`
- L1168 `static int(getNbLaunchTarget)`
- L1172 ` 	(for)`
- L1228 ` Timer(TurnTimer)`
- L1233 ` void(actionPerformed)`
- L1237 `  (if)`
- L1239 ` 	(if)`
- L1242 ` 	(for)`
- L1246 ` 	(if)`
- L1264 ` public(Fight)`
- L1279 ` 	(if)`
- L1286 ` 	(if)`
- L1322 ` 	(if)`
- L1336 ` public(Fight)`
- L1347 ` 	(for)`
- L1363 ` 	(if)`
- L1383 ` 	(for)`
- L1387 ` 	(if)`
- L1414 ` 	(for)`
- L1424 ` public(Fight)`
- L1452 ` 	(if)`
- L1472 ` 	(for)`
- L1476 ` 	(if)`
- L1503 ` 	(for)`
- L1512 ` Carte(get_map)`
- L1517 ` List<Piege>(get_traps)`
- L1522 ` List<Glyphe>(get_glyphs)`
- L1527 ` Case(getRandomCell)`
- L1540 ` 	(if)`
- L1547 ` ArrayList<Case>(parsePlaces)`
- L1552 ` int(get_id)`
- L1561 ` 	(if)`
- L1564 ` 	(for)`
- L1570 ` 	(if)`
- L1572 ` 	(for)`
- L1578 ` 	(if)`
- L1580 ` 	(for)`
- L1587 `synchronized void(changePlace)`
- L1601 ` boolean(isOccuped)`
- L1606 ` boolean(groupCellContains)`
- L1616 ` void(verifIfAllReady)`
- L1625 ` 	(if)`
- L1634 ` 	(if)`
- L1639 ` void(startFight)`
- L1643 ` 	(if)`
- L1650 ` 	(if)`
- L1653 ` 	(if)`
- L1670 ` void(actionPerformed)`
- L1676 ` 	(if)`
- L1683 ` 	(for)`
- L1685 `  (if)`
- L1697 `  (for)`
- L1711 `  (for)`
- L1721 `  (for)`
- L1729 ` 	(for)`
- L1744 ` void(startTurn)`
- L1782 ` 	(for)`
- L1787 ` 	(if)`
- L1790 ` 	(if)`
- L1814 ` 	(if)`
- L1839 ` 	(for)`
- L1846 ` void(endTurn)`
- L1855 ` 	(if)`
- L1865 ` 	(if)`
- L1868 ` 	(while)`
- L1877 ` 	(for)`
- L1890 ` 	(if)`
- L1899 ` 	(if)`
- L1904 ` 	(if)`
- L1919 ` 	(for)`
- L1934 `  (for)`
- L1962 ` void(InitOrdreJeu)`
- L1972 ` 	(if)`
- L1975 ` 	(for)`
- L1979 ` 	(if)`
- L1986 ` 	(if)`
- L1989 ` 	(for)`
- L1993 ` 	(if)`
- L2010 ` void(joinFight)`
- L2014 ` 	(if)`
- L2018 ` 	(if)`
- L2022 ` 	(if)`
- L2024 ` 	(if)`
- L2031 ` 	(if)`
- L2033 ` 	(if)`
- L2038 ` 	(if)`
- L2044 ` 	(if)`
- L2046 ` 	(if)`
- L2052 ` 	(if)`
- L2057 ` 	(if)`
- L2082 ` 	(if)`
- L2086 ` 	(if)`
- L2088 ` 	(if)`
- L2095 ` 	(if)`
- L2097 ` 	(if)`
- L2102 ` 	(if)`
- L2108 ` 	(if)`
- L2110 ` 	(if)`
- L2116 ` 	(if)`
- L2121 ` 	(if)`
- L2144 ` 	(if)`
- L2146 ` 	(for)`
- L2148 ` 	(if)`
- L2156 ` void(joinPercepteurFight)`
- L2184 ` void(toggleLockTeam)`
- L2187 ` 	(if)`
- L2201 ` void(toggleOnlyGroup)`
- L2204 ` 	(if)`
- L2218 ` void(toggleLockSpec)`
- L2224 ` 	(if)`
- L2242 ` void(toggleHelp)`
- L2245 ` 	(if)`
- L2259 ` void(set_state)`
- L2264 ` void(set_guildID)`
- L2269 ` int(get_state)`
- L2274 ` int(get_guildID)`
- L2279 ` int(get_type)`
- L2284 ` List<Fighter>(get_ordreJeu)`
- L2289 ` Map<Integer, Challenge>(get_challenges)`
- L2294 ` boolean(fighterDeplace)`
- L2298 ` 	(if)`
- L2307 ` 	(if)`
- L2323 ` 	(if)`
- L2333 ` 	(if)`
- L2355 ` 	(if)`
- L2373 `  (if)`
- L2383 `  (if)`
- L2392 ` 	(if)`
- L2412 `  (if)`
- L2421 `  (if)`
- L2429 `  (if)`
- L2439 ` 	(for)`
- L2450 `  (for)`
- L2459 ` void(onGK)`
- L2469 ` 	(for)`
- L2483 ` void(playerPass)`
- L2492 ` int(tryCastSpell)`
- L2500 ` 	(if)`
- L2511 ` 	(if)`
- L2519 ` 	(for)`
- L2529 ` 	(if)`
- L2535 ` 	(if)`
- L2582 ` boolean(CanCastSpell)`
- L2586 ` 	(if)`
- L2597 ` 	(if)`
- L2600 ` 	(if)`
- L2609 ` 	(if)`
- L2612 ` 	(if)`
- L2619 ` 	(if)`
- L2622 ` 	(if)`
- L2629 ` 	(if)`
- L2632 ` 	(if)`
- L2642 ` 	(if)`
- L2652 ` 	(if)`
- L2661 ` 	(if)`
- L2666 ` 	(if)`
- L2669 ` 	(if)`
- L2695 ` String(GetGE)`
- L2715 `  (if)`
- L2727 `  (for)`
- L2732 `  (for)`
- L2735 `  (if)`
- L2746 `  (for)`
- L2756 ` 	(for)`
- L2774 `  (for)`
- L2779 `  (for)`
- L2782 `  (if)`
- L2794 `  (while)`
- L2797 `  (for)`
- L2800 `  (if)`
- L2813 `  (for)`
- L2821 `  (for)`
- L2833 `  (for)`
- L2844 `  (if)`
- L2867 `  (if)`
- L2905 ` 	(catch)`
- L2926 ` 	(if)`
- L2942 `  (for)`
- L2947 ` 	(if)`
- L2966 `  (for)`
- L3000 ` 	(if)`
- L3002 ` 	(if)`
- L3034 `  (if)`
- L3037 ` 	(if)`
- L3049 ` 	(if)`
- L3098 ` 	(for)`
- L3103 ` 	(if)`
- L3114 ` 	(for)`
- L3131 ` boolean(verifIfTeamIsDead)`
- L3135 ` 	(for)`
- L3138 ` 	(if)`
- L3146 ` void(verifIfTeamAllDead)`
- L3152 ` 	(for)`
- L3155 ` 	(if)`
- L3161 ` 	(for)`
- L3164 ` 	(if)`
- L3170 ` 	(if)`
- L3174 ` 	(for)`
- L3187 ` 	(for)`
- L3191 ` 	(for)`
- L3211 ` 	(for)`
- L3227 ` 	(for)`
- L3241 ` 	(if)`
- L3253 ` 	(for)`
- L3255 ` 	(if)`
- L3257 ` 	(for)`
- L3260 ` 	(if)`
- L3280 ` 	(if)`
- L3283 ` 	(if)`
- L3295 ` 	(for)`
- L3300 ` 	(if)`
- L3310 ` 	(if)`
- L3313 ` 	(if)`
- L3316 ` 	(if)`
- L3337 ` 	(for)`
- L3339 ` 	(if)`
- L3345 ` 	(for)`
- L3348 ` 	(if)`
- L3360 ` 	(if)`
- L3372 ` 	(if)`
- L3384 ` 	(if)`
- L3387 ` 	(if)`
- L3390 ` 	(if)`
- L3408 ` void(onFighterDie)`
- L3415 ` 	(if)`
- L3430 ` 	(for)`
- L3436 ` 	(if)`
- L3440 ` 	(for)`
- L3461 ` 	(for)`
- L3479 ` 	(if)`
- L3486 ` 	(if)`
- L3489 ` 	(if)`
- L3494 ` 	(if)`
- L3517 ` 	(for)`
- L3527 ` 	(for)`
- L3530 ` 	(if)`
- L3541 ` 	(for)`
- L3543 ` 	(if)`
- L3559 ` 	(for)`
- L3563 ` 	(for)`
- L3577 ` int(getTeamID)`
- L3588 ` int(getOtherTeamID)`
- L3597 ` void(tryCaC)`
- L3608 ` 	(for)`
- L3622 ` 	(if)`
- L3643 ` 	(if)`
- L3652 ` 	(if)`
- L3662 ` 	(if)`
- L3672 ` 	(if)`
- L3676 ` 	(for)`
- L3690 ` Fighter(getFighterByPerso)`
- L3700 ` Fighter(getCurFighter)`
- L3705 ` void(refreshCurPlayerInfos)`
- L3711 ` void(leftFight)`
- L3718 ` 	(if)`
- L3721 ` 	(if)`
- L3729 ` 	(if)`
- L3732 ` 	(switch)`
- L3739 ` 	(if)`
- L3743 ` 	(if)`
- L3767 ` 	(if)`
- L3779 ` 	(if)`
- L3782 ` 	(if)`
- L3785 ` 	(if)`
- L3792 ` 	(if)`
- L3807 ` 	(if)`
- L3817 ` 	(if)`
- L3830 ` 	(if)`
- L3838 ` 	(if)`
- L3840 ` 	(if)`
- L3842 ` 	(if)`
- L3847 ` 	(if)`
- L3849 ` 	(if)`
- L3870 ` 	(if)`
- L3897 ` 	(if)`
- L3899 ` 	(if)`
- L3904 ` 	(if)`
- L3906 ` 	(if)`
- L3926 ` 	(if)`
- L3937 ` 	(if)`
- L3948 ` 	(if)`
- L3951 ` 	(if)`
- L3954 ` 	(if)`
- L3961 ` 	(if)`
- L3976 ` 	(if)`
- L3985 ` 	(if)`
- L3997 ` 	(if)`
- L4009 ` 	(if)`
- L4025 ` 	(if)`
- L4027 ` 	(for)`
- L4030 ` 	(if)`
- L4045 ` 	(if)`
- L4048 ` 	(if)`
- L4068 ` 	(if)`
- L4080 ` 	(if)`
- L4083 ` 	(if)`
- L4086 ` 	(if)`
- L4093 ` 	(if)`
- L4107 ` 	(if)`
- L4116 ` 	(if)`
- L4159 ` String(getGTL)`
- L4163 ` 	(for)`
- L4169 ` int(getNextLowerFighterGuid)`
- L4173 ` 	(for)`
- L4181 ` void(addFighterInTeam)`
- L4189 ` String(parseFightInfos)`
- L4198 ` 	(switch)`
- L4238 ` void(showCaseToTeam)`
- L4244 ` 	(if)`
- L4246 ` 	(for)`
- L4252 ` else(if)`
- L4254 ` 	(for)`
- L4262 ` void(showCaseToAll)`
- L4266 ` 	(for)`
- L4271 ` 	(for)`
- L4276 ` 	(for)`
- L4282 ` void(joinAsSpect)`
- L4285 ` 	(if)`
- L4302 ` 	(for)`
- L4312 ` 	(for)`
- L4315 ` 	(if)`
- L4330 ` 	(for)`
- L4333 ` 	(if)`
- L4354 ` 	(if)`
- L4356 ` 	(for)`
- L4359 ` 	(if)`
- L4376 ` 	(for)`
- L4379 ` 	(if)`
- L4398 `static void(FightStateAddFlag)`
- L4401 ` 	(for)`
- L4403 ` 	(if)`
- L4405 ` 	(if)`
- L4408 ` 	(for)`
- L4413 ` 	(for)`
- L4421 ` 	(for)`
- L4426 ` 	(for)`
- L4434 ` 	(for)`
- L4439 ` 	(for)`
- L4447 ` 	(for)`
- L4452 ` 	(for)`
- L4461 `static int(getFightIDByFighter)`
- L4464 ` 	(for)`
- L4466 ` 	(for)`
- L4468 ` 	(if)`
- L4476 ` Map<Integer,Fighter>(getDeadList)`
- L4481 ` void(delOneDead)`

#### `objects/Gift.java` — 113 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Gift
Fonctions :
- L22 ` public(Gift)`
- L29 ` 	(for)`
- L32 ` 	(if)`
- L45 ` String(parsePacket)`
- L51 `  (for)`
- L61 `  (if)`
- L72 ` String(getTitleParsed)`
- L77 ` String(getDescriptionParsed)`
- L82 ` int(getId)`
- L87 ` Map<Integer, Integer>(getItems)`
- L92 ` String(getTitle)`
- L97 ` String(getDescription)`
- L102 ` String(getPicture)`
- L107 ` boolean(maximizeStat)`

#### `objects/Guild.java` — 528 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Guild
Fonctions :
- L49 ` public(GuildMember)`
- L65 ` int(getAlign)`
- L70 ` int(getGfx)`
- L75 ` int(getLvl)`
- L80 ` String(getName)`
- L85 ` int(getGuid)`
- L90 ` int(getRank)`
- L94 ` Guild(getGuild)`
- L99 ` String(parseRights)`
- L104 ` int(getRights)`
- L109 ` long(getXpGave)`
- L113 ` int(getPXpGive)`
- L118 ` String(getLastCo)`
- L123 ` int(getHoursFromLastCo)`
- L133 ` Personnage(getPerso)`
- L138 ` boolean(canDo)`
- L146 ` void(setRank)`
- L151 ` void(setAllRights)`
- L174 ` void(setLevel)`
- L180 ` void(giveXpToGuild)`
- L186 ` void(initRight)`
- L202 ` void(parseIntToRight)`
- L205 ` 	(if)`
- L218 ` 	(while)`
- L223 ` 	(if)`
- L232 ` void(setLastCo)`
- L238 ` public(Guild)`
- L249 ` public(Guild)`
- L277 ` GuildMember(addMember)`
- L284 ` GuildMember(addNewMember)`
- L290 ` int(get_id)`
- L295 ` int(get_nbrPerco)`
- L300 ` void(set_nbrPerco)`
- L304 ` int(get_Capital)`
- L309 ` void(set_Capital)`
- L313 ` Map<Integer,SortStats>(getSpells)`
- L317 ` Map<Integer, Integer>(getStats)`
- L320 ` void(addStat)`
- L326 ` void(boostSpell)`
- L332 ` Stats(getStatsFight)`
- L336 ` String(get_name)`
- L340 ` String(get_emblem)`
- L344 ` long(get_xp)`
- L348 ` int(get_lvl)`
- L352 ` int(getSize)`
- L356 ` String(parseMembersToGM)`
- L359 ` 	(for)`
- L378 ` ArrayList<Personnage>(getMembers)`
- L384 ` GuildMember(getMember)`
- L388 ` void(removeMember)`
- L391 ` 	(if)`
- L393 ` 	(if)`
- L401 ` void(addXp)`
- L409 ` void(levelUp)`
- L420 ` 	(for)`
- L443 ` String(compileSpell)`
- L450 ` 	(for)`
- L463 ` String(compileStats)`
- L469 ` 	(for)`
- L482 ` void(upgrade_Stats)`
- L488 ` int(get_Stats)`
- L492 ` 	(for)`
- L494 ` 	(if)`
- L501 ` String(parsePercotoGuild)`
- L515 ` String(parseQuestionTaxCollector)`

#### `objects/Hdv.java` — 51 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Hdv
Fonctions :
- L11 ` public(Hdv)`
- L21 ` int(get_mapID)`
- L26 ` String(get_Categories)`
- L30 ` String(get_SellTaxe)`
- L35 ` int(get_Taxe)`
- L39 ` int(get_LvlMax)`
- L43 ` int(get_AccountItem)`
- L47 ` int(get_SellTime)`

#### `objects/HdvEntry.java` — 59 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class HdvEntry
Fonctions :
- L11 ` public(HdvEntry)`
- L22 ` int(get_ObjetID)`
- L27 ` Objet(get_obj)`
- L31 ` int(get_HdvMapID)`
- L35 ` int(get_ownerGuid)`
- L39 ` int(get_price)`
- L43 ` int(get_qua)`
- L47 ` int(get_SellDate)`
- L51 ` String(parseToEmK)`
- L55 ` String(parseToEL)`

#### `objects/House.java` — 603 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class House
Fonctions :
- L32 ` public(House)`
- L51 ` int(get_id)`
- L56 ` short(get_map_id)`
- L61 ` int(get_cell_id)`
- L66 ` int(get_owner_id)`
- L71 ` void(set_owner_id)`
- L76 ` int(get_sale)`
- L81 ` void(set_sale)`
- L86 ` int(get_guild_id)`
- L91 ` void(set_guild_id)`
- L96 ` int(get_guild_rights)`
- L101 ` void(set_guild_rights)`
- L106 ` int(get_access)`
- L111 ` void(set_access)`
- L116 ` String(get_key)`
- L121 ` void(set_key)`
- L126 ` int(get_mapid)`
- L131 ` int(get_caseid)`
- L136 ` String(get_owner_pseudo)`
- L141 ` void(set_owner_pseudo)`
- L146 `static House(get_house_id_by_coord)`
- L149 ` 	(for)`
- L151 ` 	(if)`
- L161 ` 	(for)`
- L164 ` 	(if)`
- L168 ` 	(if)`
- L192 ` 	(if)`
- L214 ` 	(if)`
- L219 ` 	(if)`
- L224 ` else(if)`
- L237 ` 	(if)`
- L290 ` 	(if)`
- L302 ` 	(for)`
- L304 ` 	(if)`
- L316 ` 	(if)`
- L320 ` 	(if)`
- L326 ` 	(if)`
- L342 ` 	(for)`
- L351 ` 	(if)`
- L366 ` 	(if)`
- L375 ` 	(for)`
- L392 `static void(closeCode)`
- L397 `static void(closeBuy)`
- L402 ` void(Lock)`
- L407 `static void(LockHouse)`
- L411 ` 	(if)`
- L422 `static String(parseHouseToGuild)`
- L427 ` 	(for)`
- L429 ` 	(if)`
- L450 `static boolean(AlreadyHaveHouse)`
- L453 ` 	(for)`
- L455 ` 	(if)`
- L462 `static void(parseHG)`
- L468 ` 	(if)`
- L471 ` 	(if)`
- L480 ` else(if)`
- L492 ` else(if)`
- L494 ` 	(if)`
- L503 `static byte(HouseOnGuild)`
- L507 ` 	(for)`
- L509 ` 	(if)`
- L516 ` boolean(canDo)`
- L521 ` void(initRight)`
- L533 ` void(parseIntToRight)`
- L536 ` 	(if)`
- L549 ` 	(while)`
- L554 ` 	(if)`
- L563 `static void(Leave)`
- L578 ` 	(for)`
- L580 ` 	(if)`
- L587 `static void(removeHouseGuild)`
- L590 ` 	(for)`
- L592 ` 	(if)`

#### `objects/Metier.java` — 1625 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Metier
Fonctions :
- L34 ` public(StatsMetier)`
- L43 ` int(get_lvl)`
- L48 ` boolean(isCheap)`
- L52 ` void(setIsCheap)`
- L57 ` boolean(isFreeOnFails)`
- L62 ` void(setFreeOnFails)`
- L67 ` boolean(isNoRessource)`
- L72 ` void(setNoRessource)`
- L77 ` void(levelUp)`
- L82 ` 	(if)`
- L95 ` String(parseJS)`
- L101 ` 	(for)`
- L111 ` long(getXp)`
- L116 ` void(startAction)`
- L119 ` 	(for)`
- L121 ` 	(if)`
- L129 ` void(endAction)`
- L141 ` void(addXp)`
- L153 ` 	(if)`
- L166 ` String(getXpString)`
- L175 ` Metier(getTemplate)`
- L181 ` int(getOptBinValue)`
- L190 ` boolean(isValidMapAction)`
- L196 ` void(setOptBinValue)`
- L202 ` 	(if)`
- L208 ` 	(if)`
- L213 ` 	(if)`
- L219 ` int(getID)`
- L224 ` int(get_slotsPublic)`
- L229 ` void(set_slotsPublic)`
- L250 ` public(JobAction)`
- L263 ` void(actionPerformed)`
- L270 ` void(endAction)`
- L273 ` 	(if)`
- L297 ` 	(if)`
- L302 `  (if)`
- L313 ` void(startAction)`
- L317 ` 	(if)`
- L333 ` int(getSkillID)`
- L338 ` int(getMin)`
- L342 ` int(getXpWin)`
- L346 ` int(getMax)`
- L350 ` int(getChance)`
- L354 ` int(getTime)`
- L358 ` boolean(isCraft)`
- L362 ` void(modifIngredient)`
- L371 ` 	(if)`
- L377 ` void(craft)`
- L387 ` 	(if)`
- L403 ` 	(for)`
- L414 ` 	(if)`
- L421 ` 	(if)`
- L431 ` 	(if)`
- L456 ` 	(if)`
- L481 ` 	(for)`
- L495 ` 	(if)`
- L512 ` 	(if)`
- L525 ` void(doFmCraft)`
- L532 ` 	(for)`
- L536 ` 	(if)`
- L544 ` 	(switch)`
- L1009 ` 	(if)`
- L1045 ` 	(if)`
- L1053 ` 	(if)`
- L1058 ` 	(if)`
- L1062 ` 	(if)`
- L1066 ` 	(if)`
- L1070 ` 	(if)`
- L1125 ` 	(if)`
- L1127 ` 	(if)`
- L1132 ` 	(if)`
- L1134 ` 	(if)`
- L1139 ` 	(if)`
- L1141 ` 	(if)`
- L1146 ` 	(if)`
- L1148 ` 	(if)`
- L1153 ` 	(if)`
- L1155 ` 	(if)`
- L1160 ` 	(if)`
- L1162 ` 	(if)`
- L1170 ` 	(if)`
- L1172 ` 	(for)`
- L1182 ` 	(for)`
- L1186 ` 	(if)`
- L1209 ` 	(if)`
- L1218 ` 	(if)`
- L1220 ` 	(for)`
- L1249 ` 	(for)`
- L1283 ` 	(if)`
- L1288 ` 	(if)`
- L1293 ` 	(if)`
- L1298 ` 	(if)`
- L1303 ` 	(if)`
- L1308 ` 	(if)`
- L1351 ` 	(if)`
- L1355 ` 	(if)`
- L1367 ` 	(if)`
- L1371 ` 	(if)`
- L1389 ` void(repeat)`
- L1405 ` void(startCraft)`
- L1411 ` void(putLastCraftIngredients)`
- L1419 ` 	(for)`
- L1426 ` void(resetCraft)`
- L1437 ` public(Metier)`
- L1441 ` 	(if)`
- L1443 ` 	(for)`
- L1452 ` 	(if)`
- L1455 ` 	(for)`
- L1467 ` ArrayList<Integer>(getListBySkill)`
- L1472 ` boolean(canCraft)`
- L1478 ` int(getId)`
- L1483 ` boolean(isValidTool)`
- L1492 ` 	(if)`
- L1494 ` 	(for)`
- L1544 ` 	(for)`
- L1549 ` 	(if)`
- L1553 ` else(if)`
- L1557 ` else(if)`
- L1561 ` else(if)`
- L1565 ` else(if)`
- L1569 ` else(if)`
- L1585 `static int(getBaseMaxJet)`
- L1590 ` 	(for)`
- L1606 `static int(getActualJet)`
- L1609 ` 	(for)`

#### `objects/Monstre.java` — 695 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Monstre
Fonctions :
- L44 ` public(MobGroup)`
- L53 ` 	(switch)`
- L146 ` 	(for)`
- L183 ` public(MobGroup)`
- L193 ` 	(for)`
- L214 ` int(getID)`
- L219 ` int(getCellID)`
- L224 ` int(getOrientation)`
- L229 ` int(getAggroDistance)`
- L234 ` boolean(isFix)`
- L238 ` void(setOrientation)`
- L242 ` void(setCellID)`
- L247 ` int(getAlignement)`
- L252 ` MobGrade(getMobGradeByID)`
- L257 ` int(getSize)`
- L262 ` String(parseGM)`
- L273 ` 	(for)`
- L276 ` 	(if)`
- L294 ` int(getStarBonus)`
- L303 ` Map<Integer, MobGrade>(getMobs)`
- L307 ` void(setCondition)`
- L311 ` String(getCondition)`
- L315 ` void(setIsFix)`
- L319 ` void(startCondTimer)`
- L323 ` void(run)`
- L330 ` void(stopConditionTimer)`
- L359 ` public(MobGrade)`
- L406 ` 	(for)`
- L427 ` private(MobGrade)`
- L442 ` ArrayList<Drop>(getDrops)`
- L446 ` int(getBaseXp)`
- L450 ` int(getInit)`
- L453 ` MobGrade(getCopy)`
- L460 ` Stats(getStats)`
- L465 ` int(getLevel)`
- L470 ` ArrayList<SpellEffect>(getBuffs)`
- L475 ` Case(getFightCell)`
- L480 ` void(setFightCell)`
- L485 ` Map<Integer,SortStats>(getSpells)`
- L490 ` Monstre(getTemplate)`
- L495 ` int(getPDV)`
- L499 ` void(setPDV)`
- L503 ` int(getPDVMAX)`
- L507 ` int(getGrade)`
- L512 ` void(setInFightID)`
- L517 ` int(getInFightID)`
- L521 ` int(getPA)`
- L526 ` int(getPM)`
- L530 ` void(modifStatByInvocator)`
- L548 ` public(Monstre)`
- L623 ` int(getID)`
- L627 ` void(addDrop)`
- L631 ` ArrayList<Drop>(getDrops)`
- L635 ` int(getGfxID)`
- L638 ` int(getMinKamas)`
- L642 ` int(getMaxKamas)`
- L646 ` int(getAlign)`
- L650 ` String(getColors)`
- L654 ` int(getIAType)`
- L658 ` Map<Integer, MobGrade>(getGrades)`
- L662 ` MobGrade(getGradeByLevel)`
- L665 ` 	(for)`
- L672 ` MobGrade(getRandomGrade)`
- L677 ` 	(for)`
- L679 ` 	(if)`
- L689 ` boolean(isCapturable)`

#### `objects/NPC_tmpl.java` — 326 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class NPC_tmpl
Fonctions :
- L34 ` public(NPC_question)`
- L42 ` int(get_id)`
- L47 ` String(parseToDQPacket)`
- L59 ` String(getReponses)`
- L64 ` String(parseArguments)`
- L73 ` void(setReponses)`
- L86 ` public(NPC)`
- L94 ` NPC_tmpl(get_template)`
- L98 ` int(get_cellID)`
- L102 ` int(get_guid)`
- L106 ` int(get_orientation)`
- L110 ` String(parseGM)`
- L123 ` 	(if)`
- L140 ` void(setCellID)`
- L145 ` void(setOrientation)`
- L157 ` public(NPC_reponse)`
- L162 ` int(get_id)`
- L167 ` void(addAction)`
- L175 ` void(apply)`
- L181 ` boolean(isAnotherDialog)`
- L184 ` 	(for)`
- L193 ` public(NPC_tmpl)`
- L212 ` 	(for)`
- L223 ` int(get_id)`
- L227 ` int(get_bonusValue)`
- L231 ` int(get_gfxID)`
- L235 ` int(get_scaleX)`
- L239 ` int(get_scaleY)`
- L243 ` int(get_sex)`
- L247 ` int(get_color1)`
- L251 ` int(get_color2)`
- L255 ` int(get_color3)`
- L259 ` String(get_acces)`
- L263 ` int(get_extraClip)`
- L267 ` int(get_customArtWork)`
- L271 ` int(get_initQuestionID)`
- L275 ` String(getItemVendorList)`
- L280 ` 	(for)`
- L286 ` boolean(addItemVendor)`
- L293 ` boolean(delItemVendor)`
- L297 ` 	(for)`
- L299 ` 	(if)`
- L309 ` void(setInitQuestion)`
- L314 ` boolean(haveItem)`
- L317 ` 	(for)`

#### `objects/Objet.java` — 710 lignes
Rôle : Objet Ancestrar : équivalent GameObject/ObjectTemplate imbriqué.
Classe(s) : class Objet
Fonctions :
- L29 ` public(ObjTemplate)`
- L61 ` void(addAction)`
- L66 ` boolean(isTwoHanded)`
- L71 ` int(getBonusCC)`
- L76 ` int(getPOmin)`
- L81 ` int(getPOmax)`
- L86 ` int(getTauxCC)`
- L91 ` int(getTauxEC)`
- L96 ` int(getPACost)`
- L101 ` int(getID)`
- L106 ` String(getStrTemplate)`
- L111 ` String(getName)`
- L116 ` int(getType)`
- L121 ` int(getLevel)`
- L126 ` int(getPod)`
- L131 ` int(getPrix)`
- L136 ` int(getPanopID)`
- L141 ` String(getConditions)`
- L146 ` Objet(createNewItem)`
- L151 ` 	(if)`
- L165 ` Stats(generateNewStatsFromTemplate)`
- L173 ` 	(for)`
- L181 ` 	(if)`
- L194 ` 	(if)`
- L210 ` ArrayList<SpellEffect>(getEffectTemplate)`
- L217 ` 	(for)`
- L221 ` 	(for)`
- L223 ` 	(if)`
- L236 ` String(parseItemTemplateStats)`
- L241 ` void(applyAction)`
- L256 ` public(Objet)`
- L267 ` public(Objet)`
- L272 ` void(parseStringToStats)`
- L276 ` 	(for)`
- L282 ` 	(if)`
- L290 ` 	(if)`
- L305 ` 	(for)`
- L307 ` 	(if)`
- L323 ` void(addSoulStat)`
- L328 ` Map<Integer, Integer>(getSoulStat)`
- L333 ` Map<Integer, String>(getTxtStat)`
- L338 ` String(getTraquedName)`
- L341 ` 	(for)`
- L351 ` public(Objet)`
- L363 ` Personnage.Stats(getStats)`
- L368 ` int(getQuantity)`
- L373 ` void(setQuantity)`
- L378 ` int(getPosition)`
- L383 ` void(setPosition)`
- L388 ` ObjTemplate(getTemplate)`
- L393 ` int(getGuid)`
- L398 ` String(parseItem)`
- L406 ` String(parseStatsString)`
- L414 ` 	(for)`
- L431 ` 	(for)`
- L441 ` 	(for)`
- L446 ` 	(if)`
- L451 ` else(if)`
- L454 ` 	(if)`
- L473 ` 	(for)`
- L485 ` void(set_Template)`
- L497 ` 	(for)`
- L583 ` 	(for)`
- L661 ` ArrayList<SpellEffect>(getEffects)`
- L666 ` ArrayList<SpellEffect>(getCritEffects)`
- L670 ` 	(for)`
- L677 ` 	(if)`
- L693 `static Objet(getCloneObjet)`
- L699 ` void(clearStats)`

#### `objects/Others.java` — 77 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Others
Fonctions :
- L14 ` public(Bank)`
- L33 ` void(addBankKamas)`
- L38 ` void(setBankKamas)`
- L43 ` void(addBankItem)`
- L48 ` String(parseBankItems)`
- L53 ` 	(for)`
- L61 ` long(getBankKamas)`
- L66 ` int(getGuid)`
- L71 ` Map<Integer, Objet>(getBankItems)`

#### `objects/Percepteur.java` — 564 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Percepteur
Fonctions :
- L37 ` public(Percepteur)`
- L49 ` 	(for)`
- L61 ` long(getKamas)`
- L66 ` void(setKamas)`
- L71 ` long(getXp)`
- L76 ` void(setXp)`
- L81 ` Map<Integer, Objet>(getObjets)`
- L86 ` void(removeObjet)`
- L91 ` boolean(HaveObjet)`
- L94 ` 	(if)`
- L102 ` void(remove_timeTurn)`
- L107 ` void(set_timeTurn)`
- L112 ` int(get_turnTimer)`
- L117 `static String(parseGM)`
- L123 ` 	(for)`
- L126 ` 	(if)`
- L148 ` int(get_guildID)`
- L152 ` void(DelPerco)`
- L155 ` 	(for)`
- L162 ` int(get_inFight)`
- L167 ` void(set_inFight)`
- L172 ` int(getGuid)`
- L177 ` int(get_cellID)`
- L182 ` void(set_cellID)`
- L187 ` void(set_inFightID)`
- L192 ` int(get_inFightID)`
- L197 ` short(get_mapID)`
- L202 ` int(get_N1)`
- L207 ` int(get_N2)`
- L212 `static String(parsetoGuild)`
- L217 ` 	(for)`
- L219 ` 	(if)`
- L228 ` 	(if)`
- L261 `static int(GetPercoGuildID)`
- L263 ` 	(for)`
- L266 ` 	(if)`
- L273 ` int(GetPercoGuildID)`
- L278 `static Percepteur(GetPercoByMapID)`
- L280 ` 	(for)`
- L283 ` 	(if)`
- L290 `static int(CountPercoGuild)`
- L293 ` 	(for)`
- L295 ` 	(if)`
- L302 `static void(parseAttaque)`
- L305 ` 	(for)`
- L307 ` 	(if)`
- L313 `static void(parseDefense)`
- L316 ` 	(for)`
- L318 ` 	(if)`
- L324 `static String(parseAttaqueToGuild)`
- L341 `static String(parseDefenseToGuild)`
- L363 ` String(getItemPercepteurList)`
- L367 ` 	(if)`
- L369 ` 	(for)`
- L377 ` String(parseItemPercepteur)`
- L381 ` 	(for)`
- L387 ` void(removeFromPercepteur)`
- L399 ` 	(if)`
- L432 ` 	(if)`
- L463 ` void(LogXpDrop)`
- L468 ` void(LogObjetDrop)`
- L473 ` long(get_LogXp)`
- L478 ` String(get_LogItems)`
- L484 ` 	(for)`
- L492 ` void(addObjet)`
- L497 ` void(set_Exchange)`
- L502 ` boolean(get_Exchange)`
- L507 `static void(removePercepteur)`
- L510 ` 	(for)`
- L512 ` 	(if)`
- L526 ` boolean(addDefenseFight)`
- L538 ` void(delDefenseFight)`
- L544 ` void(clearDefenseFight)`
- L549 ` Map<Integer, Personnage>(getDefenseFight)`
- L554 ` ArrayList<Drop>(getDrops)`
- L558 ` 	(for)`

#### `objects/Personnage.java` — 3997 lignes
Rôle : Personnage Ancestrar : équivalent Player, XP, stats, inventaire, sorts, combat.
Classe(s) : class Personnage
Fonctions :
- L155 ` public(traque)`
- L161 ` void(set_traqued)`
- L166 ` Personnage(get_traqued)`
- L171 ` long(get_time)`
- L176 ` void(set_time)`
- L187 ` public(Group)`
- L194 ` boolean(isChief)`
- L199 ` void(addPerso)`
- L204 ` int(getPersosNumber)`
- L209 ` int(getGroupLevel)`
- L213 ` 	(for)`
- L219 ` ArrayList<Personnage>(getPersos)`
- L224 ` Personnage(getChief)`
- L229 ` void(leave)`
- L235 ` 	(if)`
- L249 ` public(Stats)`
- L261 ` public(Stats)`
- L272 ` public(Stats)`
- L277 ` public(Stats)`
- L282 ` int(addOneStat)`
- L294 ` boolean(isSameStats)`
- L297 ` 	(for)`
- L304 ` 	(for)`
- L313 ` int(getEffect)`
- L439 `static Stats(cumulStat)`
- L458 ` Map<Integer, Integer>(getMap)`
- L463 ` String(parseToItemSetStats)`
- L467 ` 	(for)`
- L475 ` public(Personnage)`
- L507 ` 	(if)`
- L517 ` 	(if)`
- L526 ` else(if)`
- L529 ` 	(if)`
- L535 ` 	(for)`
- L542 ` 	(if)`
- L552 ` 	(if)`
- L559 ` 	(for)`
- L574 ` 	(if)`
- L576 ` 	(for)`
- L599 ` void(actionPerformed)`
- L609 ` 	(if)`
- L611 ` 	(for)`
- L628 `static Personnage(CREATE_PERSONNAGE)`
- L632 ` 	(if)`
- L634 ` 	(for)`
- L695 ` public(Personnage)`
- L709 ` 	(if)`
- L715 ` 	(for)`
- L731 ` 	(if)`
- L740 ` Personnage(ClonePerso)`
- L762 ` 	(if)`
- L768 ` 	(if)`
- L794 ` 	(if)`
- L801 ` String(parseALK)`
- L819 ` void(remove)`
- L824 ` void(OnJoinGame)`
- L845 ` 	(if)`
- L856 ` 	(if)`
- L915 ` 	(for)`
- L917 ` 	(if)`
- L921 ` 	(if)`
- L930 ` 	(if)`
- L956 ` void(regenLife)`
- L967 ` void(sendGameCreate)`
- L988 ` String(parseToOa)`
- L995 ` String(parseToGM)`
- L1011 ` 	(if)`
- L1023 ` 	(if)`
- L1032 ` 	(if)`
- L1043 ` String(getGMStuffString)`
- L1063 ` String(getAsPacket)`
- L1076 ` 	(if)`
- L1080 ` 	(if)`
- L1137 ` void(warpToSavePos)`
- L1146 ` void(removeByTemplateID)`
- L1157 ` 	(for)`
- L1161 ` 	(if)`
- L1165 ` 	(if)`
- L1183 ` 	(if)`
- L1186 ` 	(if)`
- L1193 ` 	(for)`
- L1212 ` void(set_Online)`
- L1217 ` boolean(isOnline)`
- L1222 ` void(setGroup)`
- L1227 ` Group(getGroup)`
- L1232 ` String(get_savePos)`
- L1237 ` void(set_savePos)`
- L1242 ` int(get_isTradingWith)`
- L1247 ` void(set_isTradingWith)`
- L1252 ` int(get_isTalkingWith)`
- L1257 ` void(set_isTalkingWith)`
- L1262 ` long(get_kamas)`
- L1267 ` Map<Integer, SpellEffect>(get_buff)`
- L1272 ` void(set_kamas)`
- L1277 ` Compte(get_compte)`
- L1282 ` int(get_spellPts)`
- L1287 ` void(set_spellPts)`
- L1292 ` Guild(get_guild)`
- L1298 ` void(setGuildMember)`
- L1303 ` boolean(is_ready)`
- L1308 ` void(set_ready)`
- L1313 ` int(get_duelID)`
- L1318 ` Fight(get_fight)`
- L1323 ` void(set_duelID)`
- L1328 ` int(get_energy)`
- L1333 ` boolean(is_showSeller)`
- L1338 ` void(set_showSeller)`
- L1343 ` String(get_canaux)`
- L1348 ` void(set_energy)`
- L1353 ` int(get_lvl)`
- L1358 ` void(set_lvl)`
- L1363 ` long(get_curExp)`
- L1368 ` Carte.Case(get_curCell)`
- L1373 ` void(set_curCell)`
- L1378 ` void(set_curExp)`
- L1383 ` int(get_size)`
- L1388 ` void(set_size)`
- L1393 ` void(set_fight)`
- L1398 ` int(get_gfxID)`
- L1403 ` void(set_gfxID)`
- L1408 ` int(get_GUID)`
- L1413 ` Carte(get_curCarte)`
- L1418 ` String(get_name)`
- L1423 ` boolean(is_away)`
- L1428 ` void(set_away)`
- L1433 ` boolean(isSitted)`
- L1438 ` int(get_sexe)`
- L1443 ` int(get_classe)`
- L1448 ` int(get_color1)`
- L1453 ` int(get_color2)`
- L1458 ` Stats(get_baseStats)`
- L1463 ` int(get_color3)`
- L1468 ` int(get_capital)`
- L1473 ` void(fullPDV)`
- L1478 ` int(get_orientation)`
- L1483 ` void(set_orientation)`
- L1488 ` String(xpString)`
- L1493 ` int(emoteActive)`
- L1498 ` void(setEmoteActive)`
- L1503 ` int(get_PDV)`
- L1508 ` void(set_PDV)`
- L1512 ` 	(if)`
- L1517 ` int(get_PDVMAX)`
- L1522 ` void(set_PDVMAX)`
- L1526 ` 	(if)`
- L1531 ` void(setSitted)`
- L1539 ` 	(if)`
- L1550 ` int(get_pdvper)`
- L1557 ` void(emoticone)`
- L1570 ` boolean(isMuted)`
- L1575 ` void(set_curCarte)`
- L1580 ` void(addKamas)`
- L1585 ` void(setCurExchange)`
- L1590 ` Exchange(get_curExchange)`
- L1595 ` void(addChanel)`
- L1602 ` void(removeChanel)`
- L1608 ` GuildMember(getGuildMember)`
- L1613 ` int(getAccID)`
- L1618 ` traque(get_traque)`
- L1623 ` void(set_traque)`
- L1628 ` boolean(get_isClone)`
- L1633 ` void(set_isClone)`
- L1638 ` int(get_isOnPercepteurID)`
- L1643 ` void(set_isOnPercepteurID)`
- L1648 ` void(set_title)`
- L1653 ` byte(get_title)`
- L1658 ` long(getLastPacketTime)`
- L1663 ` void(refreshLastPacketTime)`
- L1668 ` void(setInTrunk)`
- L1673 ` Trunk(getInTrunk)`
- L1678 ` void(setInHouse)`
- L1683 ` House(getInHouse)`
- L1688 ` void(set_Restriction)`
- L1693 ` String(get_Restriction)`
- L1698 ` void(addCapital)`
- L1703 ` void(addSpellPoint)`
- L1708 ` void(startActionOnCell)`
- L1723 ` void(finishActionOnCell)`
- L1734 ` void(teleport)`
- L1738 ` 	(if)`
- L1742 ` 	(if)`
- L1747 ` 	(if)`
- L1752 ` 	(if)`
- L1763 ` 	(if)`
- L1780 ` 	(if)`
- L1789 ` 	(for)`
- L1803 ` String(getStringVar)`
- L1808 ` 	(if)`
- L1814 ` boolean(isDispo)`
- L1819 ` 	(if)`
- L1827 ` void(VerifAndChangeItemPlace)`
- L1846 ` 	(for)`
- L1849 ` 	(if)`
- L1851 ` 	(if)`
- L1860 ` else(if)`
- L1862 ` 	(if)`
- L1871 ` else(if)`
- L1873 ` 	(if)`
- L1882 ` else(if)`
- L1884 ` 	(if)`
- L1893 ` else(if)`
- L1895 ` 	(if)`
- L1904 ` else(if)`
- L1906 ` 	(if)`
- L1915 ` else(if)`
- L1917 ` 	(if)`
- L1926 ` else(if)`
- L1928 ` 	(if)`
- L1937 ` else(if)`
- L1939 ` 	(if)`
- L1948 ` else(if)`
- L1950 ` 	(if)`
- L1959 ` else(if)`
- L1961 ` 	(if)`
- L1970 ` else(if)`
- L1972 ` 	(if)`
- L1981 ` else(if)`
- L1983 ` 	(if)`
- L1992 ` else(if)`
- L1994 ` 	(if)`
- L2003 ` else(if)`
- L2005 ` 	(if)`
- L2014 ` else(if)`
- L2016 ` 	(if)`
- L2028 ` void(changeOrientation)`
- L2031 ` 	(if)`
- L2041 ` boolean(is_showSpells)`
- L2045 ` String(parseSpellToDB)`
- L2050 ` 	(for)`
- L2063 ` void(parseSpells)`
- L2067 ` 	(for)`
- L2079 ` void(setisForgetingSpell)`
- L2084 ` boolean(isForgetingSpell)`
- L2089 ` boolean(learnSpell)`
- L2092 ` 	(if)`
- L2098 ` 	(if)`
- L2107 ` boolean(boostSpell)`
- L2110 ` 	(if)`
- L2117 ` 	(if)`
- L2119 ` 	(if)`
- L2139 ` boolean(forgetSpell)`
- L2142 ` 	(if)`
- L2149 ` 	(if)`
- L2163 ` String(parseSpellList)`
- L2175 ` void(set_SpellPlace)`
- L2183 ` void(replace_SpellInBook)`
- L2186 ` 	(for)`
- L2188 ` 	(if)`
- L2190 ` 	(if)`
- L2197 ` SortStats(getSortStatBySortIfHas)`
- L2202 ` boolean(hasSpell)`
- L2209 ` String(getStoreItemsIDSplitByChar)`
- L2213 ` 	(for)`
- L2220 ` String(parseToMerchant)`
- L2241 ` Map<Integer, Integer>(getStoreItems)`
- L2246 ` String(parseStoreItemsList)`
- L2251 `  (for)`
- L2259 ` String(parseStoreItemstoBD)`
- L2263 ` 	(for)`
- L2269 ` void(addinStore)`
- L2274 ` 	(if)`
- L2281 ` 	(if)`
- L2294 ` 	(if)`
- L2320 ` 	(if)`
- L2349 ` Objet(getSimilarStoreItem)`
- L2352 ` 	(for)`
- L2362 ` void(removeFromStore)`
- L2367 ` 	(if)`
- L2380 ` 	(if)`
- L2396 ` 	(if)`
- L2413 ` void(removeStoreItem)`
- L2418 ` void(addStoreItem)`
- L2423 ` int(storeBuy)`
- L2427 ` 	(for)`
- L2438 ` Stats(getStuffStats)`
- L2442 ` 	(for)`
- L2445 ` 	(if)`
- L2450 ` 	(if)`
- L2455 ` 	(if)`
- L2463 ` 	(if)`
- L2471 ` void(levelUp)`
- L2483 ` 	(if)`
- L2488 ` 	(if)`
- L2495 ` void(addXp)`
- L2502 ` 	(if)`
- L2508 ` int(getInitiative)`
- L2531 ` Stats(getTotalStats)`
- L2543 ` Stats(getDonsStats)`
- L2550 ` int(getPodUsed)`
- L2554 ` 	(for)`
- L2560 ` int(getMaxPod)`
- L2564 ` 	(for)`
- L2571 ` void(refreshMapAfterFight)`
- L2575 ` 	(if)`
- L2585 ` void(boostStat)`
- L2589 ` 	(switch)`
- L2605 ` 	(if)`
- L2607 ` 	(switch)`
- L2638 ` void(refreshStats)`
- L2645 ` Stats(getBuffsStats)`
- L2649 ` 	(for)`
- L2653 ` 	(if)`
- L2656 ` 	(if)`
- L2660 ` 	(for)`
- L2670 ` int(get_isDead)`
- L2684 ` void(set_FuneralStone)`
- L2698 ` void(set_Ghosts)`
- L2713 ` void(set_Alive)`
- L2731 ` void(teleportToCemetery)`
- L2741 ` String(parseObjetsToDB)`
- L2745 ` 	(for)`
- L2752 ` boolean(hasEquiped)`
- L2760 ` boolean(addObjet)`
- L2763 ` 	(for)`
- L2783 ` void(addObjet)`
- L2788 ` Map<Integer,Objet>(getItems)`
- L2793 ` String(parseItemToASK)`
- L2798 ` 	(for)`
- L2804 ` String(getItemsIDSplitByChar)`
- L2809 ` 	(for)`
- L2816 ` boolean(hasItemGuid)`
- L2821 ` void(removeItem)`
- L2826 ` void(removeItem)`
- L2833 ` 	(if)`
- L2837 ` 	(if)`
- L2854 ` void(deleteItem)`
- L2860 ` Objet(getObjetByPos)`
- L2864 ` 	(for)`
- L2873 ` Objet(getSimilarItem)`
- L2876 ` 	(for)`
- L2889 ` boolean(hasItemTemplate)`
- L2892 ` 	(for)`
- L2902 ` void(sellItem)`
- L2930 ` void(setInBank)`
- L2934 ` boolean(isInBank)`
- L2939 ` String(parseBankPacket)`
- L2943 ` 	(for)`
- L2951 ` void(addInBank)`
- L2956 ` 	(if)`
- L2969 ` 	(if)`
- L2998 ` 	(if)`
- L3026 ` Objet(getSimilarBankItem)`
- L3029 ` 	(for)`
- L3038 ` void(removeFromBank)`
- L3043 ` 	(if)`
- L3056 ` 	(if)`
- L3089 ` 	(if)`
- L3120 ` void(openMountPark)`
- L3122 ` 	(if)`
- L3148 ` void(leftMountPark)`
- L3154 ` MountPark(getInMountPark)`
- L3161 ` Map<Integer,StatsMetier>(getMetiers)`
- L3165 ` int(learnJob)`
- L3168 ` 	(for)`
- L3191 ` 	(if)`
- L3212 ` void(unlearnJob)`
- L3217 ` void(doJobAction)`
- L3224 ` void(finishJobAction)`
- L3231 ` String(parseJobData)`
- L3236 ` 	(for)`
- L3243 ` int(totalJobBasic)`
- L3247 ` 	(for)`
- L3251 ` 	(if)`
- L3268 ` int(totalJobFM)`
- L3272 ` 	(for)`
- L3276 ` 	(if)`
- L3288 ` void(setCurJobAction)`
- L3293 ` JobAction(getCurJobAction)`
- L3298 ` StatsMetier(getMetierBySkill)`
- L3305 ` StatsMetier(getMetierByID)`
- L3311 ` String(get_isJobActivate)`
- L3316 ` void(set_isJobActivate)`
- L3321 ` boolean(is_onCraftBook)`
- L3326 ` void(set_onCraftBook)`
- L3331 ` int(get_isCraftingWith)`
- L3336 ` void(set_isCraftingWith)`
- L3341 ` int(get_isCraftingWithskID)`
- L3346 ` void(set_isCraftingWithskID)`
- L3351 ` boolean(is_onCraftBookCrafter)`
- L3356 ` void(set_onCraftBookCrafter)`
- L3363 ` void(SetSeeFriendOnline)`
- L3367 ` boolean(is_showFriendConnection)`
- L3372 ` String(parseToFriendList)`
- L3394 ` String(parseToEnemyList)`
- L3418 ` boolean(isOnMount)`
- L3422 ` void(toogleOnMount)`
- L3425 ` 	(if)`
- L3433 ` 	(if)`
- L3439 ` 	(if)`
- L3450 ` int(getMountXpGive)`
- L3455 ` Dragodinde(getMount)`
- L3460 ` void(setMount)`
- L3465 ` void(setMountGiveXp)`
- L3472 ` byte(get_align)`
- L3476 ` boolean(canAggro)`
- L3481 ` void(set_canAggro)`
- L3486 ` boolean(is_showWings)`
- L3491 ` int(getGrade)`
- L3502 ` void(modifAlignement)`
- L3516 ` void(setDeshonor)`
- L3521 ` int(getDeshonor)`
- L3526 ` void(setShowWings)`
- L3531 ` int(get_honor)`
- L3536 ` void(set_honor)`
- L3541 ` void(setALvl)`
- L3546 ` int(getALvl)`
- L3551 ` void(toggleWings)`
- L3556 ` 	(switch)`
- L3574 ` void(addHonor)`
- L3582 `  (if)`
- L3587 ` void(remHonor)`
- L3595 ` 	(if)`
- L3613 ` 	(for)`
- L3623 ` boolean(hasZaap)`
- L3629 ` void(openZaapMenu)`
- L3634 ` 	(if)`
- L3649 ` void(useZaap)`
- L3660 ` 	(if)`
- L3666 ` 	(if)`
- L3672 ` 	(if)`
- L3678 ` 	(if)`
- L3689 ` String(parseZaaps)`
- L3696 ` 	(for)`
- L3704 ` void(stopZaaping)`
- L3711 ` void(Zaapi_close)`
- L3718 ` void(Zaapi_use)`
- L3724 ` 	(if)`
- L3726 ` 	(for)`
- L3729 ` 	(if)`
- L3731 ` 	(if)`
- L3738 ` 	(if)`
- L3749 ` void(SetZaaping)`
- L3757 ` void(MarryTo)`
- L3762 ` String(get_wife_friendlist)`
- L3767 ` 	(if)`
- L3770 ` 	(if)`
- L3780 ` String(parse_towife)`
- L3784 ` 	(if)`
- L3799 ` 	(if)`
- L3810 ` 	(if)`
- L3812 ` 	(if)`
- L3824 ` void(Divorce)`
- L3833 ` int(getWife)`
- L3838 ` int(setisOK)`
- L3843 ` int(getisOK)`
- L3849 ` void(setInvitation)`
- L3853 ` int(getInvitation)`
- L3858 ` String(parseToPM)`
- L3876 ` int(getNumbEquipedItemOfPanoplie)`
- L3880 ` 	(for)`
- L3889 ` Timer(DialogTimer)`
- L3895 ` void(actionPerformed)`
- L3898 `  (if)`
- L3909 ` 	(if)`
- L3915 ` 	(if)`
- L3946 ` boolean(is_hasEndFight)`
- L3951 ` void(set_hasEndFight)`
- L3956 ` int(get_deshonor)`
- L3961 ` void(resetVars)`

#### `objects/Pets.java` — 285 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Pets
Fonctions :
- L23 ` public(Pets)`
- L36 ` int(get_Tid)`
- L41 ` int(get_Type)`
- L46 ` String(get_Gap)`
- L51 ` String(get_StatsUp)`
- L56 ` int(get_Max)`
- L61 ` int(get_Gain)`
- L66 ` int(get_DeadTemplate)`
- L71 ` Map<Integer, ArrayList<Map<Integer, Integer>>>(get_Monsters)`
- L76 ` int(get_NumbMonster)`
- L79 ` 	(for)`
- L81 ` 	(if)`
- L83 ` 	(for)`
- L85 ` 	(for)`
- L87 ` 	(if)`
- L97 ` void(DecompileStatsUpItem)`
- L100 ` 	(if)`
- L109 ` 	(for)`
- L124 ` 	(for)`
- L142 ` 	(for)`
- L146 ` 	(for)`
- L148 ` 	(if)`
- L168 ` 	(for)`
- L172 ` 	(for)`
- L174 ` 	(if)`
- L190 ` boolean(canEat)`
- L193 ` 	(if)`
- L195 ` 	(for)`
- L197 ` 	(for)`
- L199 ` 	(for)`
- L201 ` 	(if)`
- L210 ` 	(if)`
- L212 ` 	(for)`
- L214 ` 	(if)`
- L221 ` 	(if)`
- L223 ` 	(for)`
- L225 ` 	(if)`
- L236 ` int(statsIDbyEat)`
- L239 ` 	(if)`
- L241 ` 	(for)`
- L243 ` 	(for)`
- L245 ` 	(for)`
- L247 ` 	(if)`
- L256 ` 	(if)`
- L258 ` 	(for)`
- L264 ` 	(if)`
- L266 ` 	(for)`
- L276 ` Map<Integer, String>(generateNewtxtStatsForPets)`

#### `objects/PetsEntry.java` — 427 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class PetsEntry
Fonctions :
- L23 ` public(PetsEntry)`
- L34 ` int(get_ObjectID)`
- L39 ` long(get_LastEatDate)`
- L44 ` String(parse_LastEatDate)`
- L65 ` int(get_quaEat)`
- L70 ` int(get_PDV)`
- L75 ` int(get_Corpulence)`
- L80 ` boolean(get_isEupeoh)`
- L107 ` 	(for)`
- L125 ` void(LooseFight)`
- L136 ` 	(if)`
- L151 ` 	(if)`
- L161 ` void(Eat)`
- L201 ` 	(if)`
- L206 ` 	(if)`
- L219 ` 	(if)`
- L233 ` 	(if)`
- L244 ` void(EatSouls)`
- L252 ` 	(for)`
- L256 ` 	(if)`
- L275 ` 	(for)`
- L277 ` 	(for)`
- L279 ` 	(for)`
- L284 ` 	(for)`
- L290 ` 	(if)`
- L301 ` void(update_pets)`
- L316 ` 	(if)`
- L331 ` 	(if)`
- L346 ` 	(if)`
- L357 ` void(resurrection)`
- L364 ` 	(for)`
- L367 ` 	(if)`
- L386 ` void(RestoreLife)`
- L393 ` 	(if)`
- L399 ` 	(if)`
- L413 ` void(Give_EPO)`

#### `objects/PierreAme.java` — 91 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class PierreAme extends Objet
Fonctions :
- L10 ` public(PierreAme)`
- L25 ` 	(for)`
- L37 ` String(parseStatsString)`
- L42 ` 	(for)`
- L65 ` 	(for)`
- L76 ` String(parseToSave)`
- L81 ` 	(for)`

#### `objects/Sort.java` — 342 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Sort
Fonctions :
- L45 ` public(SortStats)`
- L70 ` ArrayList<SpellEffect>(parseEffect)`
- L75 ` 	(for)`
- L87 ` int(getSpellID)`
- L92 ` Sort(getSpell)`
- L97 ` int(getSpriteID)`
- L101 ` String(getSpriteInfos)`
- L106 ` int(getLevel)`
- L110 ` int(getPACost)`
- L114 ` int(getMinPO)`
- L118 ` int(getMaxPO)`
- L122 ` int(getTauxCC)`
- L126 ` int(getTauxEC)`
- L130 ` boolean(isLineLaunch)`
- L134 ` boolean(hasLDV)`
- L138 ` boolean(isEmptyCell)`
- L142 ` boolean(isModifPO)`
- L146 ` int(getMaxLaunchbyTurn)`
- L150 ` int(getMaxLaunchbyByTarget)`
- L154 ` int(getCoolDown)`
- L158 ` int(getReqLevel)`
- L162 ` boolean(isEcEndTurn)`
- L166 ` ArrayList<SpellEffect>(getEffects)`
- L170 ` ArrayList<SpellEffect>(getCCeffects)`
- L174 ` String(getPorteeType)`
- L178 ` void(applySpellEffectToFight)`
- L192 ` 	(for)`
- L208 ` void(applySpellEffectToFight)`
- L221 ` 	(for)`
- L235 ` 	(if)`
- L247 ` 	(for)`
- L278 ` public(Sort)`
- L287 ` 	(for)`
- L298 ` 	(for)`
- L310 ` ArrayList<Integer>(getEffectTargets)`
- L316 ` int(getSpriteID)`
- L321 ` String(getSpriteInfos)`
- L325 ` int(getSpellID)`
- L329 ` SortStats(getStatsByLevel)`
- L334 ` void(addSortStats)`

#### `objects/SpellEffect.java` — 3912 lignes
Rôle : Effets de sorts : application des dommages, buffs, soins, invocations, états, pièges/glyphes.
Classe(s) : class SpellEffect
Fonctions :
- L37 ` public(SpellEffect)`
- L54 ` public(SpellEffect)`
- L70 ` boolean(getSpell2)`
- L73 ` 	(if)`
- L82 ` int(getDuration)`
- L87 ` int(getTurn)`
- L92 ` boolean(isDebuffabe)`
- L97 ` void(setTurn)`
- L102 ` int(getEffectID)`
- L107 ` String(getJet)`
- L112 ` int(getValue)`
- L117 ` int(getChance)`
- L123 ` String(getArgs)`
- L128 `static ArrayList<Fighter>(getTargets)`
- L132 ` 	(for)`
- L141 ` void(setValue)`
- L146 ` int(decrementDuration)`
- L152 ` void(applyBeginingBuff)`
- L160 ` void(applyToFight)`
- L166 `static int(applyOnHitBuffs)`
- L169 ` 	(for)`
- L171 ` 	(for)`
- L173 ` 	(switch)`
- L207 ` 	(for)`
- L242 ` 	(if)`
- L269 ` 	(if)`
- L300 ` Fighter(getCaster)`
- L305 ` int(getSpell)`
- L310 ` void(applyToFight)`
- L323 ` 	(for)`
- L329 ` 	(switch)`
- L693 ` void(applyEffect_202)`
- L697 ` 	(if)`
- L700 ` 	(for)`
- L705 ` 	(for)`
- L712 ` void(applyEffect_782)`
- L719 ` void(applyEffect_165)`
- L730 ` void(applyEffect_51)`
- L754 ` void(applyEffect_950)`
- L763 ` 	(for)`
- L770 ` 	(if)`
- L782 ` void(applyEffect_951)`
- L791 ` 	(for)`
- L802 ` void(applyEffect_50)`
- L827 ` void(applyEffect_788)`
- L831 ` 	(for)`
- L836 ` void(applyEffect_131)`
- L839 ` 	(for)`
- L844 ` void(applyEffect_185)`
- L876 ` void(applyEffect_293)`
- L881 ` void(applyEffect_672)`
- L894 ` 	(for)`
- L898 ` 	(if)`
- L906 ` 	(if)`
- L919 ` 	(if)`
- L927 ` void(applyEffect_783)`
- L946 ` 	(while)`
- L963 ` 	(for)`
- L971 ` void(applyEffect_9)`
- L974 ` 	(for)`
- L979 ` void(applyEffect_8)`
- L986 ` 	(switch)`
- L1016 ` 	(for)`
- L1028 ` void(applyEffect_266)`
- L1033 ` 	(for)`
- L1044 ` void(applyEffect_267)`
- L1049 ` 	(for)`
- L1060 ` void(applyEffect_268)`
- L1065 ` 	(for)`
- L1076 ` void(applyEffect_269)`
- L1081 ` 	(for)`
- L1092 ` void(applyEffect_270)`
- L1097 ` 	(for)`
- L1108 ` void(applyEffect_271)`
- L1113 ` 	(for)`
- L1124 ` void(applyEffect_210)`
- L1128 ` 	(if)`
- L1133 ` 	(for)`
- L1138 ` void(applyEffect_211)`
- L1141 ` 	(if)`
- L1146 ` 	(for)`
- L1151 ` void(applyEffect_212)`
- L1154 ` 	(if)`
- L1159 ` 	(for)`
- L1164 ` void(applyEffect_213)`
- L1167 ` 	(if)`
- L1172 ` 	(for)`
- L1177 ` void(applyEffect_214)`
- L1180 ` 	(if)`
- L1185 ` 	(for)`
- L1190 ` void(applyEffect_215)`
- L1193 ` 	(if)`
- L1198 ` 	(for)`
- L1203 ` void(applyEffect_216)`
- L1206 ` 	(if)`
- L1211 ` 	(for)`
- L1216 ` void(applyEffect_217)`
- L1219 ` 	(if)`
- L1224 ` 	(for)`
- L1229 ` void(applyEffect_218)`
- L1232 ` 	(if)`
- L1237 ` 	(for)`
- L1242 ` void(applyEffect_219)`
- L1245 ` 	(if)`
- L1250 ` 	(for)`
- L1255 ` void(applyEffect_106)`
- L1263 ` 	(for)`
- L1268 ` void(applyEffect_105)`
- L1272 ` 	(if)`
- L1277 ` 	(for)`
- L1282 ` void(applyEffect_265)`
- L1286 ` 	(if)`
- L1291 ` 	(for)`
- L1297 ` void(applyEffect_155)`
- L1301 ` 	(if)`
- L1306 ` 	(for)`
- L1312 ` void(applyEffect_163)`
- L1315 ` 	(if)`
- L1320 ` 	(for)`
- L1326 ` void(applyEffect_162)`
- L1329 ` 	(if)`
- L1334 ` 	(for)`
- L1340 ` void(applyEffect_161)`
- L1343 ` 	(if)`
- L1348 ` 	(for)`
- L1354 ` void(applyEffect_160)`
- L1357 ` 	(if)`
- L1362 ` 	(for)`
- L1368 ` void(applyEffect_149)`
- L1376 ` 	(for)`
- L1378 ` 	(if)`
- L1380 `  (if)`
- L1391 ` void(applyEffect_182)`
- L1395 ` 	(if)`
- L1400 ` 	(for)`
- L1406 ` void(applyEffect_184)`
- L1410 ` 	(if)`
- L1415 ` 	(for)`
- L1421 ` void(applyEffect_183)`
- L1425 ` 	(if)`
- L1430 ` 	(for)`
- L1436 ` void(applyEffect_145)`
- L1440 ` 	(if)`
- L1445 ` 	(for)`
- L1451 ` void(applyEffect_171)`
- L1455 ` 	(if)`
- L1460 ` 	(for)`
- L1466 ` void(applyEffect_142)`
- L1470 ` 	(if)`
- L1475 ` 	(for)`
- L1481 ` void(applyEffect_150)`
- L1485 ` 	(for)`
- L1491 ` void(applyEffect_402)`
- L1511 ` void(applyEffect_401)`
- L1532 ` void(applyEffect_400)`
- L1560 ` 	(if)`
- L1565 ` 	(for)`
- L1575 ` 	(if)`
- L1580 ` 	(for)`
- L1592 ` 	(if)`
- L1597 ` 	(for)`
- L1607 ` 	(if)`
- L1612 ` 	(for)`
- L1622 ` 	(if)`
- L1634 ` 	(if)`
- L1639 ` 	(for)`
- L1664 ` 	(for)`
- L1710 ` 	(for)`
- L1717 ` void(applyEffect_110)`
- L1721 ` 	(if)`
- L1726 ` 	(for)`
- L1732 ` void(applyEffect_111)`
- L1736 ` 	(if)`
- L1741 ` 	(for)`
- L1743 ` 	(if)`
- L1753 ` void(applyEffect_112)`
- L1757 ` 	(if)`
- L1762 ` 	(for)`
- L1768 ` void(applyEffect_121)`
- L1772 ` 	(if)`
- L1777 ` 	(for)`
- L1783 ` void(applyEffect_122)`
- L1787 ` 	(if)`
- L1792 ` 	(for)`
- L1798 ` void(applyEffect_123)`
- L1802 ` 	(if)`
- L1807 ` 	(for)`
- L1813 ` void(applyEffect_124)`
- L1817 ` 	(if)`
- L1822 ` 	(for)`
- L1828 ` void(applyEffect_125)`
- L1832 ` 	(if)`
- L1837 ` 	(for)`
- L1843 ` void(applyEffect_126)`
- L1847 ` 	(if)`
- L1852 ` 	(for)`
- L1858 ` void(applyEffect_128)`
- L1862 ` 	(if)`
- L1867 ` 	(for)`
- L1875 ` void(applyEffect_138)`
- L1879 ` 	(if)`
- L1884 ` 	(for)`
- L1890 ` void(applyEffect_114)`
- L1894 ` 	(if)`
- L1899 ` 	(for)`
- L1905 ` void(applyEffect_115)`
- L1909 ` 	(if)`
- L1914 ` 	(for)`
- L1920 ` void(applyEffect_77)`
- L1929 ` 	(for)`
- L1932 ` 	(if)`
- L1941 ` 	(if)`
- L1949 ` void(applyEffect_84)`
- L1958 ` 	(for)`
- L1965 ` 	(if)`
- L1975 ` 	(if)`
- L1983 ` void(applyEffect_168)`
- L1986 ` 	(if)`
- L1988 ` 	(for)`
- L1996 ` 	(for)`
- L1998 ` 	(if)`
- L2021 ` void(applyEffect_169)`
- L2023 ` 	(if)`
- L2025 ` 	(for)`
- L2033 ` 	(if)`
- L2039 ` 	(for)`
- L2045 ` else(if)`
- L2068 ` void(applyEffect_101)`
- L2072 ` 	(if)`
- L2074 ` 	(for)`
- L2079 ` 	(if)`
- L2088 ` 	(for)`
- L2093 ` 	(if)`
- L2095 ` 	(if)`
- L2108 ` void(applyEffect_127)`
- L2111 ` 	(if)`
- L2113 ` 	(for)`
- L2118 ` 	(if)`
- L2127 ` 	(for)`
- L2132 ` 	(if)`
- L2147 ` void(applyEffect_107)`
- L2153 ` 	(for)`
- L2159 ` void(applyEffect_79)`
- L2166 ` 	(for)`
- L2172 ` void(applyEffect_4)`
- L2186 ` 	(for)`
- L2200 ` void(applyEffect_765B)`
- L2220 ` 	(if)`
- L2238 ` void(applyEffect_82)`
- L2241 ` 	(if)`
- L2243 ` 	(for)`
- L2247 ` 	(if)`
- L2256 ` 	(if)`
- L2275 ` 	(if)`
- L2285 ` 	(for)`
- L2291 ` void(applyEffect_6)`
- L2294 ` 	(if)`
- L2296 ` 	(for)`
- L2301 ` 	(if)`
- L2327 ` 	(for)`
- L2336 ` void(applyEffect_5)`
- L2339 ` 	(if)`
- L2341 ` 	(if)`
- L2346 ` 	(if)`
- L2348 ` 	(for)`
- L2353 ` 	(if)`
- L2371 ` 	(if)`
- L2377 ` 	(if)`
- L2382 ` 	(if)`
- L2386 ` 	(if)`
- L2409 ` 	(for)`
- L2418 ` void(applyEffect_91)`
- L2421 ` 	(if)`
- L2423 ` 	(for)`
- L2427 ` 	(if)`
- L2448 ` 	(if)`
- L2458 ` 	(for)`
- L2461 ` 	(if)`
- L2469 ` 	(if)`
- L2489 ` 	(if)`
- L2499 ` 	(for)`
- L2505 ` void(applyEffect_92)`
- L2509 ` 	(if)`
- L2511 ` 	(for)`
- L2515 ` 	(if)`
- L2536 ` 	(if)`
- L2545 ` 	(for)`
- L2548 ` 	(if)`
- L2556 ` 	(if)`
- L2577 ` 	(if)`
- L2587 ` 	(for)`
- L2593 ` void(applyEffect_93)`
- L2597 ` 	(if)`
- L2599 ` 	(for)`
- L2603 ` 	(if)`
- L2624 ` 	(if)`
- L2633 ` 	(for)`
- L2636 ` 	(if)`
- L2644 ` 	(if)`
- L2665 ` 	(if)`
- L2675 ` 	(for)`
- L2681 ` void(applyEffect_94)`
- L2687 ` 	(for)`
- L2691 ` 	(if)`
- L2712 ` 	(if)`
- L2721 ` 	(for)`
- L2724 ` 	(if)`
- L2732 ` 	(if)`
- L2752 ` 	(if)`
- L2762 ` 	(for)`
- L2768 ` void(applyEffect_95)`
- L2774 ` 	(for)`
- L2778 ` 	(if)`
- L2799 ` 	(if)`
- L2808 ` 	(for)`
- L2811 ` 	(if)`
- L2819 ` 	(if)`
- L2840 ` 	(if)`
- L2850 ` 	(for)`
- L2856 ` void(applyEffect_85)`
- L2859 ` 	(if)`
- L2861 ` 	(for)`
- L2864 ` 	(if)`
- L2872 ` 	(if)`
- L2900 ` 	(if)`
- L2909 ` 	(for)`
- L2915 ` void(applyEffect_86)`
- L2918 ` 	(if)`
- L2920 ` 	(for)`
- L2923 ` 	(if)`
- L2931 ` 	(if)`
- L2959 ` 	(if)`
- L2968 ` 	(for)`
- L2974 ` void(applyEffect_87)`
- L2977 ` 	(if)`
- L2979 ` 	(for)`
- L2982 ` 	(if)`
- L2990 ` 	(if)`
- L3018 ` 	(if)`
- L3027 ` 	(for)`
- L3033 ` void(applyEffect_88)`
- L3036 ` 	(if)`
- L3038 ` 	(for)`
- L3041 ` 	(if)`
- L3049 ` 	(if)`
- L3077 ` 	(if)`
- L3086 ` 	(for)`
- L3092 ` void(applyEffect_89)`
- L3095 ` 	(if)`
- L3097 ` 	(for)`
- L3100 ` 	(if)`
- L3108 ` 	(if)`
- L3136 ` 	(if)`
- L3145 ` 	(for)`
- L3151 ` void(applyEffect_96)`
- L3157 ` 	(for)`
- L3161 ` 	(if)`
- L3171 ` 	(for)`
- L3173 ` 	(if)`
- L3192 ` 	(if)`
- L3202 ` 	(for)`
- L3205 ` 	(if)`
- L3213 ` 	(if)`
- L3223 ` 	(for)`
- L3225 ` 	(if)`
- L3245 ` 	(if)`
- L3254 ` 	(for)`
- L3260 ` void(applyEffect_97)`
- L3266 ` 	(for)`
- L3270 ` 	(if)`
- L3280 ` 	(for)`
- L3282 ` 	(if)`
- L3301 ` 	(if)`
- L3311 ` 	(for)`
- L3314 ` 	(if)`
- L3322 ` 	(if)`
- L3332 ` 	(for)`
- L3334 ` 	(if)`
- L3345 ` 	(if)`
- L3366 ` 	(if)`
- L3378 ` 	(for)`
- L3384 ` void(applyEffect_98)`
- L3390 ` 	(for)`
- L3394 ` 	(if)`
- L3404 ` 	(for)`
- L3406 ` 	(if)`
- L3425 ` 	(if)`
- L3435 ` 	(for)`
- L3438 ` 	(if)`
- L3446 ` 	(if)`
- L3456 ` 	(for)`
- L3458 ` 	(if)`
- L3478 ` 	(if)`
- L3487 ` 	(for)`
- L3493 ` void(applyEffect_99)`
- L3500 ` 	(for)`
- L3504 ` 	(if)`
- L3514 ` 	(for)`
- L3516 ` 	(if)`
- L3535 ` 	(if)`
- L3544 ` 	(for)`
- L3551 ` 	(if)`
- L3559 ` 	(if)`
- L3569 ` 	(for)`
- L3571 ` 	(if)`
- L3591 ` 	(if)`
- L3600 ` 	(for)`
- L3606 ` void(applyEffect_100)`
- L3612 ` 	(for)`
- L3616 ` 	(if)`
- L3626 ` 	(for)`
- L3628 ` 	(if)`
- L3647 ` 	(if)`
- L3656 ` 	(for)`
- L3659 ` 	(if)`
- L3667 ` 	(if)`
- L3677 ` 	(for)`
- L3679 ` 	(if)`
- L3700 ` 	(if)`
- L3709 ` 	(for)`
- L3715 ` void(applyEffect_132)`
- L3718 ` 	(for)`
- L3724 ` void(applyEffect_140)`
- L3727 ` 	(for)`
- L3732 ` void(applyEffect_765)`
- L3735 ` 	(for)`
- L3740 ` void(applyEffect_90)`
- L3756 ` 	(for)`
- L3766 ` 	(for)`
- L3772 ` void(applyEffect_108)`
- L3775 ` 	(if)`
- L3779 ` 	(if)`
- L3783 ` 	(if)`
- L3790 ` 	(for)`
- L3811 ` 	(for)`
- L3817 ` void(applyEffect_141)`
- L3820 ` 	(for)`
- L3824 ` 	(if)`
- L3838 ` void(applyEffect_320)`
- L3847 ` 	(for)`
- L3853 ` 	(if)`
- L3861 ` void(applyEffect_780)`
- L3867 ` 	(for)`
- L3903 ` void(setArgs)`
- L3908 ` void(setEffectID)`

#### `objects/Trunk.java` — 458 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Trunk
Fonctions :
- L29 ` public(Trunk)`
- L36 ` 	(for)`
- L52 ` int(get_id)`
- L57 ` int(get_house_id)`
- L62 ` int(get_mapid)`
- L67 ` int(get_cellid)`
- L72 ` Map<Integer, Objet>(get_object)`
- L77 ` long(get_kamas)`
- L82 ` void(set_kamas)`
- L87 ` String(get_key)`
- L92 ` void(set_key)`
- L97 ` int(get_owner_id)`
- L102 ` void(set_owner_id)`
- L107 ` void(Lock)`
- L112 `static Trunk(get_trunk_id_by_coord)`
- L115 ` 	(for)`
- L117 ` 	(if)`
- L124 `static void(LockTrunk)`
- L129 ` 	(if)`
- L145 ` 	(if)`
- L199 `static void(closeCode)`
- L210 `static ArrayList<Trunk>(getTrunksByHouse)`
- L214 `  (for)`
- L216 `  (if)`
- L224 ` String(parseToTrunkPacket)`
- L234 ` void(addInTrunk)`
- L248 ` 	(if)`
- L264 ` 	(if)`
- L291 ` 	(if)`
- L314 ` 	(for)`
- L317 ` 	(if)`
- L326 ` void(removeFromTrunk)`
- L334 ` 	(if)`
- L349 ` 	(if)`
- L380 ` 	(if)`
- L405 ` 	(for)`
- L408 ` 	(if)`
- L417 ` Objet(getSimilarTrunkItem)`
- L420 ` 	(for)`
- L429 ` String(parseTrunkObjetsToDB)`
- L433 ` 	(for)`
- L440 ` void(purgeTrunk)`
- L443 ` 	(for)`
- L449 ` void(moveTrunktoBank)`
- L452 ` 	(for)`

### Ancestrar Realm

#### `common/Ancestra.java` — 262 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Ancestra
Fonctions :
- L40 `static void(main)`
- L46 ` void(run)`
- L60 ` 	(if)`
- L82 `static void(loadConfiguration)`
- L94 ` 	(if)`
- L99 ` 	(if)`
- L103 ` 	(if)`
- L112 ` 	(if)`
- L116 ` 	(if)`
- L120 ` 	(if)`
- L124 ` 	(if)`
- L131 ` 	(if)`
- L135 ` 	(if)`
- L139 ` 	(if)`
- L143 ` 	(if)`
- L148 ` 	(if)`
- L185 `static void(addToErrorLog)`
- L199 `static void(addToRealmLog)`
- L213 `static void(addToComLog)`
- L227 `static void(closeServers)`
- L230 ` 	(if)`
- L253 `static String(makeHeader)`

#### `common/CryptManager.java` — 132 lignes
Rôle : Encodage/décodage Dofus : hash, cellules, IP, packet utils.
Classe(s) : class CryptManager
Fonctions :
- L4 `static String(CryptPassword)`
- L30 `static String(decryptpass)`
- L50 `static String(CryptIP)`
- L75 `static String(CryptPort)`
- L90 `static int(getIntByHashedValue)`
- L98 ` 	(if)`
- L105 `static char(getHashedValueByInt)`
- L113 `static String(toUtf)`

#### `common/Realm.java` — 87 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Realm
Fonctions :
- L15 `static boolean(IPcompareToBanIP)`
- L19 ` 	(for)`
- L26 `static void(loadRealm)`
- L46 `static void(addAccount)`
- L57 `static Map<Integer, Account>(getAccountsMap)`
- L62 `static void(deleteAccount)`
- L68 `static Account(getCompteByID)`
- L73 `static Account(getCompteByName)`

#### `common/SQLManager.java` — 364 lignes
Rôle : Accès SQL historique Ancestrar : chargements/sauvegardes directs.
Classe(s) : class SQLManager
Fonctions :
- L22 `static ResultSet(executeQuery)`
- L36 `static ResultSet(executeQueryG)`
- L59 `static PreparedStatement(newTransact)`
- L67 `static void(commitTransacts)`
- L72 ` 	(if)`
- L89 `static void(closeCons)`
- L105 `final boolean(setUpConnexion)`
- L112 ` 	(if)`
- L131 `static void(TIMER)`
- L134 ` 	(if)`
- L138 ` void(run)`
- L151 `static void(closeResultSet)`
- L165 `static void(closePreparedStatement)`
- L179 `static void(UPDATE_ACCOUNT)`
- L206 `static void(RESET_CUR_IP)`
- L223 `static int(getNumberPersosOnThisServer)`
- L248 `static void(LOAD_ACCOUNT_BY_USER)`
- L254 ` 	(while)`
- L286 `static void(LOAD_SERVERS)`
- L292 ` 	(while)`
- L315 `static int(LOAD_BANIP)`
- L322 `  (while)`
- L340 `static void(ADD_BANIP)`

#### `common/SendManager.java` — 97 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class SendManager
Fonctions :
- L18 `static String(get_BufferRemove)`
- L23 `static void(set_BufferRemove)`
- L28 `static void(del_BufferRemove)`
- L33 `static Map<Integer, Map<Long, Map<PrintWriter, String>>>(getPacketBuffer)`
- L38 `static Timer(FlushTimer)`
- L43 ` void(actionPerformed)`
- L45 `  (for)`
- L61 ` 	(for)`
- L67 ` 	(if)`
- L80 `static void(send)`

#### `common/SocketManager.java` — 194 lignes
Rôle : Constructeur central de packets réseau envoyés au client.
Classe(s) : class SocketManager
Fonctions :
- L13 `static void(send)`
- L20 ` 	(if)`
- L27 `static void(SEND_POLICY_FILE)`
- L38 `static void(SEND_REQUIRED_VERSION)`
- L45 `static void(SEND_BANNED)`
- L52 `static void(SEND_TOO_MANY_PLAYER_ERROR)`
- L59 `static String(SEND_HC_PACKET)`
- L76 `static void(SEND_LOGIN_ERROR)`
- L83 `static void(SEND_Af_PACKET)`
- L92 `static void(SEND_Ad_Ac_AH_AlK_AQ_PACKETS)`
- L102 ` 	(for)`
- L104 ` 	(if)`
- L107 ` 	(if)`
- L119 ` 	(if)`
- L136 `static void(SEND_ALREADY_CONNECTED)`
- L143 `static void(refresh)`
- L150 ` 	(for)`
- L160 `static void(SEND_PERSO_LIST)`
- L167 ` 	(for)`
- L175 `static void(SEND_GAME_SERVER_IP)`

#### `communication/ComServer.java` — 67 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ComServer implements Runnable
Fonctions :
- L12 ` public(ComServer)`
- L29 ` void(run)`
- L32 ` 	(while)`
- L48 ` void(kickAll)`
- L61 ` Thread(getThread)`

#### `communication/ComThread.java` — 309 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ComThread implements Runnable
Fonctions :
- L24 ` public(ComThread)`
- L45 ` 	(if)`
- L50 ` 	(for)`
- L59 ` void(run)`
- L66 ` 	(while)`
- L69 ` 	(if)`
- L74 ` 	(if)`
- L89 ` 	(if)`
- L94 ` 	(for)`
- L110 ` 	(if)`
- L115 ` 	(for)`
- L127 ` void(kick)`
- L134 ` 	(if)`
- L139 ` 	(for)`
- L157 ` void(sendDeco)`
- L176 ` void(sendAddWaiting)`
- L195 ` void(sendGetOnline)`
- L214 ` void(parsePacket)`
- L217 ` 	(switch)`
- L220 ` 	(switch)`
- L228 ` 	(for)`
- L233 ` 	(if)`
- L244 ` 	(if)`
- L256 ` 	(if)`
- L261 ` 	(switch)`
- L281 ` 	(if)`
- L286 ` 	(switch)`
- L302 ` 	(for)`

#### `objects/Account.java` — 181 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Account
Fonctions :
- L24 ` public(Account)`
- L40 ` void(setCurIP)`
- L45 ` String(getLastConnectionDate)`
- L50 ` void(setLastIP)`
- L55 ` String(getLastIP)`
- L60 ` void(setLastConnectionDate)`
- L65 ` void(setRealmThread)`
- L70 ` RealmThread(getRealmThread)`
- L75 ` boolean(isValidPass)`
- L80 ` int(get_GUID)`
- L85 ` String(get_name)`
- L90 ` String(get_pass)`
- L95 ` String(get_pseudo)`
- L104 ` 	(if)`
- L130 ` String(get_lastIP)`
- L135 ` String(get_question)`
- L140 ` String(get_reponse)`
- L145 ` boolean(isBanned)`
- L150 ` void(setBanned)`
- L155 ` int(get_gmLvl)`
- L160 ` String(get_curIP)`
- L165 ` void(setGmLvl)`
- L170 ` String(get_giftID)`
- L175 ` void(set_giftID)`

#### `objects/GameServer.java` — 126 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class GameServer
Fonctions :
- L20 ` public(GameServer)`
- L35 ` ComThread(getThread)`
- L40 ` void(setThread)`
- L45 ` String(getHost)`
- L50 ` String(getKey)`
- L55 ` String(getName)`
- L60 ` String(getUser)`
- L65 ` String(getPassword)`
- L70 ` int(getID)`
- L75 ` String(getIP)`
- L80 ` int(getPort)`
- L85 ` int(getState)`
- L90 ` void(setState)`
- L95 ` void(setBlockLevel)`
- L100 ` int(getBlockLevel)`
- L105 ` void(set_PlayerLimit)`
- L110 ` int(get_PlayerLimit)`
- L115 ` void(set_NumPlayer)`
- L120 ` int(get_NumPlayer)`

#### `realm/RealmServer.java` — 83 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class RealmServer implements Runnable
Fonctions :
- L14 ` public(RealmServer)`
- L31 ` void(run)`
- L34 ` 	(while)`
- L50 ` void(kickAll)`
- L64 ` 	(for)`
- L72 ` void(delClient)`
- L77 ` Thread(getThread)`

#### `realm/RealmThread.java` — 299 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class RealmThread implements Runnable
Fonctions :
- L32 ` public(RealmThread)`
- L53 ` 	(if)`
- L60 ` void(run)`
- L70 ` 	(while)`
- L73 ` 	(if)`
- L79 ` 	(if)`
- L95 ` 	(if)`
- L110 ` 	(if)`
- L121 ` void(kick)`
- L131 ` 	(if)`
- L145 ` void(closeSocket)`
- L152 ` void(refresh)`
- L167 ` void(parsePacket)`
- L170 ` 	(switch)`
- L173 ` 	(if)`
- L183 ` 	(if)`
- L256 ` 	(if)`
- L263 ` 	(if)`
- L268 ` 	(if)`
- L280 ` 	(if)`
- L290 ` 	(if)`

### StarLoco Game

#### `org/starloco/locos/anims/Animation.java` — 24 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Animation
Fonctions :
- L14 ` public(Animation)`
- L21 ` KeyFrame(getFrame)`

#### `org/starloco/locos/anims/KeyFrame.java` — 58 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class KeyFrame
Fonctions :
- L18 ` private(KeyFrame)`
- L20 `  (if)`
- L29 `static KeyFrame(fromScriptValue)`
- L47 ` boolean(hasDuration)`
- L49 ` int(durationMillis)`
- L51 ` boolean(isObjectInteractive)`
- L53 ` Map<String, Integer>(getCellOverrides)`

#### `org/starloco/locos/annotation/DofusMessage.java` — 13 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : (aucune classe détectée)
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/annotation/Handler.java` — 12 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : (aucune classe détectée)
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/api/AbstractDofusMessage.java` — 43 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class AbstractDofusMessage
Fonctions :
- L10 ` public(AbstractDofusMessage)`
- L14 ` StringBuilder(getOutput)`
- L18 ` void(setOutput)`
- L22 ` StringBuilder(getInput)`
- L26 ` void(setInput)`
- L30 ` GameClient(getClient)`
- L34 ` void(setClient)`

#### `org/starloco/locos/api/AbstractEventMessageDispatcher.java` — 41 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class AbstractEventMessageDispatcher <T>
Fonctions :
- L15 ` void(doPublish)`
- L17 `  (for)`
- L27 ` void(subscribe)`
- L29 `  (for)`
- L30 `  (if)`
- L31 `  (if)`

#### `org/starloco/locos/area/Area.java` — 76 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Area implements Scripted<SArea>
Fonctions :
- L18 ` public(Area)`
- L24 ` int(getId)`
- L28 ` int(getSuperArea)`
- L32 ` int(getAlignement)`
- L36 ` void(setAlignement)`
- L48 ` int(getPrismId)`
- L52 ` void(setPrismId)`
- L56 ` void(addSubArea)`
- L60 ` ArrayList<SubArea>(getSubAreas)`
- L64 ` ArrayList<GameMap>(getMaps)`
- L71 ` a(scripted)`

#### `org/starloco/locos/area/SubArea.java` — 108 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class SubArea implements Scripted<SSubArea>
Fonctions :
- L27 ` public(SubArea)`
- L37 ` int(getId)`
- L41 ` String(getName)`
- L45 ` Area(getArea)`
- L49 ` int(getAlignment)`
- L53 ` void(setAlignment)`
- L57 ` Prism(getPrism)`
- L61 ` void(setPrism)`
- L65 ` boolean(getConquerable)`
- L69 ` void(setConquerable)`
- L73 ` List<GameMap>(getMaps)`
- L77 ` void(addMapID)`
- L81 ` boolean(ownNearestSubArea)`
- L83 `  (for)`
- L90 ` boolean(isMoreThanEnemies)`
- L93 `  (for)`
- L95 `  (for)`
- L103 ` a(scripted)`

#### `org/starloco/locos/area/map/Actor.java` — 8 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : interface Actor
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/area/map/CellsDataProvider.java` — 393 lignes
Rôle : Décodage/override des données de cellules Dofus : active, LoS, movement, ground, objets.
Classe(s) : interface CellsDataProvider, class RawCellsDataProvider implements CellsDataProvider, class CellsDataOverride implements CellsDataProvider
Fonctions :
- L16 `static int(activeRaw)`
- L20 `static int(lineOfSightRaw)`
- L24 `static int(movementRaw)`
- L28 `static int(groundRotRaw)`
- L32 `static int(groundLevelRaw)`
- L36 `static int(groundSlopeRaw)`
- L40 `static int(groundFlipRaw)`
- L44 `static int(groundNumRaw)`
- L48 `static int(object1NumRaw)`
- L52 `static int(object1RotRaw)`
- L56 `static int(object1FlipRaw)`
- L60 `static int(object2NumRaw)`
- L64 `static int(object2FlipRaw)`
- L68 `static int(object2InteractiveRaw)`
- L72 ` default boolean(active)`
- L77 ` default boolean(lineOfSight)`
- L81 ` default int(movement)`
- L85 ` default int(object2)`
- L89 ` default boolean(object2Interactive)`
- L93 `static long(b64ToLong)`
- L134 ` public(RawCellsDataProvider)`
- L143 ` t(cellCount)`
- L146 ` g(cellData)`
- L151 ` t(overrideMask)`
- L161 ` public(CellsDataOverride)`
- L165 ` t(cellCount)`
- L168 ` g(cellData)`
- L174 `  (if)`
- L180 ` t(overrideMask)`
- L206 ` String(encodeCellData)`
- L233 ` boolean(setActive)`
- L247 ` boolean(setInteractive)`
- L260 ` boolean(setMovement)`
- L273 ` boolean(setGroundNum)`
- L284 ` boolean(setObject1Num)`
- L295 ` boolean(setObject2Num)`
- L306 `static void(apply)`
- L317 ` boolean(applyOverrides)`
- L322 `  (while)`
- L324 `  (switch)`
- L351 ` boolean(removeOverrides)`
- L356 `  (while)`
- L358 `  (switch)`
- L383 ` Stream<Integer>(getOverrides)`
- L387 ` boolean(isEmpty)`

#### `org/starloco/locos/area/map/GameCase.java` — 1069 lignes
Rôle : Cellule de carte : walkable, acteurs présents, objets interactifs/drops, interactions métier/trigger.
Classe(s) : class GameCase
Fonctions :
- L18 ` public(GameCase)`
- L23 ` int(getId)`
- L25 ` List<T>(getActorsOfType)`
- L32 ` List<Player>(getPlayers)`
- L36 ` List<Fighter>(getFighters)`
- L40 ` GameObject(getDroppedItem)`
- L42 `  (if)`
- L47 ` void(clearDroppedItem)`
- L51 ` InteractiveObject(getObject)`
- L55 ` boolean(isWalkable)`
- L58 `  (switch)`
- L68 ` boolean(isWalkableFight)`
- L72 ` boolean(isWalkable)`
- L82 `  (if)`
- L91 ` void(addActor)`
- L95 ` void(addPlayer)`
- L99 ` void(addFighter)`
- L101 ` void(removeActor)`
- L105 ` void(removePlayer)`
- L109 ` void(removeFighter)`
- L112 ` Fighter(getFirstFighter)`
- L1053 ` void(tryDropItem)`
- L1055 `  (if)`
- L1059 ` boolean(blockLoS)`

#### `org/starloco/locos/area/map/GameMap.java` — 1568 lignes
Rôle : Carte : groupes de mobs, cellules, portes/IO, fights, drops, placement, triggers, sauvegarde map.
Classe(s) : class GameMap
Fonctions :
- L50 ` MobGroupDef(randomizeMobGroup)`
- L69 ` d(update)`
- L72 `  (if)`
- L74 `  (for)`
- L77 `  (if)`
- L91 `  (if)`
- L93 `  (if)`
- L94 `  (if)`
- L96 `  (for)`
- L110 `  (for)`
- L112 `  (if)`
- L117 `  (for)`
- L120 `  (if)`
- L132 ` <RespawnGroup>(get)`
- L165 ` public(GameMap)`
- L187 ` void(refreshInteractiveObjects)`
- L193 ` String(getForbidden)`
- L197 `static void(removeMountPark)`
- L201 `  (if)`
- L203 `  (if)`
- L212 `  (if)`
- L235 `static int(getObjResist)`
- L241 `  (for)`
- L242 `  (for)`
- L251 `  (if)`
- L261 `  (if)`
- L281 `static int(getObjResist)`
- L286 `  (for)`
- L287 `  (for)`
- L295 `  (if)`
- L304 `  (if)`
- L323 ` void(addMobExtra)`
- L327 ` List<MonsterGrade>(getMobPossibles)`
- L331 ` int(getId)`
- L335 ` String(getDate)`
- L339 ` int(getW)`
- L343 ` int(getH)`
- L347 ` String(getKey)`
- L351 ` List<List<Integer>>(getPlaces)`
- L355 ` List<GameCase>(getCases)`
- L359 ` GameCase(getCase)`
- L361 `  (if)`
- L366 ` Fight(newFight)`
- L380 ` void(removeFight)`
- L382 `  (if)`
- L384 `  (while)`
- L386 `  (if)`
- L395 ` int(getNbrFight)`
- L399 ` Fight(getFight)`
- L408 ` List<Fight>(getFights)`
- L414 ` Map<Integer, MonsterGroup>(getMobGroups)`
- L418 ` Map<Integer, MonsterGroup>(getFixMobGroups)`
- L422 ` void(removeNpcOrMobGroup)`
- L427 ` Npc(addNpc)`
- L445 ` Map<Integer, Npc>(getNpcs)`
- L449 ` Npc(getNpc)`
- L453 ` Npc(getNpcByTemplateId)`
- L460 ` Npc(RemoveNpc)`
- L464 ` void(applyEndFightAction)`
- L470 ` void(applyInitFightAction)`
- L474 ` void(applyStartFightAction)`
- L477 ` int(getX)`
- L481 ` int(getY)`
- L485 ` SubArea(getSubArea)`
- L487 ` Area(getArea)`
- L491 ` MountPark(getMountPark)`
- L495 ` int(getMaxGroupNumb)`
- L499 ` int(getMaxTeam)`
- L503 ` boolean(containsForbiddenCellSpawn)`
- L507 ` GameMap(getMapCopy)`
- L511 ` void(addPlayer)`
- L515 `  (if)`
- L518 `  (if)`
- L530 ` ArrayList<Player>(getPlayers)`
- L537 ` void(sendFloorItems)`
- L544 ` void(delAllDropItem)`
- L546 `  (for)`
- L551 ` int(getStoreCount)`
- L557 ` boolean(haveMobFix)`
- L561 ` boolean(isPossibleToPutMonster)`
- L565 ` boolean(loadExtraMonsterOnMap)`
- L580 ` void(loadMonsterOnMap)`
- L589 ` void(mute)`
- L593 ` boolean(isMute)`
- L597 ` boolean(isAggroByMob)`
- L603 `  (for)`
- L610 `  (if)`
- L621 ` void(spawnAfterTimeGroup)`
- L625 ` void(spawnAfterTimeGroupFix)`
- L629 ` InteractiveObject(getInteractiveObject)`
- L633 ` String(getAnimationState)`
- L639 ` void(setAnimationState)`
- L650 `  (if)`
- L668 `  (if)`
- L678 `  (if)`
- L684 `  (if)`
- L690 ` void(setAnimationState)`
- L694 ` void(sendOverrides)`
- L699 ` void(sendAnimStates)`
- L714 ` public(RespawnGroup)`
- L721 ` void(spawnGroup)`
- L730 `  (if)`
- L731 `  (for)`
- L736 `  (while)`
- L765 ` void(respawnGroup)`
- L770 ` void(spawnGroupWith)`
- L788 ` void(spawnNewGroup)`
- L806 ` MonsterGroup(spawnGroupOnCommand)`
- L821 ` int(spawnMobGroup)`
- L836 ` void(refreshSpawns)`
- L838 `  (for)`
- L850 ` String(getPlayersGMsPackets)`
- L861 ` String(getFightersGMsPackets)`
- L869 ` String(getFighterGMPacket)`
- L878 ` String(getFighterGMPacket)`
- L886 ` String(getMobGroupGMsPackets)`
- L894 `  (for)`
- L907 ` String(getPrismeGMPacket)`
- L911 `  (if)`
- L912 `  (for)`
- L913 `  (if)`
- L921 ` String(getNpcsGMsPackets)`
- L929 `  (for)`
- L942 ` String(getObjectsGDsPackets)`
- L957 ` void(startFightMonsterVersusMonster)`
- L971 ` void(startFightVersusMonstres)`
- L975 `  (if)`
- L979 `  (if)`
- L1029 ` void(startFightVersusProtectors)`
- L1033 `  (if)`
- L1039 `  (if)`
- L1058 `  (if)`
- L1074 ` void(startFightVersusPercepteur)`
- L1078 `  (if)`
- L1097 ` void(startFightVersusPrisme)`
- L1101 `  (if)`
- L1115 ` int(getRandomFreeCellId)`
- L1118 `  (for)`
- L1126 `  (if)`
- L1127 `  (switch)`
- L1204 `  (for)`
- L1239 ` void(onMapMonsterDeplacement)`
- L1245 `  (for)`
- L1248 `  (switch)`
- L1271 `  (if)`
- L1276 `  (if)`
- L1371 ` boolean(checkCell)`
- L1375 ` String(getObjects)`
- L1381 `  (for)`
- L1382 `  (for)`
- L1391 ` String(getObjDurable)`
- L1394 `  (for)`
- L1395 `  (for)`
- L1402 ` boolean(cellSideLeft)`
- L1412 ` boolean(cellSideRight)`
- L1422 ` boolean(cellSide)`
- L1432 ` String(getGMOfMount)`
- L1438 `  (for)`
- L1453 ` String(getGMOfMount)`
- L1460 `  (for)`
- L1472 ` Player(getPlayer)`
- L1481 ` void(onPlayerArriveOnCell)`
- L1487 `  (if)`
- L1489 `  (if)`
- L1490 `  (synchronized)`
- L1492 `  (if)`
- L1513 `  (for)`
- L1521 ` void(send)`
- L1525 ` Fight(newFightbouf)`
- L1541 ` void(addStaticGroup)`
- L1553 ` Stream<Integer>(findObjectsPositionsByID)`
- L1559 ` void(addActor)`
- L1565 ` SMap(scripted)`

#### `org/starloco/locos/area/map/MapData.java` — 164 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class MapData implements CellsDataProvider
Fonctions :
- L44 ` protected(MapData)`
- L48 `  (if)`
- L82 `  (if)`
- L96 `  (if)`
- L104 ` SubArea(getSubArea)`
- L106 ` Area(getArea)`
- L109 ` List<List<Integer>>(getPlaces)`
- L112 ` String(getForbidden)`
- L123 ` void(onFightInit)`
- L126 ` void(onFightStart)`
- L130 ` int(cellCount)`
- L134 ` Map<Integer, Integer>(interactiveObjects)`
- L135 ` long(cellData)`
- L139 ` int(overrideMask)`
- L142 `static List<List<Integer>>(decodePositions)`
- L157 `static String(encodePositions)`

#### `org/starloco/locos/area/map/OrthogonalProj.java` — 58 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class OrthogonalProj
Fonctions :
- L4 `static int(getOrthX)`
- L7 `static int(getOrthXFromY)`
- L10 `static int(getOrthY)`
- L18 `static short(getCellId)`
- L22 `static int(getCellsDistance)`
- L30 `static int(getOrthCellID)`
- L39 `static boolean(isEdgeCell)`
- L47 `static boolean(isEdgeCellOrth)`

#### `org/starloco/locos/area/map/ScriptMapData.java` — 186 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ScriptMapData extends MapData
Fonctions :
- L25 ` private(ScriptMapData)`
- L52 `static ScriptMapData(build)`
- L95 ` >(getNPCs)`
- L106 ` <MobGroupDef>(getStaticGroups)`
- L115 ` d(onMoveEnd)`
- L124 ` n(cellHasMoveEndActions)`
- L135 ` Optional<Object>(onFightFunctionByType)`
- L144 ` d(onFightInit)`
- L154 ` d(onFightStart)`
- L163 ` d(onFightEnd)`
- L176 ` n(hasFightEndForType)`
- L181 ` Table(scripted)`

#### `org/starloco/locos/auction/Auction.java` — 59 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Auction
Fonctions :
- L15 ` public(Auction)`
- L22 ` int(getPrice)`
- L26 ` Player(getOwner)`
- L30 ` Player(getCustomer)`
- L34 ` void(setCustomer)`
- L38 ` GameObject(getObject)`
- L42 ` void(setObject)`
- L46 ` void(setPrice)`
- L50 ` byte(getRetry)`
- L54 ` void(incRetry)`

#### `org/starloco/locos/auction/AuctionManager.java` — 365 lignes
Rôle : Système auction/hôtel de vente alternatif : checks différés, offres, achat/vente.
Classe(s) : class AuctionManager extends Updatable<Void>
Fonctions :
- L28 `static AuctionManager(getInstance)`
- L39 ` public(AuctionManager)`
- L44 ` List<Auction>(getAuctions)`
- L48 ` void(talk)`
- L52 `  (for)`
- L58 `  (if)`
- L59 `  (for)`
- L66 ` void(talkNext)`
- L70 `  (for)`
- L74 `  (for)`
- L79 ` void(talk)`
- L85 ` String(getTalkStringObject)`
- L89 ` boolean(currentIsAvailable)`
- L93 ` boolean(auctionIsAvailable)`
- L99 ` d(update)`
- L102 `  (if)`
- L105 `  (if)`
- L107 `  (if)`
- L109 `  (if)`
- L119 `synchronized void(check)`
- L121 `  (if)`
- L128 ` boolean(start)`
- L130 `  (if)`
- L138 ` boolean(newAuction)`
- L140 `  (if)`
- L141 `  (if)`
- L157 ` boolean(counter)`
- L159 `  (if)`
- L160 `  (if)`
- L173 ` void(stop)`
- L175 `  (if)`
- L178 `  (if)`
- L190 `  (if)`
- L207 ` d(get)`
- L212 ` void(onPlayerLoadMap)`
- L215 `  (if)`
- L219 `  (if)`
- L222 `  (if)`
- L231 `synchronized void(onPlayerChat)`
- L241 `  (if)`
- L244 `  (if)`
- L251 `  (if)`
- L268 ` void(onPlayerCommand)`
- L271 `  (if)`
- L273 `  (if)`
- L275 `  (if)`
- L277 `  (for)`
- L291 `  (if)`
- L312 ` void(onPlayerOpenExchange)`
- L320 ` boolean(onPlayerChangeItemInNpcExchange)`
- L324 `  (if)`
- L326 `  (if)`
- L333 ` boolean(onPlayerAccept)`
- L336 `  (if)`
- L338 `  (if)`
- L340 `  (if)`
- L353 ` boolean(isValid)`

#### `org/starloco/locos/client/Account.java` — 584 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Account
Fonctions :
- L54 ` public(Account)`
- L79 `  (if)`
- L80 `  (for)`
- L89 `  (if)`
- L90 `  (for)`
- L102 ` long(getHeureVote)`
- L106 ` String(getLastVoteIP)`
- L110 ` int(getId)`
- L114 ` String(getName)`
- L118 ` String(getPseudo)`
- L122 ` String(getAnswer)`
- L126 ` String(getCurrentIp)`
- L130 ` void(setCurrentIp)`
- L134 ` String(getLastIP)`
- L138 ` void(setLastIP)`
- L142 ` String(getLastConnectionDate)`
- L146 ` void(setLastConnectionDate)`
- L150 ` long(getPoints)`
- L155 ` boolean(modPoints)`
- L162 ` void(mute)`
- L171 ` void(unMute)`
- L178 ` boolean(isMuted)`
- L189 ` long(getMuteTime)`
- L195 ` String(getMutePseudo)`
- L201 ` List<GameObject>(getBank)`
- L205 ` String(parseBankObjectsToDB)`
- L214 ` long(getBankKamas)`
- L218 ` void(setBankKamas)`
- L223 ` GameClient(getGameClient)`
- L227 ` void(setGameClient)`
- L231 ` Map<Integer, Player>(getPlayers)`
- L238 ` Player(getCurrentPlayer)`
- L242 ` void(setCurrentPlayer)`
- L246 ` boolean(isBanned)`
- L250 ` void(setBanned)`
- L254 ` boolean(isOnline)`
- L258 ` void(setState)`
- L263 ` byte(getState)`
- L267 ` void(setSubscribe)`
- L271 ` long(getSubscribeRemaining)`
- L278 ` boolean(isSubscribe)`
- L285 ` boolean(isSubscribeWithoutCondition)`
- L290 ` boolean(createPlayer)`
- L295 ` void(deletePlayer)`
- L300 ` void(sendOnline)`
- L302 `  (for)`
- L308 ` void(addFriend)`
- L310 `  (if)`
- L316 `  (if)`
- L323 `  (if)`
- L330 `  (if)`
- L335 `  (if)`
- L343 ` void(removeFriend)`
- L345 `  (if)`
- L354 ` boolean(isFriendWith)`
- L358 ` Stream<Integer>(getFriendIds)`
- L362 ` String(parseFriendListToDB)`
- L365 `  (for)`
- L372 ` String(parseFriendList)`
- L377 `  (for)`
- L392 ` void(addEnemy)`
- L394 `  (if)`
- L398 `  (if)`
- L406 ` void(removeEnemy)`
- L408 `  (if)`
- L417 ` boolean(isEnemyWith)`
- L421 ` String(parseEnemyListToDB)`
- L424 `  (for)`
- L431 ` String(parseEnemyList)`
- L436 `  (for)`
- L451 ` List<BigStoreListing>(getHdvEntries)`
- L455 ` int(countHdvEntries)`
- L459 ` void(resetAllChars)`
- L461 `  (for)`
- L462 `  (if)`
- L480 ` void(disconnect)`
- L491 `  (if)`
- L492 `  (if)`
- L497 `  (if)`
- L514 ` void(updateVote)`
- L520 ` void(parseBank)`
- L522 `  (if)`
- L526 `  (if)`
- L528 `  (for)`
- L529 `  (if)`
- L538 ` void(addGift)`
- L541 `  (if)`
- L546 `  (if)`
- L552 ` void(addQuestProgression)`
- L556 ` void(delQuestProgress)`
- L564 ` QuestProgress(getQuestProgress)`
- L570 ` Stream<QuestProgress>(getQuestProgressions)`
- L575 ` void(saveQuestProgress)`
- L580 ` SAccount(scripted)`

#### `org/starloco/locos/client/Player.java` — 6088 lignes
Rôle : Joueur/personnage : stats, inventaire, sorts, XP, level-up, banque, monture/familier, échanges, groupes, paquets perso.
Classe(s) : class Player implements Scripted<SPlayer>, Actor
Fonctions :
- L236 ` ArrayList<Integer>(getIsCraftingType)`
- L240 ` public(Player)`
- L305 ` private(Player)`
- L376 `  (if)`
- L388 `  (if)`
- L393 `  (if)`
- L400 `  (if)`
- L405 `  (if)`
- L406 `  (for)`
- L414 `  (if)`
- L426 `  (if)`
- L428 `  (for)`
- L459 `  (if)`
- L460 `  (for)`
- L481 ` void(parseObjects)`
- L483 `  (if)`
- L488 `  (for)`
- L506 `static Player(create)`
- L509 `  (if)`
- L544 `static String(getCompiledEmote)`
- L550 ` int(getId)`
- L554 ` void(setId)`
- L558 ` String(getName)`
- L562 ` void(setName)`
- L571 ` void(setColors)`
- L579 ` Group(getGroup)`
- L583 ` void(setGroupe)`
- L589 ` boolean(isInvisible)`
- L593 ` void(setInvisible)`
- L597 ` int(getSexe)`
- L601 ` void(setSexe)`
- L606 ` int(getClasse)`
- L610 ` void(setClasse)`
- L614 ` int[](getColors)`
- L619 `  (if)`
- L620 `  (if)`
- L633 ` int(getColor1)`
- L637 ` int(getColor2)`
- L641 ` int(getColor3)`
- L645 ` int(getLevel)`
- L649 ` void(setLevel)`
- L653 ` int(getEnergy)`
- L657 ` void(setEnergy)`
- L661 ` long(getExp)`
- L665 ` void(setExp)`
- L669 ` int(getCurPdv)`
- L674 ` void(setPdv)`
- L685 ` int(getMaxPdv)`
- L689 ` void(setMaxPdv)`
- L696 ` Stats(getStats)`
- L703 ` Stats(getStatsParcho)`
- L707 ` String(parseStatsParcho)`
- L714 ` boolean(getDoAction)`
- L718 ` void(setDoAction)`
- L722 ` void(setRoleplayBuff)`
- L725 `  (switch)`
- L735 `  (if)`
- L749 ` void(setBenediction)`
- L751 `  (if)`
- L756 `  (if)`
- L761 `  (switch)`
- L778 ` void(setMalediction)`
- L781 `  (switch)`
- L788 `  (if)`
- L792 `  (if)`
- L801 `  (if)`
- L808 ` void(setMascotte)`
- L810 `  (if)`
- L815 `  (if)`
- L830 ` void(setCandy)`
- L832 `  (if)`
- L838 `  (switch)`
- L864 ` void(calculTurnCandy)`
- L867 `  (if)`
- L869 `  (if)`
- L878 `  (if)`
- L880 `  (if)`
- L889 `  (if)`
- L891 `  (if)`
- L900 `  (if)`
- L902 `  (if)`
- L907 `  (switch)`
- L921 `  (if)`
- L923 `  (if)`
- L934 ` List<Spell.SortStats>(getSpells)`
- L938 ` boolean(isSpec)`
- L942 ` void(setSpec)`
- L946 ` String(getAllTitle)`
- L951 ` void(setAllTitle)`
- L967 ` void(teleportOldMap)`
- L969 `  (if)`
- L975 ` void(setCurrentPositionToOldPosition)`
- L977 `  (if)`
- L982 ` void(setOldPosition)`
- L987 ` void(setOnline)`
- L991 ` boolean(isOnline)`
- L995 ` Party(getParty)`
- L999 ` void(setParty)`
- L1003 ` String(encodeSpellsToDB)`
- L1009 `  (if)`
- L1017 `  (for)`
- L1029 ` void(parseSpells)`
- L1042 `  (if)`
- L1050 `  (for)`
- L1063 `  (if)`
- L1073 ` void(parseSpellsFullMorph)`
- L1078 `  (for)`
- L1087 `  (if)`
- L1101 ` Pair<Integer, Integer>(getSavePosition)`
- L1105 ` void(setSavePos)`
- L1109 ` long(getKamas)`
- L1113 ` void(setKamas)`
- L1117 ` Map<Integer, SpellEffect>(get_buff)`
- L1121 ` Account(getAccount)`
- L1125 ` int(get_spellPts)`
- L1132 ` void(setSpellPoints)`
- L1139 ` Guild(getGuild)`
- L1145 ` void(setChangeName)`
- L1150 ` boolean(isChangeName)`
- L1154 ` boolean(isReady)`
- L1158 ` void(setReady)`
- L1162 ` int(getDuelId)`
- L1166 ` void(setDuelId)`
- L1170 ` Fight(getFight)`
- L1174 ` void(setFight)`
- L1176 `  (if)`
- L1185 ` boolean(is_showFriendConnection)`
- L1189 ` boolean(is_showWings)`
- L1193 ` boolean(isShowSeller)`
- L1197 ` void(setShowSeller)`
- L1201 ` String(get_canaux)`
- L1205 ` GameCase(getCurCell)`
- L1209 ` void(setCurCell)`
- L1213 ` int(get_size)`
- L1217 ` void(set_size)`
- L1221 ` int(getGfxId)`
- L1225 ` void(setGfxId)`
- L1227 `  (if)`
- L1236 ` boolean(isMorphMercenaire)`
- L1240 ` GameMap(getCurMap)`
- L1244 ` void(setCurMap)`
- L1248 ` boolean(isAway)`
- L1252 ` void(setAway)`
- L1256 ` boolean(isSitted)`
- L1260 ` boolean(setSitted)`
- L1262 `  (if)`
- L1271 ` int(getCapital)`
- L1275 ` boolean(canLearnJob)`
- L1279 `  (if)`
- L1283 `  (if)`
- L1288 `  (if)`
- L1290 `  (if)`
- L1299 `  (if)`
- L1302 `  (if)`
- L1303 `  (if)`
- L1310 `  (if)`
- L1311 `  (if)`
- L1320 ` boolean(tryLearnJob)`
- L1329 ` void(startScenario)`
- L1336 ` g(Id)`
- L1341 ` g(name)`
- L1346 ` void(openDocument)`
- L1351 ` void(showReceivedItem)`
- L1355 ` void(resetStats)`
- L1370 ` void(spellResetPanel)`
- L1382 ` public(EnsureSpellLevelResult)`
- L1392 ` EnsureSpellLevelResult(ensureSpellLevelSilent)`
- L1402 `  (if)`
- L1406 `  (if)`
- L1418 ` boolean(ensureSpellLevel)`
- L1426 `  (if)`
- L1437 `  (if)`
- L1442 ` void(learnSpell)`
- L1444 `  (if)`
- L1448 `  (if)`
- L1458 ` boolean(learnSpell)`
- L1460 `  (if)`
- L1464 `  (if)`
- L1470 `  (if)`
- L1479 ` boolean(unlearnSpell)`
- L1481 `  (if)`
- L1493 ` boolean(unlearnSpell)`
- L1507 `  (if)`
- L1514 `  (if)`
- L1525 ` boolean(boostSpell)`
- L1532 `  (if)`
- L1533 `  (if)`
- L1549 ` void(boostSpellIncarnation)`
- L1551 `  (for)`
- L1558 ` boolean(forgetSpell)`
- L1560 `  (if)`
- L1566 `  (if)`
- L1575 ` void(demorph)`
- L1577 `  (if)`
- L1584 ` boolean(getMorphMode)`
- L1588 ` int(getMorphId)`
- L1592 ` void(setMorphId)`
- L1596 ` void(setFullMorph)`
- L1601 `  (if)`
- L1609 `  (if)`
- L1611 `  (if)`
- L1616 `  (if)`
- L1639 `  (if)`
- L1643 `  (if)`
- L1670 ` boolean(isMorph)`
- L1674 ` boolean(canCac)`
- L1678 ` void(unsetMorph)`
- L1684 ` void(unsetFullMorph)`
- L1704 `  (if)`
- L1711 ` String(encodeSpellListForSL)`
- L1723 ` void(setSpellShortcuts)`
- L1730 ` void(removeSpellShortcutAtPosition)`
- L1738 ` Spell.SortStats(getSortStatBySortIfHas)`
- L1742 ` String(parseALK)`
- L1756 `  (if)`
- L1767 `  (if)`
- L1775 ` void(remove)`
- L1779 ` void(OnJoinGame)`
- L1788 `  (if)`
- L1799 `  (if)`
- L1801 `  (if)`
- L1812 `  (if)`
- L1827 `  (if)`
- L1886 `  (for)`
- L1887 `  (if)`
- L1890 `  (if)`
- L1922 ` void(SetSeeFriendOnline)`
- L1926 ` void(sendGameCreate)`
- L1939 `  (if)`
- L1952 ` String(parseToOa)`
- L1956 ` String(parseToGM)`
- L1970 `  (if)`
- L1980 `  (if)`
- L1987 `  (if)`
- L1997 `  (if)`
- L2027 ` String(parseToMerchant)`
- L2039 `  (if)`
- L2053 ` String(getGMStuffString)`
- L2065 `  (if)`
- L2070 `  (if)`
- L2080 `  (if)`
- L2085 `  (if)`
- L2108 ` String(getAsPacket)`
- L2119 `  (if)`
- L2121 `  (if)`
- L2176 ` int(getGrade)`
- L2184 ` String(xpString)`
- L2186 `  (if)`
- L2205 ` int(emoteActive)`
- L2209 ` void(setEmoteActive)`
- L2213 ` Stats(getStuffStats)`
- L2219 `  (synchronized)`
- L2222 `  (if)`
- L2228 `  (if)`
- L2244 ` Stats(getBuffsStats)`
- L2256 ` int(get_orientation)`
- L2260 ` void(set_orientation)`
- L2264 ` int(getInitiative)`
- L2266 `  (if)`
- L2290 ` Stats(getTotalStats)`
- L2293 `  (if)`
- L2304 ` Stats(getDonsStats)`
- L2309 ` Stats(newStatsMorph)`
- L2326 ` int(getPodUsed)`
- L2329 `  (for)`
- L2338 ` int(getMaxPod)`
- L2346 `  (for)`
- L2355 ` void(refreshLife)`
- L2363 `  (if)`
- L2371 `  (if)`
- L2378 ` int(getAlignment)`
- L2382 ` void(setAlignment)`
- L2386 ` int(get_pdvper)`
- L2395 ` void(useSmiley)`
- L2408 ` void(boostStat)`
- L2411 `  (switch)`
- L2428 `  (if)`
- L2429 `  (switch)`
- L2459 ` void(boostStatFixedCount)`
- L2463 `  (switch)`
- L2478 `  (if)`
- L2479 `  (switch)`
- L2510 ` boolean(isMuted)`
- L2514 ` String(parseObjetsToDB)`
- L2519 `  (for)`
- L2528 ` void(addItem)`
- L2532 ` void(addItem)`
- L2535 `  (if)`
- L2539 ` boolean(addItem)`
- L2541 `  (synchronized)`
- L2542 `  (for)`
- L2543 `  (if)`
- L2545 `  (if)`
- L2548 `  (if)`
- L2559 ` void(addItem)`
- L2562 `  (if)`
- L2564 `  (if)`
- L2569 ` boolean(addObjetSimiler)`
- L2572 `  (if)`
- L2573 `  (for)`
- L2587 ` Map<Integer, GameObject>(getItems)`
- L2591 ` String(encodeItemASK)`
- L2596 `  (for)`
- L2601 ` String(getItemsIDSplitByChar)`
- L2606 `  (for)`
- L2614 ` String(getStoreItemsIDSplitByChar)`
- L2619 `  (for)`
- L2626 ` boolean(hasItemGuid)`
- L2630 ` void(sellItem)`
- L2641 `  (if)`
- L2657 ` void(removeItem)`
- L2659 `  (synchronized)`
- L2663 ` void(removeItem)`
- L2667 `  (synchronized)`
- L2675 `  (if)`
- L2678 `  (if)`
- L2684 `  (synchronized)`
- L2697 ` void(deleteItem)`
- L2699 `  (synchronized)`
- L2704 ` GameObject(getObjetByPos)`
- L2708 `  (synchronized)`
- L2709 `  (for)`
- L2710 `  (if)`
- L2722 ` GameObject(getObjetByPos2)`
- L2725 `  (for)`
- L2734 ` void(refreshStats)`
- L2741 ` boolean(levelUp)`
- L2756 `  (if)`
- L2762 ` boolean(addXp)`
- L2770 `  (if)`
- L2777 ` boolean(levelUpIncarnations)`
- L2787 `  (switch)`
- L2797 `  (if)`
- L2809 ` boolean(addXpIncarnations)`
- L2818 `  (while)`
- L2825 `  (while)`
- L2839 ` boolean(addKamas)`
- L2846 ` boolean(modKamasDisplay)`
- L2848 `  (if)`
- L2853 `  (if)`
- L2861 ` GameObject(getSimilarItem)`
- L2865 `  (synchronized)`
- L2884 ` int(learnJob)`
- L2886 `  (for)`
- L2912 `  (if)`
- L2932 ` boolean(unlearnJob)`
- L2943 `  (if)`
- L2949 ` void(unequipedObjet)`
- L2954 `  (if)`
- L2957 `  (for)`
- L2970 ` void(verifEquiped)`
- L2976 `  (if)`
- L2977 `  (if)`
- L2986 `  (if)`
- L2994 ` boolean(hasEquiped)`
- L3001 ` int(getInvitation)`
- L3005 ` void(setInvitation)`
- L3009 ` String(parseToPM)`
- L3017 `  (if)`
- L3034 ` int(getNumbEquipedItemOfPanoplie)`
- L3037 `  (for)`
- L3048 ` void(startActionOnCell)`
- L3066 `  (if)`
- L3074 ` void(finishActionOnCell)`
- L3092 ` void(teleportD)`
- L3099 ` void(teleportLaby)`
- L3129 `  (for)`
- L3137 ` void(teleport)`
- L3141 ` void(teleport)`
- L3145 ` void(teleport)`
- L3154 `  (if)`
- L3185 `  (if)`
- L3198 `  (if)`
- L3206 `  (if)`
- L3217 `  (for)`
- L3224 `  (if)`
- L3226 `  (if)`
- L3234 ` void(teleport)`
- L3244 `  (if)`
- L3245 `  (if)`
- L3247 `  (if)`
- L3249 `  (if)`
- L3263 `  (if)`
- L3274 `  (if)`
- L3293 `  (if)`
- L3303 `  (for)`
- L3311 ` void(disconnectInFight)`
- L3320 ` int(getBankCost)`
- L3324 ` void(openBank)`
- L3326 `  (if)`
- L3328 `  (if)`
- L3337 `  (if)`
- L3340 `  (if)`
- L3347 `  (if)`
- L3353 `  (if)`
- L3354 `  (if)`
- L3382 ` String(getStringVar)`
- L3384 `  (switch)`
- L3399 ` void(refreshMapAfterFight)`
- L3409 ` long(getBankKamas)`
- L3413 ` void(setBankKamas)`
- L3419 ` String(parseBankPacket)`
- L3428 ` void(addCapital)`
- L3432 ` void(addSpellPoint)`
- L3439 ` void(addInBank)`
- L3515 ` GameObject(getSimilarBankItem)`
- L3524 ` void(removeFromBank)`
- L3541 `  (if)`
- L3577 `  (if)`
- L3617 ` void(openMountPark)`
- L3618 `  (if)`
- L3624 `  (if)`
- L3626 `  (if)`
- L3627 `  (if)`
- L3638 `  (if)`
- L3640 `  (for)`
- L3648 `  (if)`
- L3651 `  (for)`
- L3654 `  (if)`
- L3662 `  (if)`
- L3663 `  (if)`
- L3664 `  (if)`
- L3677 ` void(fullPDV)`
- L3682 ` void(warpToSavePos)`
- L3690 ` boolean(removeItemByTemplateId)`
- L3702 `  (if)`
- L3705 `  (if)`
- L3715 `  (if)`
- L3716 `  (if)`
- L3724 `  (if)`
- L3726 `  (if)`
- L3732 `  (for)`
- L3742 `  (if)`
- L3743 `  (if)`
- L3759 ` List<Job>(getJobs)`
- L3763 ` Map<Integer, JobStat>(getMetiers)`
- L3767 ` void(useCraftSkill)`
- L3774 ` String(parseJobData)`
- L3779 `  (for)`
- L3788 ` int(totalJobBasic)`
- L3791 `  (for)`
- L3794 `  (if)`
- L3820 ` int(totalJobFM)`
- L3823 `  (for)`
- L3826 `  (if)`
- L3842 ` boolean(canAggro)`
- L3846 ` void(setCanAggro)`
- L3850 ` JobStat(getMetierBySkill)`
- L3857 ` String(parseToFriendList)`
- L3863 `  (if)`
- L3875 ` String(parseToEnemyList)`
- L3881 `  (if)`
- L3893 ` JobStat(getMetierByID)`
- L3900 ` boolean(isOnMount)`
- L3904 ` void(toogleOnMount)`
- L3908 `  (if)`
- L3914 `  (if)`
- L3918 `  (if)`
- L3927 `  (if)`
- L3936 `  (if)`
- L3941 `  (if)`
- L3948 `  (if)`
- L3958 ` int(getMountXpGive)`
- L3962 ` Mount(getMount)`
- L3966 ` void(setMount)`
- L3970 ` void(setMountGiveXp)`
- L3974 ` void(resetVars)`
- L3976 `  (if)`
- L4004 ` void(addChanel)`
- L4011 ` void(removeChanel)`
- L4016 ` void(modifAlignement)`
- L4026 ` int(getDeshonor)`
- L4030 ` void(setDeshonor)`
- L4034 ` void(setShowWings)`
- L4038 ` int(get_honor)`
- L4042 ` void(set_honor)`
- L4046 ` int(getALvl)`
- L4050 ` void(setALvl)`
- L4054 ` void(toggleWings)`
- L4056 `  (if)`
- L4062 `  (switch)`
- L4080 ` void(addHonor)`
- L4089 `  (if)`
- L4093 ` void(remHonor)`
- L4101 `  (if)`
- L4105 ` GuildMember(getGuildMember)`
- L4109 ` void(setGuildMember)`
- L4113 ` int(getAccID)`
- L4124 `  (if)`
- L4127 `  (for)`
- L4145 ` String(parsePrismesList)`
- L4149 `  (for)`
- L4155 `  (if)`
- L4166 ` void(openZaapMenu)`
- L4168 `  (if)`
- L4171 `  (if)`
- L4181 ` void(openTrunk)`
- L4184 `  (if)`
- L4192 ` void(verifAndAddZaap)`
- L4196 `  (if)`
- L4202 ` boolean(verifOtomaiZaap)`
- L4207 ` void(openPrismeMenu)`
- L4209 `  (if)`
- L4210 `  (if)`
- L4219 ` void(useZaap)`
- L4233 `  (if)`
- L4240 `  (if)`
- L4257 ` void(usePrisme)`
- L4263 `  (for)`
- L4275 `  (if)`
- L4285 ` String(parseZaaps)`
- L4292 `  (for)`
- L4300 ` String(parsePrism)`
- L4309 ` void(stopZaaping)`
- L4317 ` void(Zaapi_close)`
- L4324 ` void(Prisme_close)`
- L4331 ` void(Zaapi_use)`
- L4336 `  (if)`
- L4358 ` boolean(hasItemTemplate)`
- L4360 `  (for)`
- L4370 ` boolean(hasItemType)`
- L4372 `  (for)`
- L4381 ` GameObject(getItemTemplate)`
- L4383 `  (for)`
- L4393 ` GameObject(getItemTemplate)`
- L4395 `  (for)`
- L4404 ` int(getNbItemTemplate)`
- L4406 `  (for)`
- L4413 ` boolean(isDispo)`
- L4418 ` byte(getCurrentTitle)`
- L4422 ` void(setCurrentTitle)`
- L4428 ` void(VerifAndChangeItemPlace)`
- L4445 `  (for)`
- L4449 `  (if)`
- L4450 `  (if)`
- L4456 `  (if)`
- L4462 `  (if)`
- L4468 `  (if)`
- L4474 `  (if)`
- L4480 `  (if)`
- L4486 `  (if)`
- L4492 `  (if)`
- L4498 `  (if)`
- L4504 `  (if)`
- L4510 `  (if)`
- L4516 `  (if)`
- L4522 `  (if)`
- L4528 `  (if)`
- L4534 `  (if)`
- L4540 `  (if)`
- L4550 ` Stalk(getStalk)`
- L4554 ` void(setStalk)`
- L4558 ` void(setWife)`
- L4563 ` String(get_wife_friendlist)`
- L4567 `  (if)`
- L4570 `  (if)`
- L4576 `  (if)`
- L4586 ` String(parse_towife)`
- L4589 `  (if)`
- L4620 `  (if)`
- L4630 ` void(Divorce)`
- L4639 ` int(getWife)`
- L4643 ` int(setisOK)`
- L4647 ` int(getisOK)`
- L4651 ` List<GameObject>(getEquippedObjects)`
- L4654 `  (synchronized)`
- L4659 ` void(changeOrientation)`
- L4661 `  (if)`
- L4672 ` byte(isDead)`
- L4676 ` void(setDead)`
- L4680 ` short(getDeadLevel)`
- L4684 ` byte(getDeathCount)`
- L4688 ` void(increaseTotalKills)`
- L4692 ` long(getTotalKills)`
- L4696 ` String(getDeathInformation)`
- L4700 ` void(die)`
- L4709 ` void(revive)`
- L4712 `  (if)`
- L4744 `  (if)`
- L4765 ` boolean(isGhost)`
- L4769 ` void(setGhost)`
- L4773 ` void(setFuneral)`
- L4780 `  (if)`
- L4789 ` void(setGhost)`
- L4793 `  (if)`
- L4810 `  (if)`
- L4819 `  (if)`
- L4828 ` void(setAlive)`
- L4846 ` Map<Integer, Integer>(getStoreItems)`
- L4850 ` Fight(getLastFight)`
- L4854 ` void(setLastFightForEndFightAction)`
- L4859 ` void(setNeededEndFightAction)`
- L4864 ` boolean(applyEndFightAction)`
- L4866 `  (if)`
- L4873 ` String(parseStoreItemsList)`
- L4878 `  (for)`
- L4888 ` int(parseStoreItemsListPods)`
- L4893 `  (for)`
- L4895 `  (if)`
- L4903 ` String(parseStoreItemstoBD)`
- L4906 `  (for)`
- L4912 ` void(addInStore)`
- L4916 `  (if)`
- L4922 `  (if)`
- L4939 `  (if)`
- L4965 `  (if)`
- L4998 ` GameObject(getSimilarStoreItem)`
- L5000 `  (for)`
- L5008 ` void(removeFromStore)`
- L5012 `  (if)`
- L5022 `  (if)`
- L5034 `  (if)`
- L5049 ` void(removeStoreItem)`
- L5053 ` void(addStoreItem)`
- L5057 ` int(getSpeed)`
- L5061 ` void(setSpeed)`
- L5065 ` int(get_savestat)`
- L5069 ` void(set_savestat)`
- L5073 ` boolean(getMetierPublic)`
- L5077 ` void(setMetierPublic)`
- L5081 ` boolean(getLivreArtisant)`
- L5085 ` void(setLivreArtisant)`
- L5089 ` boolean(hasSpell)`
- L5093 ` void(leaveEnnemyFaction)`
- L5100 `  (switch)`
- L5103 `  (if)`
- L5114 `  (if)`
- L5125 `  (if)`
- L5136 `  (if)`
- L5147 `  (if)`
- L5158 `  (if)`
- L5169 `  (if)`
- L5180 `  (if)`
- L5191 `  (if)`
- L5202 `  (if)`
- L5215 ` void(leaveEnnemyFactionAndPay)`
- L5221 `  (switch)`
- L5223 `  (if)`
- L5241 `  (if)`
- L5259 `  (if)`
- L5277 `  (if)`
- L5295 `  (if)`
- L5313 `  (if)`
- L5331 `  (if)`
- L5349 `  (if)`
- L5367 `  (if)`
- L5385 `  (if)`
- L5406 ` void(leaveFaction)`
- L5420 `  (if)`
- L5423 `  (if)`
- L5427 `  (if)`
- L5431 `  (if)`
- L5441 `  (if)`
- L5458 `  (if)`
- L5466 `  (for)`
- L5474 ` void(teleportFaction)`
- L5480 `  (switch)`
- L5503 `  (if)`
- L5515 ` String(encodeColorsForMount)`
- L5521 ` Map<Integer, World.Couple<Integer, Integer>>(getObjectsClassSpell)`
- L5524 ` void(addObjectClassSpell)`
- L5526 `  (if)`
- L5530 ` void(removeObjectClassSpell)`
- L5532 `  (if)`
- L5536 ` void(addObjectClass)`
- L5541 ` void(removeObjectClass)`
- L5543 `  (if)`
- L5548 ` void(refreshObjectsClass)`
- L5552 `  (if)`
- L5556 `  (if)`
- L5559 `  (for)`
- L5575 ` int(getValueOfClassObject)`
- L5577 `  (if)`
- L5578 `  (if)`
- L5585 ` int(storeAllBuy)`
- L5588 `  (for)`
- L5597 ` void(DialogTimer)`
- L5602 `  (if)`
- L5610 `  (if)`
- L5631 ` long(getTimeTaverne)`
- L5635 ` void(setTimeTaverne)`
- L5640 ` GameAction(getGameAction)`
- L5644 ` void(setGameAction)`
- L5648 ` int(getAlignMap)`
- L5658 ` List<Integer>(getEmotes)`
- L5662 ` boolean(addStaticEmote)`
- L5674 ` String(parseEmoteToDB)`
- L5678 `  (for)`
- L5687 ` boolean(getBlockMovement)`
- L5691 ` void(setBlockMovement)`
- L5695 ` GameClient(getGameClient)`
- L5699 ` void(send)`
- L5703 ` void(sendMessage)`
- L5707 ` void(sendTypeMessage)`
- L5711 ` void(sendServerMessage)`
- L5715 ` boolean(isSubscribe)`
- L5719 ` boolean(isMissingSubscription)`
- L5725 `  (switch)`
- L5742 ` boolean(cantDefie)`
- L5746 ` boolean(cantAgro)`
- L5750 ` boolean(cantCanal)`
- L5754 ` boolean(cantTP)`
- L5758 ` boolean(isInPrison)`
- L5762 `  (switch)`
- L5770 ` void(addQuestProgression)`
- L5774 ` void(delQuestProgress)`
- L5778 ` QuestProgress(getQuestProgress)`
- L5782 ` Optional<QuestProgress>(getQuestProgressForCurrentStep)`
- L5786 ` Stream<QuestProgress>(getQuestProgressions)`
- L5791 ` void(sendQuestStatus)`
- L5794 `  (if)`
- L5801 `  (if)`
- L5820 `  (if)`
- L5826 ` String(encodeQuestList)`
- L5841 ` void(saveQuestProgress)`
- L5845 ` House(getInHouse)`
- L5849 ` void(setInHouse)`
- L5855 ` ExchangeAction<?>(getExchangeAction)`
- L5859 `synchronized void(setExchangeAction)`
- L5864 ` void(refreshCraftSecure)`
- L5867 `  (for)`
- L5871 `  (if)`
- L5872 `  (if)`
- L5881 `  (for)`
- L5898 `  (for)`
- L5905 ` void(setFullMorphbouf)`
- L5911 `  (if)`
- L5942 `  (if)`
- L5969 ` void(unsetFullMorphbouf)`
- L6010 ` LangEnum(getLang)`
- L6017 ` r(scripted)`
- L6022 ` boolean(consumeCurrency)`
- L6029 ` boolean(moveItemShortcutSend)`
- L6036 ` boolean(addItemHashShortcutSend)`
- L6051 ` boolean(addItemShortcutSend)`
- L6060 ` boolean(removeItemShortcutSend)`
- L6062 `  (if)`
- L6069 ` Optional<Integer>(removeItemShortcutByHash)`
- L6076 ` void(sendItemShortcuts)`

#### `org/starloco/locos/client/other/Party.java` — 165 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Party
Fonctions :
- L16 ` public(Party)`
- L22 ` Player(getChief)`
- L25 ` void(setChief)`
- L29 ` boolean(isChief)`
- L33 ` Player(getMaster)`
- L37 ` void(setMaster)`
- L41 ` ArrayList<Player>(getPlayers)`
- L45 ` void(addPlayer)`
- L49 ` void(leave)`
- L57 `  (for)`
- L62 `  (if)`
- L71 `  (for)`
- L78 ` void(moveAllPlayersToMaster)`
- L80 `  (if)`
- L86 `  (if)`
- L89 `  (if)`
- L91 `  (if)`
- L95 `  (if)`
- L110 ` boolean(isWithTheMaster)`
- L117 ` boolean(haveSameIp)`
- L123 ` MasterOption(getOptionByPlayer)`
- L125 `  (if)`
- L135 ` ArrayList<MasterOption>(getOptions)`
- L144 ` public(MasterOption)`
- L148 ` void(setSecond)`
- L152 ` byte(getSecond)`
- L156 ` void(togglePass)`
- L160 ` boolean(passAuto)`

#### `org/starloco/locos/client/other/Restriction.java` — 20 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Restriction
Fonctions :
- L12 `static Restriction(get)`

#### `org/starloco/locos/client/other/SpellHelper.java` — 10 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class SpellHelper
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/client/other/Stalk.java` — 56 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Stalk
Fonctions :
- L11 ` public(Stalk)`
- L16 ` Player(getTarget)`
- L20 ` void(setTarget)`
- L24 ` long(getTime)`
- L28 ` void(setTime)`
- L32 ` boolean(onPlayerTryToFight)`
- L38 `  (if)`
- L44 `  (if)`

#### `org/starloco/locos/client/other/Stats.java` — 285 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Stats
Fonctions :
- L15 ` public(Stats)`
- L19 ` public(Stats)`
- L21 `  (if)`
- L30 ` public(Stats)`
- L33 `  (if)`
- L42 ` public(Stats)`
- L51 ` public(Stats)`
- L54 ` public(Stats)`
- L56 `  (if)`
- L72 ` Map<Integer, Integer>(getEffects)`
- L76 ` int(get)`
- L80 ` int(addOneStat)`
- L83 `  (if)`
- L88 `  (if)`
- L96 ` boolean(isSameStats)`
- L98 `  (for)`
- L106 `  (for)`
- L116 ` String(encodeItemSetStats)`
- L121 `  (for)`
- L128 ` int(getEffect)`
- L131 `  (switch)`
- L251 `static Stats(cumulStat)`
- L268 `static Stats(cumulStatFight)`

#### `org/starloco/locos/command/CommandAdmin.java` — 3129 lignes
Rôle : Commandes administrateur : spawn, item, reload, téléport, stats, animation, reboot.
Classe(s) : class CommandAdmin extends AdminUser
Fonctions :
- L51 ` public(CommandAdmin)`
- L55 ` void(apply)`
- L65 `  (if)`
- L69 `  (if)`
- L80 ` void(command)`
- L82 `  (if)`
- L94 `  (if)`
- L97 `  (for)`
- L104 `  (for)`
- L114 ` else(if)`
- L126 `  (if)`
- L130 `  (if)`
- L142 `  (if)`
- L148 `  (if)`
- L167 `  (if)`
- L180 `  (if)`
- L192 `  (if)`
- L197 `  (if)`
- L210 `  (if)`
- L215 `  (if)`
- L228 `  (if)`
- L233 `  (if)`
- L263 `  (for)`
- L283 `  (if)`
- L295 `  (for)`
- L320 `  (for)`
- L342 `  (if)`
- L352 `  (if)`
- L357 `  (if)`
- L366 `  (if)`
- L372 `  (if)`
- L394 `  (if)`
- L404 `  (if)`
- L413 `  (if)`
- L418 `  (if)`
- L434 `  (if)`
- L443 `  (if)`
- L458 `  (if)`
- L461 `  (if)`
- L479 `  (if)`
- L496 `  (if)`
- L517 `  (if)`
- L531 `  (if)`
- L536 `  (if)`
- L562 `  (if)`
- L567 `  (if)`
- L574 `  (if)`
- L600 `  (if)`
- L605 `  (if)`
- L612 `  (if)`
- L638 `  (if)`
- L643 `  (if)`
- L693 `  (if)`
- L698 `  (if)`
- L700 `  (if)`
- L714 `  (if)`
- L723 `  (if)`
- L732 `  (if)`
- L737 `  (if)`
- L758 `  (if)`
- L781 `  (if)`
- L788 `  (if)`
- L795 `  (if)`
- L812 `  (if)`
- L816 `  (for)`
- L825 `  (if)`
- L826 `  (if)`
- L828 `  (if)`
- L849 `  (if)`
- L854 `  (for)`
- L857 `  (if)`
- L860 `  (if)`
- L868 `  (if)`
- L887 `  (if)`
- L892 `  (for)`
- L902 `  (if)`
- L922 `  (if)`
- L928 `  (if)`
- L934 `  (for)`
- L942 `  (if)`
- L965 `  (if)`
- L973 `  (for)`
- L995 `  (if)`
- L1003 `  (for)`
- L1024 `  (if)`
- L1031 `  (for)`
- L1051 `  (if)`
- L1058 `  (if)`
- L1059 `  (for)`
- L1076 `  (if)`
- L1103 `  (if)`
- L1113 `  (if)`
- L1119 `  (if)`
- L1138 `  (for)`
- L1155 `  (if)`
- L1163 `  (if)`
- L1183 `  (if)`
- L1191 `  (if)`
- L1209 `  (if)`
- L1221 `  (if)`
- L1240 `  (if)`
- L1253 `  (for)`
- L1255 `  (for)`
- L1257 `  (if)`
- L1269 `  (if)`
- L1302 `  (for)`
- L1312 `  (if)`
- L1340 `  (if)`
- L1375 `  (if)`
- L1425 `  (for)`
- L1426 `  (switch)`
- L1445 `  (if)`
- L1454 `  (if)`
- L1466 `  (if)`
- L1489 `  (if)`
- L1501 `  (for)`
- L1532 `  (for)`
- L1538 `  (if)`
- L1546 `  (for)`
- L1562 `  (if)`
- L1571 `  (if)`
- L1577 `  (if)`
- L1609 `  (if)`
- L1614 `  (if)`
- L1636 `  (if)`
- L1671 `  (if)`
- L1679 `  (for)`
- L1698 `  (if)`
- L1721 `  (if)`
- L1735 `  (if)`
- L1746 `  (if)`
- L1769 `  (if)`
- L1779 `  (if)`
- L1792 `  (switch)`
- L1830 `  (switch)`
- L1835 `  (for)`
- L1843 `  (if)`
- L1861 `  (for)`
- L1886 `  (if)`
- L1897 `  (if)`
- L1930 `  (switch)`
- L1933 `  (if)`
- L1941 `  (if)`
- L1953 `  (switch)`
- L1997 `  (if)`
- L2007 `  (if)`
- L2025 `  (if)`
- L2035 `  (if)`
- L2054 `  (if)`
- L2065 `  (if)`
- L2122 `  (if)`
- L2132 `  (if)`
- L2139 `  (if)`
- L2160 `  (if)`
- L2170 `  (if)`
- L2186 `  (if)`
- L2202 `  (if)`
- L2223 `  (if)`
- L2224 `  (for)`
- L2251 `  (for)`
- L2282 `  (if)`
- L2286 `  (if)`
- L2290 `  (if)`
- L2294 `  (if)`
- L2296 `  (if)`
- L2320 `  (if)`
- L2323 `  (if)`
- L2333 `  (for)`
- L2342 `  (if)`
- L2347 `  (if)`
- L2369 `  (if)`
- L2404 `  (for)`
- L2407 `  (if)`
- L2432 `  (if)`
- L2446 `  (if)`
- L2463 `  (if)`
- L2492 `  (if)`
- L2515 `  (if)`
- L2520 `  (if)`
- L2524 `  (if)`
- L2534 `  (if)`
- L2586 `  (if)`
- L2589 `  (if)`
- L2594 `  (if)`
- L2601 `  (if)`
- L2629 `  (if)`
- L2646 `  (if)`
- L2667 `  (if)`
- L2696 `  (for)`
- L2736 `  (if)`
- L2754 `  (if)`
- L2756 `  (if)`
- L2758 `  (if)`
- L2768 `  (if)`
- L2770 `  (for)`
- L2774 `  (if)`
- L2776 `  (if)`
- L2804 `  (if)`
- L2812 `  (if)`
- L2816 `  (for)`
- L2828 `  (for)`
- L2844 `  (if)`
- L2880 `  (if)`
- L2886 `  (if)`
- L2892 `  (if)`
- L2908 `  (if)`
- L2915 `  (if)`
- L2920 `  (if)`
- L2926 `  (if)`
- L2942 `  (if)`
- L2949 `  (if)`
- L2954 `  (if)`
- L2958 `  (for)`
- L2974 `  (if)`
- L2981 `  (if)`
- L2986 `  (if)`
- L2990 `  (for)`
- L3008 `  (if)`
- L3014 `  (if)`
- L3018 `  (for)`
- L3020 `  (for)`
- L3040 `  (if)`
- L3052 `  (for)`
- L3083 `static int(getCellJail)`
- L3085 `  (switch)`
- L3098 `static String(returnClasse)`
- L3100 `  (switch)`

#### `org/starloco/locos/command/CommandPlayer.java` — 531 lignes
Rôle : Joueur/personnage : stats, inventaire, sorts, XP, level-up, banque, monture/familier, échanges, groupes, paquets perso.
Classe(s) : class CommandPlayer
Fonctions :
- L26 `static boolean(analyse)`
- L29 `  (if)`
- L30 `  (if)`
- L61 `  (if)`
- L74 `  (if)`
- L75 `  (if)`
- L113 `static boolean(commandPass)`
- L115 `  (if)`
- L117 `  (if)`
- L127 `static boolean(commandInterval)`
- L129 `  (if)`
- L131 `  (if)`
- L134 `  (for)`
- L135 `  (if)`
- L159 `static boolean(commandTransfert)`
- L163 `  (if)`
- L167 `  (if)`
- L181 `  (if)`
- L186 `  (switch)`
- L219 `  (switch)`
- L233 `  (if)`
- L246 `static boolean(commandTransfertWithMaster)`
- L249 `  (if)`
- L256 `  (if)`
- L259 `  (switch)`
- L281 `  (for)`
- L291 `static boolean(commandStart)`
- L300 `  (for)`
- L301 `  (if)`
- L311 `static boolean(commandAstrub)`
- L321 `  (if)`
- L330 `static boolean(commandMaster)`
- L333 `  (if)`
- L337 `  (if)`
- L339 `  (if)`
- L355 `  (if)`
- L367 `  (if)`
- L377 `  (if)`
- L382 `  (if)`
- L387 `  (if)`
- L404 `static boolean(commandHelp)`
- L411 `static boolean(commandAll)`
- L415 `  (if)`
- L419 `  (if)`
- L423 `  (if)`
- L440 `static boolean(commandNoAll)`
- L442 `  (if)`
- L451 `static boolean(commandDeblo)`
- L457 `  (if)`
- L464 `static boolean(commandInfos)`
- L483 `static boolean(commandStaff)`
- L487 `  (for)`
- L501 `static boolean(command)`
- L506 `static String(getNameServerById)`
- L508 `  (switch)`

#### `org/starloco/locos/command/administration/AdminUser.java` — 95 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class AdminUser
Fonctions :
- L22 ` public(AdminUser)`
- L24 `  (if)`
- L30 ` Account(getAccount)`
- L34 ` Player(getPlayer)`
- L38 ` GameClient(getClient)`
- L42 ` boolean(isTimerStart)`
- L46 ` void(setTimerStart)`
- L50 ` Timer(getTimer)`
- L54 ` void(setTimer)`
- L58 ` Timer(createTimer)`
- L62 ` void(actionPerformed)`
- L74 ` void(sendMessage)`
- L78 ` void(sendErrorMessage)`
- L82 ` void(sendSuccessMessage)`
- L86 ` String(buildBAT)`

#### `org/starloco/locos/command/administration/Command.java` — 32 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Command
Fonctions :
- L11 ` public(Command)`
- L19 ` String(getName)`
- L23 ` String(getArgs)`
- L27 ` String(getDesc)`

#### `org/starloco/locos/command/administration/Group.java` — 60 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Group
Fonctions :
- L15 ` public(Group)`
- L24 ` int(getId)`
- L28 ` String(getName)`
- L32 ` boolean(isPlayer)`
- L36 ` List<Command>(getCommands)`
- L41 ` boolean(haveCommand)`
- L51 `static Group(byId)`
- L55 `static Collection<Group>(getGroups)`

#### `org/starloco/locos/common/ConditionParser.java` — 587 lignes
Rôle : Évaluation conditions Dofus : stats, classe, quête, alignement, map, niveau.
Classe(s) : class ConditionParser
Fonctions :
- L21 ` boolean(validConditions)`
- L119 ` boolean(haveMorph)`
- L134 ` boolean(haveMetier)`
- L138 `  (for)`
- L144 ` boolean(havePj)`
- L148 `  (for)`
- L164 ` boolean(haveQa)`
- L176 ` boolean(haveQEt)`
- L183 ` boolean(haveTiT)`
- L185 `  (if)`
- L187 `  (if)`
- L191 `  (if)`
- L201 ` boolean(haveTi)`
- L203 `  (if)`
- L205 `  (if)`
- L209 `  (if)`
- L218 ` boolean(haveCe)`
- L232 `  (if)`
- L246 ` boolean(haveQE)`
- L257 ` boolean(haveQT)`
- L267 ` boolean(haveNPC)`
- L269 `  (switch)`
- L283 ` boolean(haveRO)`
- L286 `  (for)`
- L289 `  (if)`
- L303 ` boolean(haveRA)`
- L306 `  (for)`
- L326 `  (if)`
- L328 `  (for)`
- L329 `  (if)`
- L330 `  (for)`
- L331 `  (if)`
- L335 `  (if)`
- L340 `  (if)`
- L356 `  (if)`
- L357 `  (for)`
- L358 `  (if)`
- L362 `  (if)`
- L367 `  (if)`
- L386 `  (for)`
- L387 `  (if)`
- L388 `  (for)`
- L389 `  (if)`
- L393 `  (if)`
- L398 `  (if)`
- L414 `  (if)`
- L415 `  (for)`
- L416 `  (if)`
- L420 `  (if)`
- L425 `  (if)`
- L445 `  (if)`
- L446 `  (for)`
- L458 `  (if)`
- L459 `  (for)`
- L472 `  (if)`
- L482 `  (for)`
- L483 `  (if)`
- L494 ` boolean(haveDV)`
- L502 `  (if)`
- L509 `  (if)`
- L510 `  (for)`
- L537 ` String(haveJOB)`
- L546 ` boolean(stackIfSimilar)`
- L550 `  (switch)`
- L561 `  (for)`
- L564 `  (for)`

#### `org/starloco/locos/common/CryptManager.java` — 195 lignes
Rôle : Encodage/décodage Dofus : hash, cellules, IP, packet utils.
Classe(s) : class CryptManager
Fonctions :
- L18 `static String(cellID_To_Code)`
- L22 ` int(cellCode_To_ID)`
- L27 `  (while)`
- L39 `static int(getIntByHashedValue)`
- L46 `static char(getHashedValueByInt)`
- L50 ` String(prepareMapDataKey)`
- L54 `  (while)`
- L62 `static String(unescape)`
- L66 `static String(checksumKey)`
- L71 `  (while)`
- L79 `static String(decryptMapData)`
- L87 `static String(decypherData)`
- L92 `  (while)`
- L102 `static boolean(isMapCiphered)`
- L110 ` String(cryptMessage)`
- L128 ` String(decryptMessage)`
- L152 `static String(prepareKey)`
- L160 ` int(checksum)`
- L167 ` String(decimalToHexadecimal)`
- L172 ` String(encode)`
- L175 `  (for)`
- L176 `  (if)`
- L186 ` char(toHex)`
- L190 ` boolean(isUnsafe)`

#### `org/starloco/locos/common/Formulas.java` — 1358 lignes
Rôle : Formules de jeu : xp, drops, dégâts, tacles, prospection, jets aléatoires.
Classe(s) : class Formulas
Fonctions :
- L31 `static int(nextGaussian)`
- L39 `static char[](shuffleCharArray)`
- L55 `static int(countCell)`
- L63 `static int(getRandomValue)`
- L68 `static int(getMinJet)`
- L81 `static int(getMaxJet)`
- L101 `  (if)`
- L104 `  (if)`
- L113 `  (if)`
- L143 `static int(getTacleChance)`
- L153 `static int(calculFinalHeal)`
- L161 `static int(calculFinalHealCac)`
- L172 `static int(calculXpWinCraft)`
- L176 `  (switch)`
- L212 `static int(calculXpWinFm)`
- L214 `  (if)`
- L222 `  (if)`
- L282 `static int(calculXpLooseCraft)`
- L286 `  (switch)`
- L322 `static int(calculFinalDommage)`
- L328 `static int(calculFinalDommagee)`
- L338 `  (if)`
- L339 `  (if)`
- L343 `  (if)`
- L353 `  (switch)`
- L430 `  (if)`
- L436 `  (if)`
- L459 `  (if)`
- L465 `  (if)`
- L466 `  (switch)`
- L472 `  (if)`
- L488 `  (if)`
- L502 `  (if)`
- L513 `  (if)`
- L515 `  (if)`
- L524 `  (if)`
- L531 `  (if)`
- L542 `  (if)`
- L560 `static int(calculZaapCost)`
- L565 `static int(applyResistancesOnDamage)`
- L571 `  (if)`
- L576 `  (if)`
- L586 `static int(getArmorResist)`
- L589 `  (for)`
- L591 `  (switch)`
- L633 `  (switch)`
- L653 `  (for)`
- L656 `  (switch)`
- L678 `static long(getGuildXpWin)`
- L695 `  (if)`
- L711 `static long(getMountXpWin)`
- L747 `static int(getKamasWin)`
- L754 `static int(getKamasWinPerco)`
- L760 `static Couple<Integer, Integer>(decompPierreAme)`
- L770 `static int(totalCaptChance)`
- L773 `  (switch)`
- L797 `static int(spellCost)`
- L806 `static int(getLoosEnergy)`
- L816 `static int(totalAppriChance)`
- L821 `  (switch)`
- L847 `static int(getCouleur)`
- L856 `  (if)`
- L864 `  (if)`
- L871 `  (if)`
- L878 `  (if)`
- L889 `static int(calculEnergieLooseForToogleMount)`
- L932 `static int(calculChanceByElement)`
- L944 `static ArrayList<Integer>(chanceFM)`
- L977 `  (if)`
- L986 `  (if)`
- L1000 `static String(convertToDate)`
- L1021 `static int(getXpStalk)`
- L1023 `  (switch)`
- L1199 `static String(translateMsg)`
- L1211 `static int(calculHonorWin)`
- L1226 `  (if)`
- L1246 `  (switch)`
- L1267 `static int(countFightersLevel)`
- L1273 `static int(countFightersGrade)`
- L1282 `static int(getPointsLost)`
- L1311 `static boolean(checkLos)`
- L1347 `  (if)`

#### `org/starloco/locos/common/PathFinding.java` — 1239 lignes
Rôle : Pathfinding/cellules : déplacements, portée, ligne de vue, distances, push/pull, cases libres.
Classe(s) : class PathFinding
Fonctions :
- L20 `static int(isValidPath)`
- L27 `  (synchronized)`
- L39 `  (if)`
- L44 `  (if)`
- L45 `  (for)`
- L54 `  (if)`
- L79 `static ArrayList<Fighter>(getEnemyFighterArround)`
- L85 `  (for)`
- L88 `  (if)`
- L90 `  (if)`
- L103 `static boolean(isNextTo)`
- L114 `static String(ValidSinglePath)`
- L123 `  (if)`
- L133 `  (if)`
- L137 `  (if)`
- L143 `  (if)`
- L152 `  (if)`
- L154 `  (if)`
- L174 `  (if)`
- L192 `  (for)`
- L201 `static ArrayList<Integer>(getAllCaseIdAllDirrection)`
- L206 `  (for)`
- L213 `static int(GetCaseIDFromDirection)`
- L219 `  (switch)`
- L249 `static int(getDistanceBetween)`
- L260 `static Fighter(getEnemyAround)`
- L263 `  (for)`
- L276 `static List<Fighter>(getEnemiesAround)`
- L281 `  (if)`
- L291 `static int(newCaseAfterPush)`
- L299 `  (if)`
- L312 `  (for)`
- L328 `static int(newCaseAfterPush)`
- L337 `  (if)`
- L351 `  (for)`
- L369 `static int(getDistanceBetweenTwoCase)`
- L372 `  (if)`
- L379 `  (while)`
- L382 `  (if)`
- L389 `static char(getOpositeDirection)`
- L391 `  (switch)`
- L411 `static int(getCaseBetweenEnemy)`
- L416 `  (for)`
- L429 `static int(getAvailableCellArround)`
- L433 `  (for)`
- L437 `  (if)`
- L440 `  (if)`
- L449 `static int(getNearestligneGA)`
- L463 `  (for)`
- L479 `  (for)`
- L496 `  (for)`
- L510 `  (while)`
- L525 `  (for)`
- L542 `  (for)`
- L563 `static boolean(casesAreInSameLine)`
- L567 `  (if)`
- L581 `static boolean(casesAreInSameLine)`
- L599 `  (for)`
- L610 `static ArrayList<Fighter>(getCiblesByZoneByWeapon)`
- L615 `  (if)`
- L621 `  (switch)`
- L668 `static Fighter(get1StFighterOnCellFromDirection)`
- L677 `static Fighter(getFighter2CellBefore)`
- L683 `static char(getDirBetweenTwoCase)`
- L691 `  (if)`
- L697 `  (for)`
- L707 `static boolean(canWalkToThisCell)`
- L716 `static List<GameCase>(getCellListFromAreaString)`
- L730 `  (switch)`
- L736 `  (for)`
- L738 `  (for)`
- L751 `  (for)`
- L780 `  (if)`
- L809 `static int(getCellXCoord)`
- L816 `static int(getCellYCoord)`
- L824 `static int(getNearestCellAround)`
- L834 `  (if)`
- L837 `  (if)`
- L847 `static int(getNearestCellAroundGA)`
- L856 `  (for)`
- L872 `static ArrayList<GameCase>(getShortestPathBetween)`
- L883 `  (while)`
- L886 `  (if)`
- L888 `  (if)`
- L914 `  (if)`
- L917 `  (while)`
- L920 `  (if)`
- L922 `  (if)`
- L953 `static String(getShortestStringPathBetween)`
- L964 `  (for)`
- L968 `  (if)`
- L976 `  (if)`
- L984 `static boolean(checkLoS)`
- L991 `  (if)`
- L994 `  (for)`
- L1003 `static ArrayList<Integer>(getLoSBotheringIDCases)`
- L1018 `  (while)`
- L1025 `  (if)`
- L1063 `  (if)`
- L1084 `static ArrayList<Integer>(getLoSBotheringCasesInDiagonal)`
- L1098 `  (while)`
- L1107 `static ArrayList<Fighter>(getFightersAround)`
- L1111 `  (for)`
- L1121 `static char(getDirEntreDosCeldas)`
- L1131 `  (if)`
- L1143 `static int(getCellArroundByDir)`
- L1147 `  (switch)`
- L1160 `static GameCase(checkIfCanPushEntity)`
- L1167 `  (while)`
- L1173 `  (for)`
- L1185 `static boolean(haveFighterOnThisCell)`
- L1187 `  (for)`
- L1193 `static int(getCaseIDFromDirrection)`
- L1196 `  (switch)`
- L1208 `static boolean(cellArroundCaseIDisOccuped)`
- L1212 `  (for)`
- L1226 `static List<GameCase>(getCellsByDir)`
- L1231 `  (if)`

#### `org/starloco/locos/common/SocketManager.java` — 2164 lignes
Rôle : Constructeur central de packets réseau envoyés au client.
Classe(s) : class SocketManager
Fonctions :
- L42 `static void(send)`
- L47 `static void(send)`
- L49 `  (if)`
- L59 `static void(MULTI_SEND_Af_PACKET)`
- L63 `static void(GAME_SEND_ATTRIBUTE_FAILED)`
- L68 `static void(GAME_SEND_AV0)`
- L73 `static void(GAME_SEND_PERSO_LIST)`
- L87 `static void(GAME_SEND_NAME_ALREADY_EXIST)`
- L92 `static void(GAME_SEND_CREATE_PERSO_FULL)`
- L97 `static void(GAME_SEND_CREATE_OK)`
- L102 `static void(GAME_SEND_DELETE_PERSO_FAILED)`
- L107 `static void(GAME_SEND_CREATE_FAILED)`
- L112 `static void(GAME_SEND_PERSO_SELECTION_FAILED)`
- L117 `static void(GAME_SEND_STATS_PACKET)`
- L123 `static void(GAME_SEND_Rx_PACKET)`
- L128 `static void(GAME_SEND_Rn_PACKET)`
- L133 `static void(GAME_SEND_UPDATE_OBJECT_DISPLAY_PACKET)`
- L138 `static void(GAME_SEND_Re_PACKET)`
- L147 `static void(GAME_SEND_ASK)`
- L152 `  (if)`
- L153 `  (if)`
- L171 `static void(GAME_SEND_ALIGNEMENT)`
- L176 `static void(GAME_SEND_ADD_CANAL)`
- L181 `static void(GAME_SEND_ZONE_ALLIGN_STATUT)`
- L186 `static void(GAME_SEND_RESTRICTIONS)`
- L191 `static void(GAME_SEND_Ow_PACKET)`
- L196 `static void(GAME_SEND_OT_PACKET)`
- L203 `static void(GAME_SEND_SEE_FRIEND_CONNEXION)`
- L209 `static void(GAME_SEND_GAME_CREATE)`
- L214 `static void(GAME_SEND_MAPDATA)`
- L220 `static void(GAME_SEND_GDK_PACKET)`
- L225 `static void(GAME_SEND_MAP_MOBS_GMS_PACKETS)`
- L232 `static void(GAME_SEND_MAP_OBJECTS_GDS_PACKETS)`
- L239 `static void(GAME_SEND_MAP_NPCS_GMS_PACKETS)`
- L248 `static void(GAME_SEND_MAP_PERCO_GMS_PACKETS)`
- L255 `static void(GAME_SEND_ERASE_ON_MAP_TO_MAP)`
- L260 `  (for)`
- L267 `static void(GAME_SEND_ON_FIGHTER_KICK)`
- L270 `  (for)`
- L278 `static void(GAME_SEND_ALTER_FIGHTER_MOUNT)`
- L283 `  (for)`
- L290 `  (if)`
- L291 `  (for)`
- L300 `static void(GAME_SEND_ADD_PLAYER_TO_MAP)`
- L303 `  (for)`
- L310 `static void(GAME_SEND_DUEL_Y_AWAY)`
- L315 `static void(GAME_SEND_DUEL_E_AWAY)`
- L320 `static void(GAME_SEND_MAP_NEW_DUEL_TO_MAP)`
- L327 `static void(GAME_SEND_CANCEL_DUEL_TO_MAP)`
- L333 `static void(GAME_SEND_MAP_START_DUEL_TO_MAP)`
- L340 `static void(GAME_SEND_MAP_FIGHT_COUNT)`
- L345 `static void(GAME_SEND_FIGHT_GJK_PACKET_TO_FIGHT)`
- L351 `  (for)`
- L357 `static void(GAME_SEND_FIGHT_PLACES_PACKET_TO_FIGHT)`
- L362 `  (for)`
- L370 `static void(GAME_SEND_MAP_FIGHT_COUNT_TO_MAP)`
- L376 `static void(GAME_SEND_GAME_ADDFLAG_PACKET_TO_MAP)`
- L384 `static void(GAME_SEND_GAME_ADDFLAG_PACKET_TO_PLAYER)`
- L390 `static void(GAME_SEND_GAME_REMFLAG_PACKET_TO_MAP)`
- L396 `static void(GAME_SEND_ADD_IN_TEAM_PACKET_TO_MAP)`
- L401 `  (for)`
- L405 `static void(GAME_SEND_ADD_IN_TEAM_PACKET_TO_PLAYER)`
- L409 `static void(GAME_SEND_REFRESH_TEAM_PACKET_TO_MAP)`
- L417 `static void(GAME_SEND_REMOVE_IN_TEAM_PACKET_TO_MAP)`
- L426 `static void(GAME_SEND_MAP_MOBS_GMS_PACKETS_TO_MAP)`
- L432 `static void(GAME_SEND_MAP_MOBS_GM_PACKET)`
- L440 `static void(GAME_SEND_MAP_GMS_PACKETS)`
- L450 `static void(GAME_SEND_ON_EQUIP_ITEM)`
- L456 `static void(GAME_SEND_ON_EQUIP_ITEM_FIGHT)`
- L471 `static void(GAME_SEND_FIGHT_CHANGE_PLACE_PACKET_TO_FIGHT)`
- L474 `  (for)`
- L482 `static void(GAME_SEND_Ew_PACKET)`
- L487 `static void(GAME_SEND_EL_MOUNT_PACKET)`
- L492 `static void(GAME_SEND_GM_MOUNT_TO_MAP)`
- L498 `static void(GAME_SEND_GDO_OBJECT_TO_MAP)`
- L505 `static void(GAME_SEND_GM_MOUNT)`
- L512 `static void(GAME_SEND_Ef_MOUNT_TO_ETABLE)`
- L518 `static void(GAME_SEND_GA_ACTION_TO_MAP)`
- L527 `static void(SEND_GDO_PUT_OBJECT_MOUNT)`
- L533 `static void(SEND_GDE_FRAME_OBJECT_EXTERNAL)`
- L539 `static void(GAME_SEND_FIGHT_CHANGE_OPTION_PACKET_TO_MAP)`
- L546 `static void(GAME_SEND_FIGHT_PLAYER_READY_TO_FIGHT)`
- L552 `  (for)`
- L560 `static void(GAME_SEND_GJK_PACKET)`
- L566 `static void(GAME_SEND_FIGHT_PLACES_PACKET)`
- L575 `static void(GAME_SEND_Im_PACKET_TO_ALL)`
- L581 `static void(GAME_SEND_Im_PACKET)`
- L586 `static void(GAME_SEND_TUTORIAL_CREATE)`
- L590 `static void(GAME_SEND_Im_PACKET_TO_MAP)`
- L596 `static void(GAME_SEND_eUK_PACKET_TO_MAP)`
- L602 `static void(GAME_SEND_Im_PACKET_TO_FIGHT)`
- L606 `  (for)`
- L614 `static void(GAME_SEND_MESSAGE)`
- L619 `static void(GAME_SEND_MESSAGE)`
- L625 `static void(GAME_SEND_MESSAGE_TO_MAP)`
- L632 `static void(GAME_SEND_GA903_ERROR_PACKET)`
- L638 `static void(GAME_SEND_GIC_PACKETS_TO_FIGHT)`
- L642 `  (for)`
- L651 `static void(GAME_SEND_GIC_PACKET_TO_FIGHT)`
- L657 `static void(GAME_SEND_GS_PACKET_TO_FIGHT)`
- L660 `  (for)`
- L668 `static void(GAME_SEND_GS_PACKET)`
- L673 `static void(GAME_SEND_GTL_PACKET_TO_FIGHT)`
- L675 `  (for)`
- L683 `static void(GAME_SEND_GTL_PACKET)`
- L688 `static void(GAME_SEND_GTM_PACKET_TO_FIGHT)`
- L692 `  (for)`
- L694 `  (if)`
- L705 `  (for)`
- L713 `static void(GAME_SEND_GAMETURNSTART_PACKET_TO_FIGHT)`
- L716 `  (for)`
- L725 `static void(GAME_SEND_GAMETURNSTART_PACKET)`
- L731 `static void(GAME_SEND_GV_PACKET)`
- L736 `static void(GAME_SEND_GAS_PACKET_TO_FIGHT)`
- L739 `  (for)`
- L747 `static void(GAME_SEND_GA_PACKET_TO_FIGHT)`
- L753 `  (for)`
- L761 `static void(GAME_SEND_GA_PACKET)`
- L768 `static void(SEND_SB_SPELL_BOOST)`
- L773 `static void(GAME_SEND_GA_PACKET)`
- L784 `static void(GAME_SEND_GA_PACKET_TO_FIGHT)`
- L787 `  (for)`
- L795 `static void(GAME_SEND_GAMEACTION_TO_FIGHT)`
- L797 `  (for)`
- L805 `static void(GAME_SEND_GAF_PACKET_TO_FIGHT)`
- L808 `  (for)`
- L814 `static void(GAME_SEND_BN)`
- L819 `static void(GAME_SEND_BN)`
- L824 `static void(GAME_SEND_GAMETURNSTOP_PACKET_TO_FIGHT)`
- L827 `  (for)`
- L836 `static void(GAME_SEND_GTR_PACKET_TO_FIGHT)`
- L839 `  (for)`
- L847 `static void(GAME_SEND_EMOTICONE_TO_MAP)`
- L853 `static void(GAME_SEND_SPELL_UPGRADE_FAILED)`
- L858 `static void(GAME_SEND_SPELL_UPGRADE_SUCCESS)`
- L863 `static void(GAME_SEND_SPELL_LIST)`
- L868 `static void(GAME_SEND_FIGHT_PLAYER_DIE_TO_FIGHT)`
- L871 `  (for)`
- L878 `static void(GAME_SEND_MAP_FIGHT_GMS_PACKETS_TO_FIGHT)`
- L882 `  (for)`
- L890 `static void(GAME_SEND_MAP_FIGHT_GMS_PACKETS)`
- L895 `static void(GAME_SEND_FIGHT_PLAYER_JOIN)`
- L898 `  (for)`
- L900 `  (if)`
- L904 `  (if)`
- L911 `static void(GAME_SEND_cMK_PACKET)`
- L916 `static void(GAME_SEND_FIGHT_LIST_PACKET)`
- L920 `  (for)`
- L927 `static void(GAME_SEND_cMK_PACKET_TO_MAP)`
- L930 `  (for)`
- L935 `static void(GAME_SEND_cMK_PACKET_TO_GUILD)`
- L938 `  (for)`
- L944 `static void(GAME_SEND_cMK_PACKET_TO_ALL)`
- L947 `  (if)`
- L952 `  (for)`
- L956 `static void(GAME_SEND_cMK_PACKET_TO_ALIGN)`
- L959 `  (for)`
- L960 `  (if)`
- L965 `static void(GAME_SEND_cMK_PACKET_TO_ADMIN)`
- L974 `static void(GAME_SEND_cMK_PACKET_TO_FIGHT)`
- L977 `  (for)`
- L984 `static void(GAME_SEND_GDZ_PACKET_TO_FIGHT)`
- L987 `  (for)`
- L996 `static void(GAME_SEND_GDC_PACKET_TO_FIGHT)`
- L999 `  (for)`
- L1008 `static void(GAME_SEND_GA2_PACKET)`
- L1013 `static void(GAME_SEND_CHAT_ERROR_PACKET)`
- L1018 `static void(GAME_SEND_eD_PACKET_TO_MAP)`
- L1024 `static void(GAME_SEND_ECK_PACKET)`
- L1031 `static void(GAME_SEND_ECK_PACKET)`
- L1038 `static void(GAME_SEND_ITEM_VENDOR_LIST_PACKET)`
- L1045 `static void(GAME_SEND_ITEM_LIST_PACKET_PERCEPTEUR)`
- L1050 `static void(GAME_SEND_ITEM_LIST_PACKET_SELLER)`
- L1055 `static void(GAME_SEND_EV_PACKET)`
- L1060 `static void(GAME_SEND_DIALOG_CREATE_PACKET)`
- L1064 `static void(GAME_SEND_DOCUMENT_CREATE_PACKET)`
- L1068 `static void(GAME_SEND_DOCUMENT_CLOSE_PACKET)`
- L1072 `static void(GAME_SEND_QUESTION_PACKET)`
- L1076 `  (if)`
- L1080 `  (if)`
- L1088 `static void(GAME_SEND_QUESTION_PACKET)`
- L1092 `static void(GAME_SEND_END_DIALOG_PACKET)`
- L1097 `static void(GAME_SEND_PAUSE_DIALOG_PACKET)`
- L1102 `static void(GAME_SEND_BUY_ERROR_PACKET)`
- L1107 `static void(GAME_SEND_SELL_ERROR_PACKET)`
- L1112 `static void(GAME_SEND_BUY_OK_PACKET)`
- L1117 `static void(GAME_SEND_OBJECT_QUANTITY_PACKET)`
- L1123 `static void(GAME_SEND_OAKO_PACKET)`
- L1128 `static void(GAME_SEND_ESK_PACKEt)`
- L1133 `static void(GAME_SEND_REMOVE_ITEM_PACKET)`
- L1138 `static void(GAME_SEND_DELETE_OBJECT_FAILED_PACKET)`
- L1143 `static void(GAME_SEND_OBJET_MOVE_PACKET)`
- L1151 `static void(GAME_SEND_DELETE_STATS_ITEM_FM)`
- L1156 `static void(GAME_SEND_EMOTICONE_TO_FIGHT)`
- L1159 `  (for)`
- L1167 `static void(GAME_SEND_OAEL_PACKET)`
- L1172 `static void(GAME_SEND_NEW_LVL_PACKET)`
- L1177 `static void(GAME_SEND_MESSAGE_TO_ALL)`
- L1183 `static void(GAME_SEND_EXCHANGE_REQUEST_OK)`
- L1188 `static void(GAME_SEND_EXCHANGE_REQUEST_ERROR)`
- L1193 `static void(GAME_SEND_EXCHANGE_CONFIRM_OK)`
- L1198 `static void(GAME_SEND_EXCHANGE_MOVE_OK)`
- L1205 `static void(GAME_SEND_EXCHANGE_MOVE_OK_FM)`
- L1212 `static void(GAME_SEND_EXCHANGE_OTHER_MOVE_OK)`
- L1219 `static void(GAME_SEND_EXCHANGE_OTHER_MOVE_OK_FM)`
- L1226 `static void(GAME_SEND_EXCHANGE_OK)`
- L1231 `static void(GAME_SEND_EXCHANGE_OK)`
- L1236 `static void(GAME_SEND_EXCHANGE_VALID)`
- L1241 `static void(GAME_SEND_GROUP_INVITATION_ERROR)`
- L1246 `static void(GAME_SEND_GROUP_INVITATION)`
- L1251 `static void(GAME_SEND_GROUP_CREATE)`
- L1256 `static void(GAME_SEND_PL_PACKET)`
- L1261 `static void(GAME_SEND_PR_PACKET)`
- L1266 `static void(GAME_SEND_PV_PACKET)`
- L1271 `static void(GAME_SEND_ALL_PM_ADD_PACKET)`
- L1276 `  (for)`
- L1284 `static void(GAME_SEND_PM_ADD_PACKET_TO_GROUP)`
- L1290 `static void(GAME_SEND_PM_MOD_PACKET_TO_GROUP)`
- L1296 `static void(GAME_SEND_PM_DEL_PACKET_TO_GROUP)`
- L1301 `static void(GAME_SEND_cMK_PACKET_TO_GROUP)`
- L1310 `static void(GAME_SEND_FIGHT_DETAILS)`
- L1321 `static void(GAME_SEND_IQ_PACKET)`
- L1326 `static void(GAME_SEND_JN_PACKET)`
- L1331 `static void(GAME_SEND_GDC_PACKET_TO_MAP)`
- L1339 `static void(GAME_SEND_GDC_PACKET)`
- L1354 `static void(GAME_SEND_GDF_PACKET)`
- L1359 `static void(GAME_SEND_GDF_PACKET_TO_MAP)`
- L1365 `static void(GAME_SEND_GDF_PACKET_TO_FIGHT)`
- L1368 `  (for)`
- L1410 `static void(GAME_SEND_GA_PACKET_TO_MAP)`
- L1420 `static void(GAME_SEND_EL_BANK_PACKET)`
- L1425 `static void(GAME_SEND_EL_TRUNK_PACKET)`
- L1430 `static void(GAME_SEND_JX_PACKET)`
- L1438 `static void(GAME_SEND_JO_PACKET)`
- L1441 `  (for)`
- L1447 `static void(GAME_SEND_JO_PACKET)`
- L1453 `static void(GAME_SEND_JS_PACKET)`
- L1456 `  (for)`
- L1461 `static void(GAME_SEND_EsK_PACKET)`
- L1466 `static void(GAME_SEND_FIGHT_SHOW_CASE)`
- L1469 `  (for)`
- L1473 `static void(GAME_SEND_Ea_PACKET)`
- L1478 `static void(GAME_SEND_EA_PACKET)`
- L1483 `static void(GAME_SEND_Ec_PACKET)`
- L1488 `static void(GAME_SEND_Em_PACKET)`
- L1493 `static void(GAME_SEND_IO_PACKET_TO_MAP)`
- L1499 `static void(GAME_SEND_FRIENDLIST_PACKET)`
- L1503 `  (if)`
- L1508 `static void(GAME_SEND_FRIEND_ONLINE)`
- L1515 `static void(GAME_SEND_FA_PACKET)`
- L1520 `static void(GAME_SEND_FD_PACKET)`
- L1525 `static void(GAME_SEND_Rp_PACKET)`
- L1535 `  (if)`
- L1543 `static void(GAME_SEND_OS_PACKET)`
- L1553 `  (if)`
- L1556 `  (for)`
- L1570 `static void(GAME_SEND_MOUNT_DESCRIPTION_PACKET)`
- L1575 `static void(GAME_SEND_Rr_PACKET)`
- L1580 `static void(GAME_SEND_ALTER_GM_PACKET)`
- L1583 `  (for)`
- L1590 `static void(GAME_SEND_ALTER_GM_PACKET)`
- L1595 `static void(GAME_SEND_GX_PACKET)`
- L1602 `static void(GAME_SEND_ALTER_GM_PACKET)`
- L1607 `static void(GAME_SEND_Ee_PACKET)`
- L1612 `static void(GAME_SEND_Ee_PACKET_WAIT)`
- L1617 `static void(GAME_SEND_cC_PACKET)`
- L1622 `static void(GAME_SEND_ADD_NPC_TO_MAP)`
- L1627 `static void(GAME_SEND_ADD_NPC)`
- L1631 `static void(GAME_SEND_ADD_PERCO_TO_MAP)`
- L1637 `static void(GAME_SEND_GDO_PACKET_TO_MAP)`
- L1643 `static void(GAME_SEND_ZC_PACKET)`
- L1648 `static void(GAME_SEND_GIP_PACKET)`
- L1653 `static void(GAME_SEND_gn_PACKET)`
- L1658 `static void(GAME_SEND_gC_PACKET)`
- L1663 `static void(GAME_SEND_gV_PACKET)`
- L1668 `static void(GAME_SEND_gIM_PACKET)`
- L1671 `  (if)`
- L1676 `static void(GAME_SEND_gIB_PACKET)`
- L1681 `static void(GAME_SEND_gIH_PACKET)`
- L1686 `static void(GAME_SEND_gS_PACKET)`
- L1690 `static void(GAME_SEND_gJ_PACKET)`
- L1695 `static void(GAME_SEND_gK_PACKET)`
- L1700 `static void(GAME_SEND_gIG_PACKET)`
- L1702 `  (if)`
- L1712 `static void(GAME_SEND_WC_PACKET)`
- L1717 `static void(GAME_SEND_WV_PACKET)`
- L1722 `static void(GAME_SEND_ZAAPI_PACKET)`
- L1727 `static void(GAME_SEND_CLOSE_ZAAPI_PACKET)`
- L1732 `static void(GAME_SEND_WUE_PACKET)`
- L1737 `static void(GAME_SEND_EMOTE_LIST)`
- L1741 `static void(GAME_SEND_ADD_ENEMY)`
- L1746 `static void(GAME_SEND_iAEA_PACKET)`
- L1751 `static void(GAME_SEND_ENEMY_LIST)`
- L1756 `static void(GAME_SEND_iD_COMMANDE)`
- L1761 `static void(GAME_SEND_BWK)`
- L1766 `static void(GAME_SEND_KODE)`
- L1771 `static void(GAME_SEND_hOUSE)`
- L1776 `static void(GAME_SEND_FORGETSPELL_INTERFACE)`
- L1781 `static void(GAME_SEND_R_PACKET)`
- L1786 `static void(GAME_SEND_gIF_PACKET)`
- L1791 `static void(GAME_SEND_gITM_PACKET)`
- L1796 `static void(GAME_SEND_gITp_PACKET)`
- L1801 `static void(GAME_SEND_gITP_PACKET)`
- L1806 `static void(GAME_SEND_IH_PACKET)`
- L1811 `static void(GAME_SEND_FLAG_PACKET)`
- L1816 `static void(GAME_SEND_FLAG_PACKET)`
- L1821 `static void(GAME_SEND_DELETE_FLAG_PACKET)`
- L1826 `static void(GAME_SEND_gT_PACKET)`
- L1831 `static void(GAME_SEND_GUILDHOUSE_PACKET)`
- L1836 `static void(GAME_SEND_GUILDENCLO_PACKET)`
- L1841 `static void(GAME_SEND_EHm_DEL_PACKET)`
- L1846 `static void(GAME_SEND_EHm_ADD_PACKET)`
- L1865 `static void(GAME_SEND_EHl)`
- L1884 `static void(GAME_SEND_EHL_PACKET)`
- L1889 `static void(GAME_SEND_HDVITEM_SELLING)`
- L1897 `static void(GAME_SEND_WEDDING)`
- L1903 `static void(GAME_SEND_PF)`
- L1908 `static void(GAME_SEND_MERCHANT_LIST)`
- L1915 `  (if)`
- L1924 `static void(GAME_SEND_FIGHT_GJK_PACKET_TO_FIGHT)`
- L1930 `  (for)`
- L1936 `static void(GAME_SEND_GJK_PACKET)`
- L1940 `static void(GAME_SEND_cMK_PACKET_INCARNAM_CHAT)`
- L1943 `  (for)`
- L1945 `  (if)`
- L1950 `static void(GAME_SEND_Ag_PACKET)`
- L1959 `static void(SEND_Ej_LIVRE)`
- L1964 `static void(SEND_EW_METIER_PUBLIC)`
- L1969 `static void(SEND_EJ_LIVRE)`
- L1974 `static void(SEND_GDF_PERSO)`
- L1979 `static void(SEND_EMK_MOVE_ITEM)`
- L1986 `static void(SEND_OR_DELETE_ITEM)`
- L1991 `static void(GAME_SEND_CHALLENGE_FIGHT)`
- L1995 `  (for)`
- L2005 `static void(GAME_SEND_CHALLENGE_PERSO)`
- L2009 `static void(GAME_SEND_Im_PACKET_TO_CHALLENGE)`
- L2013 `  (for)`
- L2022 `static void(GAME_SEND_Im_PACKET_TO_CHALLENGE_PERSO)`
- L2026 `static void(GAME_SEND_MESSAGE_SERVER)`
- L2031 `static void(GAME_SEND_WELCOME)`
- L2035 `static void(GAME_SEND_Eq_PACKET)`
- L2039 `static void(GAME_SEND_GA_CLEAR_PACKET_TO_FIGHT)`
- L2042 `  (for)`
- L2049 `static void(SEND_gA_PERCEPTEUR)`
- L2054 `static void(GAME_SEND_PERCO_INFOS_PACKET)`
- L2058 `static void(SEND_Wp_MENU_Prisme)`
- L2063 `static void(SEND_Ww_CLOSE_Prisme)`
- L2068 `static void(GAME_SEND_am_ALIGN_PACKET_TO_SUBAREA)`
- L2076 `static void(SEND_CB_BONUS_CONQUETE)`
- L2081 `static void(SEND_Cb_BALANCE_CONQUETE)`
- L2086 `static void(SEND_GM_PRISME_TO_MAP)`
- L2093 `static void(GAME_SEND_PRISME_TO_MAP)`
- L2099 `static void(SEND_CP_INFO_DEFENSEURS_PRISME)`
- L2104 `static void(SEND_Cp_INFO_ATTAQUANT_PRISME)`
- L2109 `static void(SEND_CW_INFO_WORLD_CONQUETE)`
- L2114 `static void(SEND_CIJ_INFO_JOIN_PRISME)`
- L2119 `static void(GAME_SEND_aM_ALIGN_PACKET_TO_AREA)`
- L2125 `static void(SEND_GA_ACTION_TO_Map)`
- L2134 `static void(SEND_CS_SURVIVRE_MESSAGE_PRISME)`
- L2139 `static void(SEND_CD_MORT_MESSAGE_PRISME)`
- L2144 `static void(SEND_CA_ATTAQUE_MESSAGE_PRISME)`
- L2149 `static void(GAME_SEND_ACTION_TO_DOOR)`
- L2154 `static void(GAME_SEND_ACTION_TO_DOOR)`
- L2158 `static void(sendPacketToMap)`

#### `org/starloco/locos/database/DatabaseManager.java` — 184 lignes
Rôle : Initialisation et accès aux DAO login/game.
Classe(s) : class DatabaseManager
Fonctions :
- L32 ` public(DatabaseManager)`
- L47 ` boolean(isConnected)`
- L61 ` void(initialize)`
- L131 ` HikariDataSource(createHikariDataSource)`
- L143 `  (if)`
- L157 ` boolean(tryConnection)`
- L172 `static ,T> D(get)`
- L173 `  (for)`
- L179 `static DatabaseManager(getInstance)`

#### `org/starloco/locos/database/data/DAO.java` — 14 lignes
Rôle : Base DAO : connexion SQL, load/update/delete, modèle persistance.
Classe(s) : interface DAO <T>
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/database/data/FunctionDAO.java` — 120 lignes
Rôle : Base DAO : connexion SQL, load/update/delete, modèle persistance.
Classe(s) : class FunctionDAO <T> implements DAO<T>, interface ResultSetFunction <R>, interface ResultSetConsumer
Fonctions :
- L25 ` public(FunctionDAO)`
- L30 ` public(FunctionDAO)`
- L36 ` String(getTableName)`
- L40 ` Connection(getConnection)`
- L44 ` void(execute)`
- L46 `  (synchronized)`
- L47 `  (try)`
- L48 `  (try)`
- L57 ` void(execute)`
- L59 `  (synchronized)`
- L68 ` PreparedStatement(getPreparedStatement)`
- L77 ` void(getData)`
- L79 `  (synchronized)`
- L80 `  (try)`
- L81 `  (try)`
- L87 ` R(getData)`
- L89 `  (synchronized)`
- L90 `  (try)`
- L91 `  (try)`
- L97 ` void(close)`
- L99 `  (if)`
- L101 `  (if)`
- L105 `  (if)`
- L115 ` void(sendError)`

#### `org/starloco/locos/database/data/game/AreaData.java` — 74 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class AreaData extends FunctionDAO<Area>
Fonctions :
- L13 ` public(AreaData)`
- L17 ` d(loadFully)`
- L23 ` 	(while)`
- L26 ` 	(if)`
- L37 ` a(load)`
- L42 ` n(insert)`
- L47 ` d(delete)`
- L52 ` d(update)`
- L68 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/AuctionData.java` — 105 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class AuctionData extends FunctionDAO<Auction>
Fonctions :
- L18 ` public(AuctionData)`
- L22 ` d(loadFully)`
- L27 `  (while)`
- L30 `  (if)`
- L44 ` n(load)`
- L49 ` n(insert)`
- L69 ` d(delete)`
- L83 ` d(update)`
- L99 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/BankData.java` — 81 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BankData extends FunctionDAO<Account>
Fonctions :
- L13 ` public(BankData)`
- L16 ` d(loadFully)`
- L21 ` t(load)`
- L27 `  (if)`
- L38 ` n(insert)`
- L55 ` d(delete)`
- L60 ` void(update)`
- L75 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/BigStoreListingData.java` — 113 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BigStoreListingData extends FunctionDAO<BigStoreListing>
Fonctions :
- L18 ` public(BigStoreListingData)`
- L21 ` d(loadFully)`
- L26 `  (while)`
- L28 `  (if)`
- L30 `  (if)`
- L43 ` g(load)`
- L48 ` n(insert)`
- L62 `  (if)`
- L66 `  (try)`
- L67 `  (if)`
- L83 ` d(delete)`
- L93 `  (if)`
- L102 ` d(update)`
- L107 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/ChallengeData.java` — 53 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ChallengeData extends FunctionDAO<Object>
Fonctions :
- L11 ` public(ChallengeData)`
- L14 ` d(loadFully)`
- L19 `  (while)`
- L27 ` t(load)`
- L32 ` n(insert)`
- L37 ` d(delete)`
- L42 ` d(update)`
- L47 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/CollectorData.java` — 143 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class CollectorData extends FunctionDAO<Collector>
Fonctions :
- L16 ` public(CollectorData)`
- L19 ` d(loadFully)`
- L24 `  (while)`
- L26 `  (if)`
- L31 `  (if)`
- L43 ` r(load)`
- L48 ` n(insert)`
- L69 `  (if)`
- L73 `  (try)`
- L74 `  (if)`
- L88 ` d(delete)`
- L102 ` d(update)`
- L119 ` <?>(getReferencedClass)`
- L124 ` int(getId)`
- L130 `  (while)`

#### `org/starloco/locos/database/data/game/CraftData.java` — 71 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class CraftData extends FunctionDAO<Object>
Fonctions :
- L12 ` public(CraftData)`
- L15 ` d(loadFully)`
- L20 `  (while)`
- L24 `  (for)`
- L45 ` t(load)`
- L50 ` n(insert)`
- L55 ` d(delete)`
- L60 ` d(update)`
- L65 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/DropData.java` — 85 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class DropData extends FunctionDAO<World.Drop>
Fonctions :
- L15 ` public(DropData)`
- L18 ` d(loadFully)`
- L24 `  (while)`
- L29 `  (if)`
- L38 `  (if)`
- L49 ` ArrayList<Double>(getPercents)`
- L59 ` p(load)`
- L64 ` n(insert)`
- L69 ` d(delete)`
- L74 ` d(update)`
- L79 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/ExperienceTables.java` — 58 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ExperienceTables
Fonctions :
- L8 ` public(ExperienceTable)`
- L12 ` long(minXpAt)`
- L16 ` long(maxXpAt)`
- L21 ` int(levelForXp)`
- L33 ` int(maxLevel)`
- L46 ` public(ExperienceTables)`

#### `org/starloco/locos/database/data/game/ExtraMonsterData.java` — 53 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ExtraMonsterData extends FunctionDAO<Object>
Fonctions :
- L11 ` public(ExtraMonsterData)`
- L14 ` d(loadFully)`
- L19 `  (while)`
- L27 ` t(load)`
- L32 ` n(insert)`
- L37 ` d(delete)`
- L42 ` d(update)`
- L47 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/FullMorphData.java` — 58 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class FullMorphData extends FunctionDAO<Object>
Fonctions :
- L11 ` public(FullMorphData)`
- L14 ` d(loadFully)`
- L19 `  (while)`
- L21 `  (if)`
- L32 ` t(load)`
- L37 ` n(insert)`
- L42 ` d(delete)`
- L47 ` d(update)`
- L52 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/GangsterData.java` — 63 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class GangsterData extends FunctionDAO<Bandit>
Fonctions :
- L12 ` public(GangsterData)`
- L15 ` d(loadFully)`
- L20 `  (while)`
- L28 ` t(load)`
- L33 ` n(insert)`
- L38 ` d(delete)`
- L43 ` d(update)`
- L57 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/GiftData.java` — 73 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class GiftData extends FunctionDAO<Pair<Account, String>>
Fonctions :
- L13 ` public(GiftData)`
- L16 ` d(loadFully)`
- L21 ` <Account, String>(load)`
- L31 ` n(insert)`
- L47 ` d(delete)`
- L52 ` d(update)`
- L67 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/GuildMemberData.java` — 129 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class GuildMemberData extends FunctionDAO<Player>
Fonctions :
- L15 ` public(GuildMemberData)`
- L18 ` d(loadFully)`
- L23 `  (while)`
- L37 ` r(load)`
- L42 ` n(insert)`
- L47 ` d(delete)`
- L61 ` d(update)`
- L90 ` <?>(getReferencedClass)`
- L95 ` void(deleteAll)`
- L108 ` int(isPersoInGuild)`
- L118 ` int[](isPersoInGuild)`

#### `org/starloco/locos/database/data/game/HdvData.java` — 54 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class HdvData extends FunctionDAO<Object>
Fonctions :
- L12 ` public(HdvData)`
- L15 ` d(loadFully)`
- L20 `  (while)`
- L28 ` t(load)`
- L33 ` n(insert)`
- L38 ` d(delete)`
- L43 ` d(update)`
- L48 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/HeroicMobsGroupsData.java` — 221 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class HeroicMobsGroupsData extends FunctionDAO<Object>
Fonctions :
- L21 ` public(HeroicMobsGroupsData)`
- L23 ` d(loadFully)`
- L28 `  (while)`
- L30 `  (if)`
- L40 ` t(load)`
- L45 ` n(insert)`
- L50 ` d(delete)`
- L55 ` d(update)`
- L60 ` <?>(getReferencedClass)`
- L65 ` void(insert)`
- L90 ` void(update)`
- L111 ` void(delete)`
- L129 ` void(deleteAll)`
- L141 ` void(loadFix)`
- L145 `  (while)`
- L147 `  (for)`
- L154 `  (for)`
- L165 ` void(insertFix)`
- L186 ` void(updateFix)`
- L190 `  (for)`
- L208 ` void(deleteAllFix)`

#### `org/starloco/locos/database/data/game/HouseData.java` — 188 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class HouseData extends FunctionDAO<House>
Fonctions :
- L15 ` public(HouseData)`
- L18 ` d(loadFully)`
- L23 `  (while)`
- L47 ` e(load)`
- L52 ` n(insert)`
- L57 ` d(delete)`
- L62 ` d(update)`
- L82 ` <?>(getReferencedClass)`
- L87 ` boolean(update)`
- L103 ` void(buy)`
- L126 ` void(sell)`
- L141 ` void(updateCode)`
- L157 ` void(updateGuild)`
- L174 ` void(removeGuild)`

#### `org/starloco/locos/database/data/game/JobData.java` — 58 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class JobData extends FunctionDAO<Job>
Fonctions :
- L12 ` public(JobData)`
- L15 ` d(loadFully)`
- L20 `  (while)`
- L32 ` b(load)`
- L37 ` n(insert)`
- L42 ` d(delete)`
- L47 ` d(update)`
- L52 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/MonsterData.java` — 80 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class MonsterData extends FunctionDAO<Monster>
Fonctions :
- L13 ` public(MonsterData)`
- L16 ` d(loadFully)`
- L21 `  (while)`
- L41 `  (if)`
- L54 ` r(load)`
- L59 ` n(insert)`
- L64 ` d(delete)`
- L69 ` d(update)`
- L74 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/MountParkData.java` — 114 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class MountParkData extends FunctionDAO<MountPark>
Fonctions :
- L13 ` public(MountParkData)`
- L16 ` d(loadFully)`
- L21 `  (while)`
- L45 ` k(load)`
- L50 ` n(insert)`
- L70 ` d(delete)`
- L75 ` d(update)`
- L96 ` <?>(getReferencedClass)`
- L101 ` void(exist)`
- L105 `  (if)`

#### `org/starloco/locos/database/data/game/NpcData.java` — 92 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class NpcData extends FunctionDAO<Pair<Npc, Integer>>
Fonctions :
- L17 ` public(NpcData)`
- L20 ` d(loadFully)`
- L25 `  (while)`
- L40 ` <Npc, Integer>(load)`
- L45 ` n(insert)`
- L66 ` d(delete)`
- L81 ` d(update)`
- L86 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/ObjectActionData.java` — 62 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ObjectActionData extends FunctionDAO<ObjectAction>
Fonctions :
- L14 ` public(ObjectActionData)`
- L17 ` d(loadFully)`
- L23 `  (while)`
- L36 ` n(load)`
- L41 ` n(insert)`
- L46 ` d(delete)`
- L51 ` d(update)`
- L56 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/ObjectSetData.java` — 54 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ObjectSetData extends FunctionDAO<ObjectSet>
Fonctions :
- L12 ` public(ObjectSetData)`
- L15 ` d(loadFully)`
- L20 `  (while)`
- L28 ` t(load)`
- L33 ` n(insert)`
- L38 ` d(delete)`
- L43 ` d(update)`
- L48 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/ObjectTemplateData.java` — 79 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ObjectTemplateData extends FunctionDAO<ObjectTemplate>
Fonctions :
- L14 ` public(ObjectTemplateData)`
- L17 ` d(loadFully)`
- L22 `  (while)`
- L24 `  (if)`
- L42 ` e(load)`
- L47 ` n(insert)`
- L52 ` d(delete)`
- L57 ` d(update)`
- L73 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/PetTemplateData.java` — 55 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class PetTemplateData extends FunctionDAO<Pet>
Fonctions :
- L12 ` public(PetTemplateData)`
- L15 ` d(loadFully)`
- L20 `  (while)`
- L29 ` t(load)`
- L34 ` n(insert)`
- L39 ` d(delete)`
- L44 ` d(update)`
- L49 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/PrismData.java` — 95 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class PrismData extends FunctionDAO<Prism>
Fonctions :
- L13 ` public(PrismData)`
- L16 ` d(loadFully)`
- L21 `  (while)`
- L30 ` m(load)`
- L35 ` n(insert)`
- L58 ` d(delete)`
- L72 ` d(update)`
- L89 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/QuestProgressData.java` — 100 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class QuestProgressData extends FunctionDAO<QuestProgress>
Fonctions :
- L19 ` public(QuestProgressData)`
- L23 ` d(loadFully)`
- L28 ` s(load)`
- L36 `  (while)`
- L52 ` n(insert)`
- L58 ` d(delete)`
- L70 ` void(replace)`
- L89 ` d(update)`
- L94 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/RuneData.java` — 53 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class RuneData extends FunctionDAO<Rune>
Fonctions :
- L11 ` public(RuneData)`
- L14 ` d(loadFully)`
- L19 `  (while)`
- L27 ` e(load)`
- L32 ` n(insert)`
- L37 ` d(delete)`
- L42 ` d(update)`
- L47 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/SaleOffer.java` — 75 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class SaleOffer
Fonctions :
- L12 ` public(SaleOffer)`
- L16 ` public(SaleOffer)`
- L20 ` public(SaleOffer)`
- L26 ` String(encode)`
- L44 ` private(Currency)`
- L46 ` boolean(isItem)`
- L48 `static Currency(nonItemCurrency)`
- L50 `  (switch)`
- L57 `static Currency(itemCurrency)`
- L68 ` ObjectTemplate(item)`

#### `org/starloco/locos/database/data/game/SpellData.java` — 125 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class SpellData extends FunctionDAO<Spell>
Fonctions :
- L16 ` public(SpellData)`
- L19 ` d(loadFully)`
- L25 `  (while)`
- L36 `  (if)`
- L63 ` l(load)`
- L68 ` n(insert)`
- L73 ` d(delete)`
- L78 ` d(update)`
- L83 ` <?>(getReferencedClass)`
- L88 ` SortStats(parseSortStats)`

#### `org/starloco/locos/database/data/game/SubAreaData.java` — 75 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class SubAreaData extends FunctionDAO<SubArea>
Fonctions :
- L13 ` public(SubAreaData)`
- L17 ` d(loadFully)`
- L22 `  (while)`
- L25 `  (if)`
- L37 ` a(load)`
- L42 ` n(insert)`
- L47 ` d(delete)`
- L52 ` d(update)`
- L69 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/game/TrunkData.java` — 140 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class TrunkData extends FunctionDAO<Trunk>
Fonctions :
- L15 ` public(TrunkData)`
- L18 ` d(loadFully)`
- L24 `  (while)`
- L30 `  (if)`
- L43 ` k(load)`
- L48 ` n(insert)`
- L69 ` d(delete)`
- L74 ` d(update)`
- L90 ` <?>(getReferencedClass)`
- L95 ` void(exist)`
- L99 `  (if)`
- L107 ` void(update)`
- L123 ` void(updateCode)`

#### `org/starloco/locos/database/data/game/ZaapiData.java` — 66 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ZaapiData extends FunctionDAO<Object>
Fonctions :
- L11 ` public(ZaapiData)`
- L14 ` d(loadFully)`
- L20 `  (while)`
- L21 `  (if)`
- L40 ` t(load)`
- L45 ` n(insert)`
- L50 ` d(delete)`
- L55 ` d(update)`
- L60 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/AccountData.java` — 186 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class AccountData extends FunctionDAO<Account>
Fonctions :
- L17 ` public(AccountData)`
- L21 ` d(loadFully)`
- L26 ` t(load)`
- L49 ` n(insert)`
- L54 ` d(delete)`
- L61 ` d(update)`
- L79 ` <?>(getReferencedClass)`
- L84 ` long(getSubscribe)`
- L94 ` void(updateVoteAll)`
- L98 `  (while)`
- L100 `  (if)`
- L109 ` void(updateLastConnection)`
- L124 ` void(setLogged)`
- L129 `  (if)`
- L140 ` void(updateBannedTime)`
- L152 ` int(loadPoints)`
- L156 ` int(loadPointsWithoutUsersDb)`
- L166 ` World.Couple<Long, Boolean>(modPoints)`
- L175 `  (try)`

#### `org/starloco/locos/database/data/login/AdData.java` — 56 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class AdData extends FunctionDAO<Object>
Fonctions :
- L12 ` public(AdData)`
- L17 ` d(loadFully)`
- L22 `  (while)`
- L30 ` t(load)`
- L35 ` n(insert)`
- L40 ` d(delete)`
- L45 ` d(update)`
- L50 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/BanIpData.java` — 66 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BanIpData extends FunctionDAO<String>
Fonctions :
- L11 ` public(BanIpData)`
- L14 ` d(loadFully)`
- L19 ` g(load)`
- L24 ` n(insert)`
- L41 ` d(delete)`
- L55 ` d(update)`
- L60 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/BaseAreaData.java` — 57 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BaseAreaData extends FunctionDAO<Area>
Fonctions :
- L12 ` public(BaseAreaData)`
- L17 ` d(loadFully)`
- L22 ` 	(while)`
- L31 ` a(load)`
- L36 ` n(insert)`
- L41 ` d(delete)`
- L46 ` d(update)`
- L51 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/BaseHouseData.java` — 58 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BaseHouseData extends FunctionDAO<House>
Fonctions :
- L12 ` public(BaseHouseData)`
- L16 ` d(loadFully)`
- L21 ` 	(while)`
- L32 ` e(load)`
- L37 ` n(insert)`
- L42 ` d(delete)`
- L47 ` d(update)`
- L52 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/BaseMountParkData.java` — 83 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BaseMountParkData extends FunctionDAO<MountPark>
Fonctions :
- L15 ` public(BaseMountParkData)`
- L19 ` d(loadFully)`
- L24 ` 	(while)`
- L34 ` k(load)`
- L50 ` n(insert)`
- L55 ` d(delete)`
- L60 ` d(update)`
- L77 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/BaseSubAreaData.java` — 60 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BaseSubAreaData extends FunctionDAO<SubArea>
Fonctions :
- L12 ` public(BaseSubAreaData)`
- L17 ` d(loadFully)`
- L22 ` 	(while)`
- L34 ` a(load)`
- L39 ` n(insert)`
- L44 ` d(delete)`
- L49 ` d(update)`
- L54 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/BaseTrunkData.java` — 127 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class BaseTrunkData extends FunctionDAO<Trunk>
Fonctions :
- L17 ` public(BaseTrunkData)`
- L22 ` d(loadFully)`
- L27 `  (while)`
- L29 `  (if)`
- L41 ` k(load)`
- L48 ` n(insert)`
- L53 `  (for)`
- L54 `  (if)`
- L59 `  (if)`
- L66 `  (while)`
- L67 `  (if)`
- L78 `  (if)`
- L85 `  (if)`
- L89 `  (try)`
- L90 `  (if)`
- L111 ` d(delete)`
- L116 ` d(update)`
- L121 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/EventData.java` — 106 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class EventData extends FunctionDAO<Event>
Fonctions :
- L16 ` public(EventData)`
- L20 ` Event[](load)`
- L25 `  (if)`
- L27 `  (while)`
- L30 `  (if)`
- L43 ` byte(getNumberOfEvent)`
- L49 `  (if)`
- L60 ` Event(getEventById)`
- L65 `  (switch)`
- L75 ` d(loadFully)`
- L80 ` t(load)`
- L85 ` n(insert)`
- L90 ` d(delete)`
- L95 ` d(update)`
- L100 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/GuildData.java` — 115 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class GuildData extends FunctionDAO<Guild>
Fonctions :
- L15 ` public(GuildData)`
- L19 ` d(loadFully)`
- L25 ` d(load)`
- L30 `  (if)`
- L42 ` n(insert)`
- L55 `  (if)`
- L59 `  (try)`
- L60 `  (if)`
- L75 ` d(delete)`
- L89 ` d(update)`
- L109 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/MountData.java` — 169 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class MountData extends FunctionDAO<Mount>
Fonctions :
- L17 ` public(MountData)`
- L21 ` d(loadFully)`
- L26 ` t(load)`
- L31 `  (if)`
- L48 ` n(insert)`
- L80 `  (if)`
- L84 `  (try)`
- L85 `  (if)`
- L100 ` d(delete)`
- L107 `  (if)`
- L117 ` void(delete)`
- L125 ` d(update)`
- L133 `  (if)`
- L163 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/ObjectData.java` — 158 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ObjectData extends FunctionDAO<GameObject>
Fonctions :
- L14 ` public(ObjectData)`
- L18 ` d(loadFully)`
- L23 `  (while)`
- L43 ` t(load)`
- L54 `  (if)`
- L68 ` n(insert)`
- L81 `  (if)`
- L85 `  (try)`
- L86 `  (if)`
- L101 ` d(delete)`
- L115 ` d(update)`
- L134 ` <?>(getReferencedClass)`
- L139 ` void(loads)`
- L143 `  (while)`
- L145 `  (if)`

#### `org/starloco/locos/database/data/login/ObvijevanData.java` — 75 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ObvijevanData extends FunctionDAO<Pair<Integer, Integer>>
Fonctions :
- L12 ` public(ObvijevanData)`
- L16 ` d(loadFully)`
- L21 ` <Integer, Integer>(load)`
- L36 ` n(insert)`
- L51 ` d(delete)`
- L64 ` d(update)`
- L69 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/PetData.java` — 96 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class PetData extends FunctionDAO<PetEntry>
Fonctions :
- L13 ` public(PetData)`
- L17 ` d(loadFully)`
- L22 `  (while)`
- L30 ` y(load)`
- L37 ` n(insert)`
- L57 ` d(delete)`
- L71 ` d(update)`
- L90 ` <?>(getReferencedClass)`

#### `org/starloco/locos/database/data/login/PlayerData.java` — 432 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class PlayerData extends FunctionDAO<Player>
Fonctions :
- L26 ` public(PlayerData)`
- L30 ` d(loadFully)`
- L35 ` Player(buildFromResultSet)`
- L50 ` r(load)`
- L79 ` n(insert)`
- L109 `  (if)`
- L110 `  (try)`
- L111 `  (if)`
- L129 ` d(delete)`
- L153 ` d(update)`
- L219 ` <?>(getReferencedClass)`
- L224 ` HashMap<Integer, Integer>(getStats)`
- L235 ` void(loadByAccountId)`
- L247 `  (while)`
- L249 `  (if)`
- L250 `  (if)`
- L281 ` String(loadTitles)`
- L293 ` void(updateInfos)`
- L312 ` void(updateGroupe)`
- L327 ` void(updateGroupe)`
- L342 ` void(updateTimeTaverne)`
- L356 ` void(updateTitles)`
- L370 ` void(updateLogged)`
- L384 ` boolean(exist)`
- L396 ` void(reloadGroup)`
- L400 `  (if)`
- L409 ` int(canRevive)`
- L421 ` void(setRevive)`

#### `org/starloco/locos/database/data/login/ServerData.java` — 65 lignes
Rôle : DAO spécialisé : chargement/sauvegarde d’une table ou famille de données.
Classe(s) : class ServerData extends FunctionDAO<Long>
Fonctions :
- L13 ` public(ServerData)`
- L16 ` d(loadFully)`
- L29 ` g(load)`
- L34 ` n(insert)`
- L39 ` d(delete)`
- L44 ` d(update)`
- L59 ` <?>(getReferencedClass)`

#### `org/starloco/locos/dynamic/FormuleOfficiel.java` — 200 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class FormuleOfficiel
Fonctions :
- L16 `static long(getXp)`
- L22 `  (if)`
- L25 `  (if)`
- L48 `  (for)`
- L60 `  (switch)`
- L104 `  (if)`
- L108 `  (if)`
- L142 `  (for)`
- L154 `  (switch)`

#### `org/starloco/locos/dynamic/Noel.java` — 115 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Noel
Fonctions :
- L30 `static void(getRandomObjectOne)`
- L34 `  (if)`
- L47 `static void(getRandomObjectTwo)`
- L51 `  (if)`
- L64 `static void(getRandomObjectTree)`
- L68 `  (if)`
- L81 `static void(getRandomObjectFour)`
- L85 `  (if)`
- L98 `static void(getRandomObjectFive)`
- L102 `  (if)`

#### `org/starloco/locos/dynamic/Start.java` — 251 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Start
Fonctions :
- L22 ` public(Start)`

#### `org/starloco/locos/dynamic/Tavernier.java` — 129 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Tavernier extends Updatable<Void>
Fonctions :
- L24 `static Tavernier(getInstance)`
- L32 ` public(Tavernier)`
- L37 ` d(update)`
- L40 `  (if)`
- L43 `  (for)`
- L51 `  (if)`
- L59 ` void(drinkAllRound)`
- L63 `  (for)`
- L64 `  (if)`
- L66 `  (if)`
- L76 ` d(get)`
- L81 ` void(talk)`
- L85 ` List<String>(parseHtml)`
- L88 `  (for)`
- L99 `  (if)`
- L106 ` List<String>(getHTML)`
- L114 `  (if)`

#### `org/starloco/locos/entity/Collector.java` — 605 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Collector
Fonctions :
- L40 ` public(Collector)`
- L53 `  (for)`
- L66 ` void(setId)`
- L70 ` String(getFullName)`
- L74 `static String(parseGM)`
- L79 `  (for)`
- L85 `  (if)`
- L87 `  (if)`
- L110 ` void(moveOnMap)`
- L124 `  (if)`
- L127 `  (for)`
- L132 `static String(parseToGuild)`
- L163 `  (for)`
- L164 `  (if)`
- L174 `  (if)`
- L206 `  (if)`
- L241 `static int(getCollectorByGuildId)`
- L248 `static Collector(getCollectorByMapId)`
- L255 `static int(countCollectorGuild)`
- L263 `static void(parseAttaque)`
- L269 `static void(parseDefense)`
- L275 `static String(parseAttaqueToGuild)`
- L280 `  (if)`
- L294 `static String(parseDefenseToGuild)`
- L298 `  (for)`
- L313 `static void(removeCollector)`
- L315 `  (for)`
- L316 `  (if)`
- L326 ` void(reloadTimer)`
- L335 ` long(getDate)`
- L339 ` Player(getPoseur)`
- L343 ` void(setPoseur)`
- L347 ` int(getId)`
- L351 ` int(getMap)`
- L355 ` int(getCell)`
- L359 ` void(setCell)`
- L363 ` int(getGuildId)`
- L367 ` Guild(getGuild)`
- L371 ` int(getN1)`
- L375 ` int(getN2)`
- L379 ` int(getInFight)`
- L383 ` void(setInFight)`
- L387 ` int(get_inFightID)`
- L391 ` void(set_inFightID)`
- L395 ` long(getKamas)`
- L399 ` void(setKamas)`
- L403 ` long(getXp)`
- L407 ` void(setXp)`
- L411 ` boolean(getExchange)`
- L415 ` void(setExchange)`
- L419 ` void(addLogObjects)`
- L423 ` String(getLogObjects)`
- L434 ` java.util.Map<Integer, GameObject>(getOjects)`
- L438 ` boolean(haveObjects)`
- L442 ` int(getPodsTotal)`
- L450 ` int(getMaxPod)`
- L454 ` boolean(addObjet)`
- L456 `  (for)`
- L458 `  (if)`
- L466 ` void(removeObjet)`
- L470 ` void(delCollector)`
- L476 ` String(getItemCollectorList)`
- L486 ` String(parseItemCollector)`
- L493 ` void(removeFromCollector)`
- L503 `  (if)`
- L532 `  (if)`
- L564 `synchronized boolean(addDefenseFight)`
- L568 `  (for)`
- L570 `  (if)`
- L585 `synchronized boolean(delDefenseFight)`
- L593 ` void(clearDefenseFight)`
- L597 ` java.util.Map<Integer, Player>(getDefenseFight)`
- L601 ` Collection<GameObject>(getDrops)`

#### `org/starloco/locos/entity/Prism.java` — 241 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Prism
Fonctions :
- L34 ` public(Prism)`
- L42 `  (if)`
- L54 `static void(parseAttack)`
- L60 `static void(parseDefense)`
- L66 `static String(attackerOfPrisme)`
- L73 `  (for)`
- L75 `  (if)`
- L76 `  (for)`
- L89 `static String(defenderOfPrisme)`
- L95 `  (if)`
- L96 `  (for)`
- L97 `  (if)`
- L98 `  (for)`
- L126 ` int(getId)`
- L130 ` int(getAlignment)`
- L134 ` byte(getState)`
- L138 ` int(getLevel)`
- L142 ` void(setLevel)`
- L146 ` int(getMap)`
- L150 ` int(getCell)`
- L154 ` void(setCell)`
- L158 ` int(getHonor)`
- L162 ` void(addHonor)`
- L166 ` int(getGrade)`
- L172 ` int(getConquestArea)`
- L176 ` void(setConquestArea)`
- L180 ` Fight(getFight)`
- L184 ` void(setFight)`
- L186 ` Stats(getStats)`
- L190 ` void(refreshStats)`
- L214 ` int(getX)`
- L219 ` int(getY)`
- L224 ` SubArea(getSubArea)`
- L229 ` Area(getArea)`
- L234 ` String(parseToGM)`

#### `org/starloco/locos/entity/Town.java` — 65 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Town
Fonctions :
- L25 ` public(Town)`
- L34 ` int(getId)`
- L38 ` Area(getArea)`
- L42 ` int(getAlignment)`
- L46 ` GameMap(getMainDoorMap)`
- L50 ` int(getMainDoorOpeningDuration)`
- L54 ` GameMap(getPrismRoomMap)`
- L58 ` int(getPrismRoomOpeningDuration)`

#### `org/starloco/locos/entity/exchange/CraftSecure.java` — 230 lignes
Rôle : Système échange : état d’échange entre joueur/PNJ/craft sécurisé.
Classe(s) : class CraftSecure extends PlayerExchange
Fonctions :
- L27 ` public(CraftSecure)`
- L31 `  (if)`
- L38 ` Player(getNeeder)`
- L42 ` int(getMaxCase)`
- L46 `synchronized void(apply)`
- L78 `  (if)`
- L103 ` d(giveObjects)`
- L106 `  (for)`
- L107 `  (for)`
- L117 `  (if)`
- L126 `synchronized void(cancel)`
- L134 ` void(setPayKamas)`
- L147 `  (switch)`
- L165 ` void(setPayItems)`
- L180 `  (if)`
- L187 ` void(addItem)`
- L195 `  (if)`
- L207 ` void(removeItem)`
- L214 `  (if)`
- L225 ` void(send)`

#### `org/starloco/locos/entity/exchange/Exchange.java` — 34 lignes
Rôle : Système échange : état d’échange entre joueur/PNJ/craft sécurisé.
Classe(s) : class Exchange
Fonctions :
- L15 ` public(Exchange)`
- L20 `static World.Couple<Integer, Integer>(getCoupleInList)`

#### `org/starloco/locos/entity/exchange/NpcExchange.java` — 261 lignes
Rôle : Système échange : état d’échange entre joueur/PNJ/craft sécurisé.
Classe(s) : class NpcExchange
Fonctions :
- L30 ` public(NpcExchange)`
- L36 ` ArrayList<World.Couple<Integer, Integer>>(getItems1)`
- L40 `synchronized long(getKamas)`
- L45 ` boolean(isPlayerOk)`
- L49 `synchronized void(toogleOK)`
- L51 `  (if)`
- L64 `synchronized void(setKamas)`
- L71 `  (if)`
- L83 `synchronized void(cancel)`
- L89 `synchronized void(apply)`
- L91 `  (for)`
- L94 `  (if)`
- L101 `  (if)`
- L109 `  (if)`
- L116 `  (for)`
- L125 `  (if)`
- L142 `synchronized void(addItem)`
- L151 `  (if)`
- L161 `synchronized void(removeItem)`
- L170 `  (if)`
- L180 `synchronized int(getQuaItem)`
- L192 `synchronized void(clearItems)`
- L199 `  (if)`
- L204 `synchronized World.Couple<Integer, Integer>(getCoupleInList)`
- L211 `synchronized void(putAllGiveItem)`
- L217 `  (if)`
- L224 `  (if)`
- L225 `  (if)`
- L233 `  (if)`
- L239 `  (if)`
- L245 ` NpcTemplate(getNpc)`
- L249 ` void(setNpc)`
- L253 ` void(setAuction)`
- L257 ` Auction(getAuction)`

#### `org/starloco/locos/entity/exchange/PlayerExchange.java` — 909 lignes
Rôle : Système échange : état d’échange entre joueur/PNJ/craft sécurisé.
Classe(s) : class PlayerExchange extends Exchange
Fonctions :
- L19 ` public(PlayerExchange)`
- L23 ` boolean(isPodsOK)`
- L30 `  (if)`
- L33 `  (for)`
- L39 `  (if)`
- L42 `  (for)`
- L48 `  (if)`
- L57 `  (for)`
- L63 `  (if)`
- L66 `  (for)`
- L72 `  (if)`
- L79 `synchronized long(getKamas)`
- L93 `synchronized boolean(toogleOk)`
- L96 `  (if)`
- L97 `  (if)`
- L110 `synchronized void(setKamas)`
- L126 `  (if)`
- L140 `synchronized void(cancel)`
- L151 `synchronized void(apply)`
- L156 `  (for)`
- L167 `  (for)`
- L211 `  (for)`
- L237 ` void(giveObject)`
- L252 `synchronized void(addItem)`
- L272 `  (if)`
- L275 `  (for)`
- L282 `  (for)`
- L304 `  (if)`
- L306 `  (if)`
- L320 `  (if)`
- L334 `synchronized void(removeItem)`
- L352 `  (if)`
- L358 `  (if)`
- L373 `  (if)`
- L385 `synchronized int(getQuaItem)`
- L410 ` public(NpcExchangePets)`
- L415 `synchronized void(toogleOK)`
- L417 `  (if)`
- L427 `synchronized void(setKamas)`
- L434 `  (if)`
- L446 `synchronized void(cancel)`
- L452 `synchronized void(apply)`
- L455 `  (for)`
- L477 `  (for)`
- L504 `synchronized void(addItem)`
- L515 `  (if)`
- L523 `  (if)`
- L524 `  (if)`
- L527 `  (for)`
- L531 `  (if)`
- L556 `synchronized void(removeItem)`
- L577 `  (if)`
- L578 `  (if)`
- L581 `  (for)`
- L585 `  (if)`
- L610 ` boolean(verifIfAlonePets)`
- L617 ` boolean(verifIfAloneParcho)`
- L624 `synchronized void(clearNpcItems)`
- L631 `synchronized Couple<Integer, Integer>(getCoupleInList)`
- L639 `synchronized int(getQuaItem)`
- L652 ` NpcTemplate(getNpc)`
- L656 ` void(setNpc)`
- L671 ` public(NpcRessurectPets)`
- L676 `synchronized long(getKamas)`
- L682 `synchronized void(toogleOK)`
- L684 `  (if)`
- L694 `synchronized void(setKamas)`
- L701 `  (if)`
- L713 `synchronized void(cancel)`
- L720 `synchronized void(apply)`
- L722 `  (for)`
- L724 `  (if)`
- L735 `  (if)`
- L746 `synchronized void(addItem)`
- L757 `  (if)`
- L765 `  (if)`
- L766 `  (if)`
- L769 `  (for)`
- L775 `  (if)`
- L777 `  (if)`
- L803 `synchronized void(removeItem)`
- L824 `  (if)`
- L825 `  (if)`
- L828 `  (for)`
- L834 `  (if)`
- L860 ` boolean(verification)`
- L863 `  (for)`
- L871 `synchronized void(clearNpcItems)`
- L878 `synchronized Couple<Integer, Integer>(getCoupleInList)`
- L886 `synchronized int(getQuaItem)`
- L899 ` NpcTemplate(getNpc)`
- L903 ` void(setNpc)`

#### `org/starloco/locos/entity/map/House.java` — 200 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class House
Fonctions :
- L26 ` public(House)`
- L49 ` void(setGuildRightsWithParse)`
- L54 ` int(getId)`
- L58 ` int(getMapId)`
- L62 ` int(getCellId)`
- L66 ` int(getOwnerId)`
- L70 ` void(setOwnerId)`
- L74 ` int(getSale)`
- L78 ` void(setSale)`
- L82 ` int(getGuildId)`
- L86 ` void(setGuildId)`
- L90 ` int(getGuildRights)`
- L94 ` void(setGuildRights)`
- L98 ` int(getAccess)`
- L102 ` void(setAccess)`
- L106 ` String(getKey)`
- L110 ` void(setKey)`
- L114 ` int(getHouseMapId)`
- L118 ` int(getHouseCellId)`
- L122 ` void(enter)`
- L144 `  (if)`
- L154 ` void(lock)`
- L159 ` boolean(canDo)`
- L163 ` void(initRight)`
- L174 ` void(parseIntToRight)`
- L176 `  (if)`
- L188 `  (while)`
- L192 `  (if)`

#### `org/starloco/locos/entity/map/InteractiveObject.java` — 302 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class InteractiveObject
Fonctions :
- L10 ` public(InteractiveObject)`
- L289 ` int(getId)`
- L293 ` int(getState)`
- L297 ` void(setState)`

#### `org/starloco/locos/entity/map/InteractiveObjectTemplate.java` — 27 lignes
Rôle : Template item : génération de jets, stats min/max, actions, type, prix, conditions.
Classe(s) : class InteractiveObjectTemplate
Fonctions :
- L11 ` public(InteractiveObjectTemplate)`
- L17 ` int(getId)`
- L21 ` boolean(allowSkill)`
- L24 ` boolean(isWalkable)`

#### `org/starloco/locos/entity/map/MountPark.java` — 371 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class MountPark
Fonctions :
- L30 ` public(MountPark)`
- L39 `  (if)`
- L41 `  (for)`
- L48 ` void(setData)`
- L55 `  (for)`
- L63 `  (for)`
- L84 ` int(getPriceBase)`
- L88 ` void(setDoor)`
- L92 ` int(getMountcell)`
- L96 ` void(setMountCell)`
- L100 ` void(setCellObject)`
- L104 ` int(getOwner)`
- L108 ` void(setOwner)`
- L112 ` int(getSize)`
- L116 ` Guild(getGuild)`
- L120 ` void(setGuild)`
- L124 ` int(getMap)`
- L128 ` int(getCell)`
- L132 ` int(getPrice)`
- L136 ` void(setPrice)`
- L140 ` int(getPlaceOfSpawn)`
- L144 ` int(getMaxObject)`
- L148 ` int(getDoor)`
- L152 ` ArrayList<Integer>(getCellOfObject)`
- L156 ` boolean(hasEtableFull)`
- L158 `  (if)`
- L168 ` boolean(hasEnclosFull)`
- L170 `  (if)`
- L180 ` void(addCellObject)`
- L188 ` void(parseBreedObjects)`
- L190 `  (if)`
- L191 `  (for)`
- L203 ` void(parseDurabilityObjects)`
- L205 `  (if)`
- L206 `  (for)`
- L217 ` String(parseStringCellObject)`
- L221 `  (for)`
- L231 ` java.util.Map<Integer, Integer>(getCellAndObject)`
- L235 ` void(addObject)`
- L237 `  (if)`
- L251 ` boolean(delObject)`
- L260 ` java.util.Map<Integer, java.util.Map<Integer, Integer>>(getObjDurab)`
- L264 ` java.util.Map<Integer, java.util.Map<Integer, Integer>>(getObject)`
- L268 ` void(addRaising)`
- L272 ` void(delRaising)`
- L277 ` CopyOnWriteArrayList<Integer>(getListOfRaising)`
- L281 ` ArrayList<Mount>(getEtable)`
- L285 ` Mount(containsMountInList)`
- L287 `  (if)`
- L288 `  (for)`
- L295 `synchronized void(startMoveMounts)`
- L297 `  (if)`
- L299 `  (for)`
- L301 `  (if)`
- L307 ` String(getStringObject)`
- L314 `  (for)`
- L325 ` String(getStringObjDurab)`
- L332 `  (for)`
- L337 `  (for)`
- L345 ` String(parseRaisingToString)`
- L352 `  (for)`
- L360 ` String(parseEtableToString)`
- L363 `  (for)`

#### `org/starloco/locos/entity/map/Trunk.java` — 419 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Trunk
Fonctions :
- L33 ` public(Trunk)`
- L40 `static void(closeCode)`
- L44 `static Optional<Trunk>(getTrunkIdByCoord)`
- L51 `static void(lock)`
- L56 `  (if)`
- L65 `static void(open)`
- L84 `static Stream<Trunk>(getTrunksByHouse)`
- L88 ` void(setObjects)`
- L90 `  (for)`
- L102 ` int(getId)`
- L106 ` void(setId)`
- L110 ` int(getHouseId)`
- L114 ` void(setHouseId)`
- L118 ` int(getMapId)`
- L122 ` void(setMapId)`
- L126 ` int(getCellId)`
- L130 ` void(setCellId)`
- L134 ` String(getKey)`
- L138 ` void(setKey)`
- L142 ` int(getOwnerId)`
- L146 ` void(setOwnerId)`
- L150 ` long(getKamas)`
- L154 ` void(setKamas)`
- L158 ` Player(getPlayer)`
- L162 ` void(setPlayer)`
- L166 ` Map<Integer, GameObject>(getObject)`
- L170 ` void(setObject)`
- L174 ` void(Lock)`
- L179 ` void(enter)`
- L205 ` String(parseToTrunkPacket)`
- L215 ` void(addInTrunk)`
- L246 `  (if)`
- L274 `  (if)`
- L309 ` void(removeFromTrunk)`
- L330 `  (if)`
- L360 `  (if)`
- L395 ` GameObject(getSimilarTrunkItem)`
- L402 ` String(parseTrunkObjetsToDB)`
- L405 `  (for)`
- L411 ` void(moveTrunkToBank)`

#### `org/starloco/locos/entity/monster/MobGroupDef.java` — 53 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class MobGroupDef
Fonctions :
- L15 ` public(MobGroupDef)`
- L20 ` List<MonsterGrade>(randomize)`
- L31 `static Mapper(get)`
- L32 ` f(from)`
- L47 ` t(to)`

#### `org/starloco/locos/entity/monster/Monster.java` — 235 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Monster
Fonctions :
- L20 ` public(Monster)`
- L51 `  (if)`
- L93 ` void(setInfos)`
- L159 ` int(getId)`
- L163 ` int(getGfxId)`
- L167 ` int(getAlign)`
- L171 ` String(getColors)`
- L175 ` int(getIa)`
- L179 ` int(getMinKamas)`
- L183 ` int(getMaxKamas)`
- L187 ` Map<Integer, MonsterGrade>(getGrades)`
- L191 ` void(addDrop)`
- L195 ` ArrayList<Drop>(getDrops)`
- L199 ` boolean(isCapturable)`
- L203 ` int(getAggroDistance)`
- L207 ` MonsterGrade(getGradeByLevel)`
- L215 ` MonsterGrade(getRandomGrade)`
- L219 `  (for)`
- L227 ` boolean(isBoss)`
- L231 ` boolean(isArchMonster)`

#### `org/starloco/locos/entity/monster/MonsterGrade.java` — 254 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class MonsterGrade
Fonctions :
- L36 ` public(MonsterGrade)`
- L60 `  (if)`
- L88 `  (if)`
- L96 `  (if)`
- L99 `  (for)`
- L114 `  (if)`
- L121 ` private(MonsterGrade)`
- L141 ` MonsterGrade(getCopy)`
- L146 ` void(refresh)`
- L152 `  (for)`
- L157 `  (for)`
- L163 ` int(getSize)`
- L167 ` Monster(getTemplate)`
- L171 ` int(getGrade)`
- L175 ` int(getLevel)`
- L179 ` int(getPdv)`
- L183 ` void(setPdv)`
- L187 ` int(getInit)`
- L207 ` int(getPa)`
- L211 ` int(getPm)`
- L215 ` int(getBaseXp)`
- L219 ` ArrayList<SpellEffect>(getBuffs)`
- L223 ` Stats(getStats)`
- L227 `  (if)`
- L245 ` Map<Integer, Spell.SortStats>(getSpells)`
- L249 ` SMobGrade(scripted)`

#### `org/starloco/locos/entity/monster/MonsterGroup.java` — 356 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class MonsterGroup
Fonctions :
- L26 ` public(MonsterGroup)`
- L33 `  (if)`
- L39 `  (for)`
- L41 `  (if)`
- L72 ` public(MonsterGroup)`
- L82 `  (for)`
- L112 `  (if)`
- L115 `  (for)`
- L122 ` public(MonsterGroup)`
- L130 `  (for)`
- L141 `  (for)`
- L164 ` public(MonsterGroup)`
- L168 `  (if)`
- L177 `  (for)`
- L189 `static List<Pair<Integer, List<Integer>>>(parseMobGroupLevels)`
- L208 ` void(setSubArea)`
- L212 ` void(changeAgro)`
- L214 `  (if)`
- L215 `  (if)`
- L219 `  (if)`
- L226 ` void(removeAgro)`
- L229 `  (for)`
- L231 `  (if)`
- L232 `  (if)`
- L238 ` boolean(haveMineur)`
- L240 `  (for)`
- L242 `  (if)`
- L248 ` int(getId)`
- L252 ` int(getCellId)`
- L256 ` void(setCellId)`
- L260 ` int(getOrientation)`
- L264 ` void(setOrientation)`
- L268 ` int(getAlignement)`
- L272 ` void(addStarBonus)`
- L276 ` int(getStarBonus)`
- L280 ` void(setStarBonus)`
- L284 ` int(getAggroDistance)`
- L288 ` boolean(isFix)`
- L292 ` void(setIsFix)`
- L296 ` Map<Integer, MonsterGrade>(getMobs)`
- L300 ` void(setMobs)`
- L304 ` String(getCondition)`
- L308 ` void(setCondition)`
- L312 ` void(startCondTimer)`
- L315 ` void(run)`
- L320 ` ArrayList<GameObject>(getObjects)`
- L328 ` String(encodeGM)`
- L331 `  (if)`
- L335 `  (for)`
- L337 `  (if)`

#### `org/starloco/locos/entity/monster/boss/Bandit.java` — 139 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Bandit
Fonctions :
- L19 ` public(Bandit)`
- L21 `  (if)`
- L22 `  (for)`
- L33 `  (if)`
- L39 `  (if)`
- L41 `  (for)`
- L60 `static Bandit(getBandits)`
- L64 `static void(run)`
- L67 `  (if)`
- L72 `  (if)`
- L77 `  (if)`
- L85 `static void(pop)`
- L93 `  (for)`
- L109 ` ArrayList<Monster>(getMonsters)`
- L113 ` ArrayList<GameMap>(getMaps)`
- L117 ` long(getTime)`
- L121 ` void(setTime)`
- L125 ` void(setPop)`
- L129 ` String(parseMobs)`
- L132 `  (for)`

#### `org/starloco/locos/entity/monster/boss/MaitreCorbac.java` — 67 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class MaitreCorbac
Fonctions :
- L15 ` public(MaitreCorbac)`
- L19 ` void(repop)`
- L32 `  (while)`
- L41 ` int(check)`
- L43 `  (switch)`

#### `org/starloco/locos/entity/mount/Generation.java` — 40 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Generation
Fonctions :
- L4 `static int(getPods)`
- L8 `static int(getEnergy)`
- L12 `static int(getMaturity)`
- L16 `static short(getTimeGestation)`
- L20 `static short(getLearningRate)`
- L22 ` 	(switch)`

#### `org/starloco/locos/entity/mount/Mount.java` — 1150 lignes
Rôle : Dragodinde/monture : stats, reproduction, énergie, fatigue, sérialisation.
Classe(s) : class Mount
Fonctions :
- L26 ` d(update)`
- L28 `  (if)`
- L29 `  (for)`
- L36 ` d(get)`
- L65 ` public(Mount)`
- L89 ` public(Mount)`
- L113 ` 	(if)`
- L115 ` 	(if)`
- L136 ` public(Mount)`
- L163 ` 	(for)`
- L179 `static int(checkCanKen)`
- L181 ` 	(if)`
- L183 ` 	(for)`
- L187 `  (if)`
- L188 ` 	(if)`
- L189 ` 	(if)`
- L197 ` 	(if)`
- L201 ` 	(if)`
- L217 ` 	(if)`
- L221 ` 	(if)`
- L237 `synchronized void(checkBaby)`
- L242 `  (if)`
- L268 ` 	(if)`
- L272 ` 	(if)`
- L289 ` int(getId)`
- L292 ` void(setId)`
- L296 ` int(getColor)`
- L300 ` void(setColor)`
- L304 ` int(getSex)`
- L308 ` int(getSize)`
- L312 ` void(setSize)`
- L316 ` String(getName)`
- L320 ` void(setName)`
- L324 ` int(getLevel)`
- L328 ` void(setLevel)`
- L332 ` long(getExp)`
- L336 ` int(getOwner)`
- L340 ` void(setOwner)`
- L344 ` int(getMapId)`
- L348 ` void(setMapId)`
- L352 ` int(getCellId)`
- L356 ` void(setCellId)`
- L360 ` int(getOrientation)`
- L364 ` void(setOrientation)`
- L368 ` int(getFatigue)`
- L372 `synchronized void(setFatigue)`
- L376 ` int(getEnergy)`
- L380 ` void(setEnergy)`
- L384 ` int(getReproduction)`
- L388 ` int(getAmour)`
- L392 ` int(getEndurance)`
- L396 ` int(getMaturity)`
- L400 ` int(getState)`
- L405 ` void(setState)`
- L409 ` int(getSavage)`
- L413 ` String(getAncestors)`
- L417 ` long(getFecundatedDate)`
- L421 ` void(setFecundatedDate)`
- L426 ` int(getCouple)`
- L430 ` void(setCouple)`
- L434 ` Stats(getStats)`
- L439 ` java.util.Map<Integer, GameObject>(getObjects)`
- L443 ` List<Integer>(getCapacitys)`
- L447 ` String(getStringColor)`
- L456 ` int(isMontable)`
- L463 ` int(isFecund)`
- L469 ` void(setCastrated)`
- L473 ` boolean(isCastrated)`
- L477 ` int(getActualPods)`
- L484 ` int(getMaxPods)`
- L488 ` void(addXp)`
- L492 ` 	(while)`
- L496 ` void(addLvl)`
- L501 ` void(stateMale)`
- L506 ` void(stateFemale)`
- L511 ` void(setMaxEnergy)`
- L515 ` int(getMaxEnergy)`
- L519 ` void(setMaxMaturity)`
- L523 ` int(getMaxMaturity)`
- L527 ` void(aumFatige)`
- L532 ` void(aumEndurance)`
- L538 ` void(aumMaturity)`
- L540 ` 	(if)`
- L544 ` 	(if)`
- L568 ` void(aumAmor)`
- L574 ` void(aumState)`
- L579 ` void(aumEnergy)`
- L585 ` void(aumReproduction)`
- L589 ` void(resFatige)`
- L594 ` void(resAmor)`
- L599 ` void(resEndurance)`
- L604 ` void(resState)`
- L609 ` void(setToMax)`
- L618 ` double(getBonusFatigue)`
- L636 `synchronized void(moveMounts)`
- L662 ` 	(if)`
- L666 ` 	(if)`
- L669 ` 	(if)`
- L678 ` 	(if)`
- L682 ` 	(if)`
- L691 ` 	(if)`
- L695 ` 	(if)`
- L703 ` 	(if)`
- L707 ` 	(if)`
- L716 ` 	(if)`
- L719 ` 	(if)`
- L728 ` 	(if)`
- L732 ` 	(if)`
- L741 ` 	(if)`
- L762 ` 	(if)`
- L771 ` 	(if)`
- L796 `synchronized void(moveMountsAuto)`
- L818 ` 	(if)`
- L824 ` 	(if)`
- L833 ` 	(if)`
- L837 ` 	(if)`
- L846 ` 	(if)`
- L850 ` 	(if)`
- L858 ` 	(if)`
- L862 ` 	(if)`
- L871 ` 	(if)`
- L874 ` 	(if)`
- L883 ` 	(if)`
- L887 ` 	(if)`
- L898 ` 	(if)`
- L907 ` 	(if)`
- L924 `  (if)`
- L936 ` void(addObject)`
- L943 ` 	(if)`
- L958 ` 	(if)`
- L982 ` 	(if)`
- L1007 ` void(removeObject)`
- L1013 ` 	(if)`
- L1025 ` 	(if)`
- L1055 ` 	(if)`
- L1084 ` GameObject(getSimilarObject)`
- L1092 ` String(convertStatsToString)`
- L1095 ` 	(for)`
- L1104 ` String(parse)`
- L1108 ` String(parseToGM)`
- L1123 ` String(parseToMountObjects)`
- L1130 ` String(parseObjectsToString)`
- L1137 ` String(parseCapacitysToString)`
- L1144 ` String(parseExp)`

#### `org/starloco/locos/entity/npc/Npc.java` — 84 lignes
Rôle : PNJ/dialogue : template, réponses, actions associées.
Classe(s) : class Npc
Fonctions :
- L13 ` public(Npc)`
- L20 ` int(getId)`
- L24 ` int(getCellId)`
- L28 ` void(setCellId)`
- L32 ` int(getOrientation)`
- L36 ` void(setOrientation)`
- L40 ` NpcTemplate(getTemplate)`
- L44 ` void(onCreateDialog)`
- L55 ` String(encodeGM)`

#### `org/starloco/locos/entity/npc/NpcAnswer.java` — 53 lignes
Rôle : PNJ/dialogue : template, réponses, actions associées.
Classe(s) : class NpcAnswer
Fonctions :
- L12 ` public(NpcAnswer)`
- L16 ` int(getId)`
- L20 ` ArrayList<Action>(getActions)`
- L24 ` void(setActions)`
- L28 ` void(addAction)`
- L39 ` boolean(apply)`
- L46 ` boolean(isAnotherDialog)`

#### `org/starloco/locos/entity/npc/NpcMovable.java` — 120 lignes
Rôle : PNJ/dialogue : template, réponses, actions associées.
Classe(s) : class NpcMovable extends Npc
Fonctions :
- L18 ` public(NpcMovable)`
- L25 ` void(move)`
- L32 `  (if)`
- L68 ` 	(if)`
- L75 `static String(inverseOfPath)`
- L85 ` 	(switch)`
- L97 `static String(getPath)`
- L106 `static char(getDirByChar)`
- L108 ` 	(switch)`
- L116 `static void(moveAll)`

#### `org/starloco/locos/entity/npc/NpcTemplate.java` — 323 lignes
Rôle : PNJ/dialogue : template, réponses, actions associées.
Classe(s) : class NpcTemplate
Fonctions :
- L29 ` public(NpcTemplate)`
- L48 ` int(getId)`
- L53 ` int(getGfxId)`
- L57 ` int(getScaleX)`
- L61 ` int(getScaleY)`
- L65 ` int(getSex)`
- L69 ` int(getColor1)`
- L73 ` int(getColor2)`
- L77 ` int(getColor3)`
- L81 ` String(encodeAccessories)`
- L85 ` int(getCustomArtWork)`
- L89 ` boolean(isBankClerk)`
- L91 `  (switch)`
- L102 ` void(onCreateDialog)`
- L106 ` void(onDialog)`
- L110 ` List<SaleOffer>(salesList)`
- L112 `  (if)`
- L142 ` byte(getFlags)`
- L146 ` int(getExtraClip)`
- L148 `  (if)`
- L161 ` Couple<Integer,Integer>(barterOutcome)`
- L163 `  (if)`
- L191 ` public(LegacyData)`
- L194 `  (if)`
- L196 `  (for)`
- L208 `  (if)`
- L210 `  (for)`
- L221 `  (if)`
- L225 `  (for)`
- L230 `  (for)`
- L235 `  (for)`
- L248 ` int(getInitQuestionId)`
- L250 `  (if)`
- L256 ` String(getPath)`
- L260 ` List<ObjectTemplate>(getAllItem)`
- L264 ` boolean(addItemVendor)`
- L271 ` boolean(haveItem)`
- L275 ` ArrayList<Couple<Integer, Integer>>(checkGetObjects)`
- L281 `  (for)`
- L284 `  (for)`
- L286 `  (for)`
- L288 `  (if)`
- L291 `  (if)`
- L299 `  (if)`
- L307 `  (if)`
- L309 `  (if)`

#### `org/starloco/locos/entity/pet/Pet.java` — 236 lignes
Rôle : Template familier : nourriture autorisée, stats gagnées, max, gain, jet, dead template.
Classe(s) : class Pet
Fonctions :
- L25 ` public(Pet)`
- L38 ` int(getTemplateId)`
- L42 ` int(getType)`
- L46 ` String(getGap)`
- L50 ` String(getStatsUp)`
- L54 ` int(getMax)`
- L58 ` int(getGain)`
- L62 ` int(getDeadTemplate)`
- L66 ` int(getEpo)`
- L70 ` Map<Integer, ArrayList<Map<Integer, Integer>>>(getMonsters)`
- L74 ` int(getNumbMonster)`
- L76 `  (for)`
- L77 `  (if)`
- L78 `  (for)`
- L79 `  (for)`
- L80 `  (if)`
- L89 ` void(decompileStatsUpItem)`
- L91 `  (if)`
- L99 `  (for)`
- L116 `  (for)`
- L134 `  (for)`
- L137 `  (for)`
- L138 `  (if)`
- L157 `  (for)`
- L160 `  (for)`
- L161 `  (if)`
- L175 ` boolean(canEat)`
- L177 `  (if)`
- L198 ` int(statsIdByEat)`
- L200 `  (if)`
- L221 ` Map<Integer, String>(generateNewtxtStatsForPets)`
- L229 ` String(getJet)`

#### `org/starloco/locos/entity/pet/PetEntry.java` — 485 lignes
Rôle : Instance/suivi familier : repas, corpulence, PDV, EPO, mort, mise à jour stats.
Classe(s) : class PetEntry
Fonctions :
- L30 ` public(PetEntry)`
- L42 ` int(getObjectId)`
- L46 ` int(getTemplate)`
- L50 ` long(getLastEatDate)`
- L54 ` int(getQuaEat)`
- L58 ` int(getPdv)`
- L62 ` int(getCorpulence)`
- L66 ` boolean(getIsEupeoh)`
- L70 ` String(parseLastEatDate)`
- L92 ` int(parseCorpulence)`
- L98 ` int(getCurrentStatsPoids)`
- L109 `  (for)`
- L139 ` int(getMaxStat)`
- L143 ` void(looseFight)`
- L155 `  (if)`
- L170 `  (if)`
- L180 ` void(eat)`
- L199 `  (if)`
- L203 `  (if)`
- L224 `  (if)`
- L233 `  (if)`
- L237 `  (if)`
- L259 `  (if)`
- L263 `  (if)`
- L277 `  (if)`
- L289 `  (if)`
- L297 `  (if)`
- L307 ` void(eatSouls)`
- L317 `  (for)`
- L320 `  (if)`
- L325 `  (if)`
- L334 `  (for)`
- L335 `  (for)`
- L336 `  (for)`
- L339 `  (for)`
- L346 `  (if)`
- L369 ` void(updatePets)`
- L388 `  (if)`
- L402 `  (if)`
- L415 `  (if)`
- L426 ` void(resurrection)`
- L444 ` void(restoreLife)`
- L452 `  (if)`
- L470 ` void(giveEpo)`

#### `org/starloco/locos/event/EventManager.java` — 265 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class EventManager extends Updatable<Long>, enum State
Fonctions :
- L28 `static EventManager(getInstance)`
- L44 ` private(EventManager)`
- L49 ` Event[](getEvents)`
- L53 ` State(getState)`
- L57 ` Event(getCurrentEvent)`
- L61 ` List<Player>(getParticipants)`
- L65 ` void(startNewEvent)`
- L68 `  (if)`
- L70 `  (if)`
- L78 `  (if)`
- L91 `synchronized void(startCurrentEvent)`
- L96 `  (if)`
- L106 ` void(finishCurrentEvent)`
- L119 `synchronized byte(subscribe)`
- L121 `  (if)`
- L124 `  (if)`
- L125 `  (if)`
- L135 `  (if)`
- L148 ` boolean(hasSameIP)`
- L150 `  (if)`
- L155 `  (for)`
- L156 `  (if)`
- L163 ` boolean(hasEnoughPlayers)`
- L170 ` d(update)`
- L173 `  (if)`
- L174 `  (if)`
- L176 `  (if)`
- L187 `  (if)`
- L191 `  (for)`
- L198 ` g(get)`
- L203 ` boolean(moveAllPlayersToEventMap)`
- L209 `  (while)`
- L212 `  (if)`
- L217 `  (if)`
- L223 `  (if)`
- L225 `  (if)`
- L234 `  (while)`
- L237 `  (if)`
- L256 `static boolean(isInEvent)`

#### `org/starloco/locos/event/IEvent.java` — 19 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : interface IEvent
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/event/type/Event.java` — 58 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Event implements IEvent
Fonctions :
- L16 ` public(Event)`
- L23 ` byte(getEventId)`
- L27 ` byte(getMaxPlayers)`
- L31 ` String(getEventName)`
- L35 ` String(getDescription)`
- L39 ` GameMap(getMap)`
- L43 `static void(wait)`
- L46 `  (while)`

#### `org/starloco/locos/event/type/EventFindMe.java` — 104 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class EventFindMe extends Event
Fonctions :
- L27 ` public(EventFindMe)`
- L31 ` GameMap(getMap)`
- L35 ` GameCase(getCell)`
- L39 ` d(prepare)`
- L47 ` GameMap(getRandomMap)`
- L56 ` d(perform)`
- L60 `  (for)`
- L66 ` d(execute)`
- L69 `  (if)`
- L82 ` d(close)`
- L85 `  (for)`
- L90 ` n(onReceivePacket)`
- L95 ` e(getEmptyCellForPlayer)`
- L100 ` d(kickPlayer)`

#### `org/starloco/locos/event/type/EventSmiley.java` — 252 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class EventSmiley extends Event
Fonctions :
- L30 ` public(EventSmiley)`
- L35 ` d(prepare)`
- L42 `  (if)`
- L49 `  (while)`
- L56 ` d(perform)`
- L83 ` d(execute)`
- L96 `  (while)`
- L103 `  (for)`
- L123 `  (for)`
- L125 `  (if)`
- L128 `  (for)`
- L130 `  (if)`
- L139 `  (if)`
- L153 `  (if)`
- L161 ` d(close)`
- L164 `  (if)`
- L170 `  (if)`
- L173 `  (if)`
- L190 ` GameCase(getEmptyCellForPlayer)`
- L194 ` d(kickPlayer)`
- L199 `  (while)`
- L202 `  (if)`
- L213 ` n(onReceivePacket)`
- L216 `  (if)`
- L218 `  (for)`
- L219 `  (if)`
- L230 ` void(initializeTurn)`
- L233 `  (for)`
- L237 ` void(moveAnimatorToCellId)`
- L246 `  (if)`

#### `org/starloco/locos/eventbus/AccountEventHandler.java` — 13 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class AccountEventHandler
Fonctions :
- L7 ` d(onQueue)`

#### `org/starloco/locos/eventbus/AsyncMessageEvent.java` — 26 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class AsyncMessageEvent <T> extends AbstractEventMessageDispatcher<T>
Fonctions :
- L12 ` public(AsyncMessageEvent)`
- L16 ` public(AsyncMessageEvent)`
- L20 ` d(publish)`

#### `org/starloco/locos/eventbus/SyncMessageEvent.java` — 13 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class SyncMessageEvent <T> extends AbstractEventMessageDispatcher<T>
Fonctions :
- L7 ` d(publish)`

#### `org/starloco/locos/exchange/ExchangeClient.java` — 96 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ExchangeClient
Fonctions :
- L22 ` public(ExchangeClient)`
- L28 ` void(setIoSession)`
- L32 ` IoSession(getIoSession)`
- L36 ` ConnectFuture(getConnectFuture)`
- L40 ` void(initialize)`
- L51 `  (if)`
- L61 ` void(restart)`
- L73 ` void(stop)`
- L84 ` void(send)`
- L89 `static IoBuffer(StringToIoBuffer)`

#### `org/starloco/locos/exchange/ExchangeHandler.java` — 44 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ExchangeHandler extends IoHandlerAdapter
Fonctions :
- L9 ` d(sessionCreated)`
- L14 ` d(messageReceived)`
- L21 ` d(messageSent)`
- L26 ` d(sessionClosed)`
- L31 ` d(exceptionCaught)`
- L36 `static String(ioBufferToString)`

#### `org/starloco/locos/exchange/ExchangePacketHandler.java` — 110 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ExchangePacketHandler
Fonctions :
- L18 `static void(parser)`
- L22 `  (switch)`
- L24 `  (if)`
- L31 `  (switch)`
- L33 `  (if)`
- L40 `  (switch)`
- L61 `  (switch)`
- L65 `  (if)`
- L81 `  (if)`
- L94 `  (if)`
- L96 `  (if)`

#### `org/starloco/locos/factory/DofusMessageFactory.java` — 42 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class DofusMessageFactory
Fonctions :
- L18 ` void(init)`
- L22 `  (for)`
- L24 `  (if)`
- L31 `static AbstractDofusMessage(getMessage)`

#### `org/starloco/locos/factory/EventDispatcherFactory.java` — 48 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class EventDispatcherFactory
Fonctions :
- L22 ` public(EventDispatcherFactory)`
- L26 ` void(init)`
- L43 `static void(dispatch)`

#### `org/starloco/locos/fight/Challenge.java` — 829 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Challenge
Fonctions :
- L24 ` public(Challenge)`
- L34 ` int(getType)`
- L38 ` boolean(getAlive)`
- L42 ` int(getXp)`
- L46 ` int(getDrop)`
- L50 ` boolean(getWin)`
- L54 ` boolean(loose)`
- L58 ` String(getPacketEndFight)`
- L62 ` void(challengeWin)`
- L68 ` void(challengeLoose)`
- L79 ` void(challengeSpecLoose)`
- L85 ` String(parseToPacket)`
- L89 `  (if)`
- L97 ` void(showCibleToPerso)`
- L106 ` void(showCibleToFight)`
- L111 `  (for)`
- L121 ` void(fightStart)`
- L125 `  (switch)`
- L135 `  (for)`
- L151 `  (if)`
- L164 `  (for)`
- L167 `  (if)`
- L177 ` void(fightEnd)`
- L181 `  (switch)`
- L184 `  (for)`
- L194 ` void(onFighterDie)`
- L198 `  (switch)`
- L211 ` void(onFighterAttacked)`
- L215 `  (switch)`
- L217 `  (if)`
- L224 `  (if)`
- L233 ` void(onFightersAttacked)`
- L244 `  (switch)`
- L256 `  (switch)`
- L267 `  (if)`
- L271 `  (if)`
- L296 `  (for)`
- L297 `  (if)`
- L307 `  (for)`
- L308 `  (if)`
- L318 `  (for)`
- L319 `  (if)`
- L330 `  (for)`
- L331 `  (if)`
- L341 `  (for)`
- L342 `  (if)`
- L361 `  (for)`
- L362 `  (if)`
- L373 `  (for)`
- L374 `  (if)`
- L390 ` void(onMobDie)`
- L405 `  (switch)`
- L415 `  (if)`
- L427 `  (if)`
- L436 `  (if)`
- L444 `  (if)`
- L450 `  (if)`
- L451 `  (if)`
- L488 `  (if)`
- L490 `  (for)`
- L504 `  (if)`
- L518 `  (for)`
- L519 `  (if)`
- L537 `  (if)`
- L544 `  (for)`
- L547 `  (if)`
- L568 `  (if)`
- L574 `  (for)`
- L577 `  (if)`
- L588 ` void(onPlayerMove)`
- L592 `  (switch)`
- L603 ` void(onPlayerAction)`
- L610 `  (switch)`
- L627 ` void(onPlayerCac)`
- L632 `  (switch)`
- L648 ` void(onPlayerSpell)`
- L654 `  (switch)`
- L665 ` void(onPlayerStartTurn)`
- L669 `  (switch)`
- L690 `  (while)`
- L691 `  (if)`
- L711 `  (for)`
- L727 `  (if)`
- L739 ` void(onPlayerEndTurn)`
- L746 `  (switch)`

#### `org/starloco/locos/fight/CloneFighter.java` — 76 lignes
Rôle : Combattant abstrait : PDV/PA/PM, buffs, états, stats, invocation, dégâts, déplacement.
Classe(s) : class CloneFighter extends Fighter
Fonctions :
- L12 ` protected(CloneFighter)`
- L16 ` t(getType)`
- L21 ` t(getLvl)`
- L26 ` t(baseMaxPdv)`
- L31 ` s(getBaseStats)`
- L36 ` t(getDefaultGfx)`
- L41 ` <Spell.SortStats>(spellRankForID)`
- L46 ` g(getPacketsName)`
- L51 ` <String>(getGMPacketParts)`
- L56 ` <Mount>(getMount)`
- L61 ` g(getMountColors)`
- L66 ` n(isInvocation)`
- L71 ` r(getInvocator)`

#### `org/starloco/locos/fight/CollectorFighter.java` — 84 lignes
Rôle : Combattant abstrait : PDV/PA/PM, buffs, états, stats, invocation, dégâts, déplacement.
Classe(s) : class CollectorFighter extends Fighter
Fonctions :
- L14 ` protected(CollectorFighter)`
- L19 ` g(getPacketsName)`
- L24 ` t(getType)`
- L29 ` t(getLvl)`
- L34 ` t(baseMaxPdv)`
- L39 ` s(getBaseStats)`
- L44 ` t(getDefaultGfx)`
- L49 ` <Spell.SortStats>(spellRankForID)`
- L54 ` <String>(getGMPacketParts)`
- L76 ` Collection<GameObject>(collectorDrops)`
- L78 ` r(getCollector)`

#### `org/starloco/locos/fight/Fight.java` — 5796 lignes
Rôle : Combat : timeline, tours, actions, fin de combat, drop/xp, placement, challenges.
Classe(s) : class Fight
Fonctions :
- L109 ` public(Fight)`
- L131 `  (if)`
- L135 `  (if)`
- L173 `  (if)`
- L188 ` public(Fight)`
- L198 `  (for)`
- L203 `  (for)`
- L218 `  (for)`
- L221 `  (if)`
- L234 `  (for)`
- L237 `  (if)`
- L265 ` public(Fight)`
- L277 `  (for)`
- L296 `  (for)`
- L299 `  (if)`
- L336 ` public(Fight)`
- L347 `  (for)`
- L351 `  (if)`
- L374 `  (for)`
- L377 `  (if)`
- L409 ` public(Fight)`
- L438 `  (if)`
- L457 `  (for)`
- L460 `  (if)`
- L509 `  (if)`
- L517 ` public(Fight)`
- L540 `  (if)`
- L559 `  (for)`
- L562 `  (if)`
- L600 `  (for)`
- L610 ` public(Fight)`
- L643 `  (if)`
- L684 `  (if)`
- L699 `static void(FightStateAddFlag)`
- L702 `  (if)`
- L723 ` int(getId)`
- L727 ` void(setId)`
- L731 ` int(getState)`
- L735 ` void(setState)`
- L739 ` int(getGuildId)`
- L743 ` void(setGuildId)`
- L747 ` int(getType)`
- L752 ` void(setType)`
- L756 ` int(getSt1)`
- L760 ` void(setSt1)`
- L764 ` int(getSt2)`
- L768 ` void(setSt2)`
- L772 ` int(getCurPlayer)`
- L776 ` void(setCurPlayer)`
- L780 ` int(getCaptWinner)`
- L784 ` void(setCaptWinner)`
- L788 ` int(getCurFighterPa)`
- L792 ` void(setCurFighterPa)`
- L796 ` int(getCurFighterPm)`
- L800 ` void(setCurFighterPm)`
- L804 ` int(getCurFighterUsedPa)`
- L808 ` void(setCurFighterUsedPa)`
- L812 ` int(getCurFighterUsedPm)`
- L816 ` void(setCurFighterUsedPm)`
- L820 ` Map<Integer, Fighter>(getTeam)`
- L822 `  (switch)`
- L830 ` Map<Integer, Fighter>(getTeam0)`
- L834 ` Map<Integer, Fighter>(getTeam1)`
- L838 ` List<Fighter>(getDeadList)`
- L842 ` boolean(removeDead)`
- L846 ` Map<Integer, Player>(getViewer)`
- L850 ` List<GameCase>(getStart0)`
- L854 ` List<GameCase>(getStart1)`
- L858 ` Map<Integer, Challenge>(getAllChallenges)`
- L862 ` Map<Integer, GameCase>(getRholBack)`
- L866 ` List<Glyph>(getGlyphs)`
- L870 ` List<Trap>(getTraps)`
- L874 ` void(checkTraps)`
- L881 ` void(recursiveCheckTrap)`
- L883 `  (if)`
- L897 ` ArrayList<Fighter>(getCapturer)`
- L901 ` ArrayList<Fighter>(getTrainer)`
- L905 ` long(getStartTime)`
- L909 ` void(setStartTime)`
- L913 ` long(getLaunchTime)`
- L917 ` boolean(isLocked0)`
- L921 ` void(setLocked0)`
- L925 ` boolean(isLocked1)`
- L929 ` void(setLocked1)`
- L933 ` boolean(isOnlyGroup0)`
- L937 ` void(setOnlyGroup0)`
- L941 ` boolean(isOnlyGroup1)`
- L945 ` void(setOnlyGroup1)`
- L949 ` boolean(isHelp0)`
- L953 ` void(setHelp0)`
- L957 ` boolean(isHelp1)`
- L961 ` void(setHelp1)`
- L965 ` boolean(isViewerOk)`
- L969 ` void(setViewerOk)`
- L973 ` boolean(isHaveKnight)`
- L977 ` void(setHaveKnight)`
- L981 ` boolean(isBegin)`
- L985 ` void(setBegin)`
- L989 ` boolean(isCheckTimer)`
- L993 ` String(getCurAction)`
- L997 ` void(setCurAction)`
- L1001 ` MonsterGroup(getMobGroup)`
- L1005 ` void(setMobGroup)`
- L1009 ` Collector(getCollector)`
- L1013 ` Prism(getPrism)`
- L1017 ` void(setPrism)`
- L1021 ` GameMap(getMap)`
- L1025 ` void(setMap)`
- L1029 ` GameMap(getMapOld)`
- L1033 ` Fighter(getInit0)`
- L1037 ` Fighter(getInit1)`
- L1041 ` String(getDefenders)`
- L1045 ` void(setDefenders)`
- L1049 ` int(getTrainerWinner)`
- L1053 ` void(setTrainerWinner)`
- L1057 ` boolean(isFinish)`
- L1061 ` int(getTeamId)`
- L1071 ` int(getOtherTeamId)`
- L1079 ` void(scheduleTimer)`
- L1082 `  (if)`
- L1088 ` void(demorph)`
- L1093 ` void(startFight)`
- L1097 `  (if)`
- L1099 `  (for)`
- L1100 `  (if)`
- L1102 `  (if)`
- L1124 `  (if)`
- L1132 `  (if)`
- L1134 `  (for)`
- L1137 `  (if)`
- L1150 `  (for)`
- L1153 `  (if)`
- L1171 `  (if)`
- L1177 `  (if)`
- L1191 `  (for)`
- L1194 `  (if)`
- L1195 `  (switch)`
- L1219 `  (for)`
- L1221 `  (if)`
- L1222 `  (if)`
- L1232 `  (for)`
- L1234 `  (if)`
- L1235 `  (if)`
- L1236 `  (switch)`
- L1278 `  (for)`
- L1288 `  (for)`
- L1296 `  (for)`
- L1299 `  (if)`
- L1312 ` void(leftFight)`
- L1318 `  (if)`
- L1320 `  (switch)`
- L1331 `  (if)`
- L1346 `  (if)`
- L1356 `  (if)`
- L1362 `  (if)`
- L1365 `  (if)`
- L1379 `  (if)`
- L1404 `  (if)`
- L1414 `  (if)`
- L1419 `  (if)`
- L1428 `  (if)`
- L1431 `  (if)`
- L1441 `  (if)`
- L1443 `  (if)`
- L1445 `  (if)`
- L1446 `  (if)`
- L1457 `  (if)`
- L1459 `  (if)`
- L1460 `  (if)`
- L1467 `  (if)`
- L1481 `  (if)`
- L1502 `  (if)`
- L1533 `  (if)`
- L1543 `  (if)`
- L1545 `  (if)`
- L1555 `  (if)`
- L1557 `  (if)`
- L1559 `  (if)`
- L1560 `  (if)`
- L1571 `  (if)`
- L1573 `  (if)`
- L1574 `  (if)`
- L1617 ` void(onPlayerLooseHonor)`
- L1619 `  (if)`
- L1632 ` void(endFight)`
- L1646 ` boolean(verifIfTeamBoufbowl)`
- L1650 ` boolean(verifTeamBoufbowl)`
- L1652 `  (if)`
- L1669 `  (if)`
- L1681 `  (for)`
- L1686 `  (if)`
- L1695 `  (for)`
- L1707 `  (for)`
- L1731 `  (for)`
- L1733 `  (if)`
- L1742 `  (for)`
- L1764 `  (if)`
- L1793 ` void(startTurn)`
- L1815 `  (if)`
- L1822 `  (if)`
- L1830 `  (if)`
- L1832 `  (if)`
- L1842 `  (if)`
- L1844 `  (if)`
- L1851 `  (if)`
- L1865 `  (for)`
- L1867 `  (if)`
- L1868 `  (if)`
- L1885 `  (if)`
- L1888 `  (if)`
- L1889 `  (if)`
- L1902 `  (if)`
- L1907 `  (if)`
- L1912 `  (if)`
- L1917 `  (if)`
- L1922 `synchronized void(endTurn)`
- L1929 `synchronized void(endTurn)`
- L1940 `  (if)`
- L1944 `  (if)`
- L1961 ` void(newTurn)`
- L1965 `  (for)`
- L1981 `  (if)`
- L1991 `  (if)`
- L1995 `  (if)`
- L2018 `  (if)`
- L2022 `  (if)`
- L2026 `  (for)`
- L2046 ` void(playerPass)`
- L2053 `synchronized void(joinFight)`
- L2060 `  (if)`
- L2067 `  (if)`
- L2072 `  (if)`
- L2077 `  (if)`
- L2080 `  (if)`
- L2083 `  (if)`
- L2084 `  (if)`
- L2090 `  (if)`
- L2092 `  (if)`
- L2096 `  (if)`
- L2103 `  (if)`
- L2104 `  (if)`
- L2109 `  (if)`
- L2133 `  (if)`
- L2139 `  (if)`
- L2141 `  (if)`
- L2142 `  (if)`
- L2148 `  (if)`
- L2150 `  (if)`
- L2154 `  (if)`
- L2161 `  (if)`
- L2162 `  (if)`
- L2167 `  (if)`
- L2207 `  (if)`
- L2217 `synchronized void(joinCollectorFight)`
- L2244 ` void(joinPrismFight)`
- L2252 `  (if)`
- L2282 ` void(joinAsSpectator)`
- L2287 `  (if)`
- L2292 `  (if)`
- L2293 `  (if)`
- L2317 `  (if)`
- L2318 `  (for)`
- L2326 `  (for)`
- L2333 ` void(toggleLockTeam)`
- L2335 `  (if)`
- L2345 ` void(toggleLockSpec)`
- L2347 `  (if)`
- L2349 `  (if)`
- L2368 ` void(toggleOnlyGroup)`
- L2370 `  (if)`
- L2380 ` void(toggleHelp)`
- L2382 `  (if)`
- L2392 ` void(showCaseToTeam)`
- L2398 `  (if)`
- L2407 ` void(showCaseToAll)`
- L2410 `  (for)`
- L2414 `  (for)`
- L2419 `  (for)`
- L2424 ` void(initOrderPlaying)`
- L2438 `  (if)`
- L2441 `  (for)`
- L2445 `  (if)`
- L2454 `  (if)`
- L2456 `  (for)`
- L2460 `  (if)`
- L2468 `  (if)`
- L2471 `  (if)`
- L2472 `  (if)`
- L2476 `  (if)`
- L2481 `  (if)`
- L2485 `  (if)`
- L2498 ` void(tryCaC)`
- L2510 `  (if)`
- L2515 `  (if)`
- L2516 `  (for)`
- L2522 `  (if)`
- L2527 `  (if)`
- L2534 `  (if)`
- L2543 `  (if)`
- L2551 `  (if)`
- L2558 `  (if)`
- L2566 `  (for)`
- L2571 `  (if)`
- L2590 `  (if)`
- L2592 `  (if)`
- L2593 `  (if)`
- L2601 `  (for)`
- L2618 `synchronized int(tryCastSpell)`
- L2621 `  (if)`
- L2629 `  (if)`
- L2644 `  (if)`
- L2648 `  (if)`
- L2684 `  (if)`
- L2687 `  (if)`
- L2704 `  (if)`
- L2710 ` void(forceCastSpellMob)`
- L2719 `  (if)`
- L2723 `  (if)`
- L2752 `  (if)`
- L2755 `  (if)`
- L2768 `  (if)`
- L2773 ` boolean(canCastSpell1)`
- L2782 `  (if)`
- L2784 `  (if)`
- L2791 `  (if)`
- L2797 `  (if)`
- L2799 `  (for)`
- L2800 `  (if)`
- L2801 `  (for)`
- L2802 `  (if)`
- L2823 `  (if)`
- L2829 `  (if)`
- L2850 `  (if)`
- L2860 `  (if)`
- L2887 `  (if)`
- L2900 `  (if)`
- L2918 `  (if)`
- L2931 ` boolean(canLaunchSpell)`
- L2945 ` boolean(canCastSpellMob)`
- L2964 `  (if)`
- L2990 ` boolean(checkKrakenState)`
- L2992 `  (switch)`
- L3004 ` boolean(onFighterMovement)`
- L3020 `  (if)`
- L3022 `  (for)`
- L3023 `  (if)`
- L3026 `  (if)`
- L3049 `  (if)`
- L3065 `  (if)`
- L3068 `  (if)`
- L3077 `  (if)`
- L3080 `  (if)`
- L3101 `  (if)`
- L3108 `  (if)`
- L3114 `  (if)`
- L3129 ` void(onFighterDie)`
- L3139 `  (if)`
- L3141 `  (if)`
- L3149 `  (if)`
- L3152 `  (if)`
- L3153 `  (if)`
- L3158 `  (if)`
- L3162 `  (if)`
- L3172 `  (if)`
- L3178 `  (if)`
- L3192 `  (if)`
- L3195 `  (for)`
- L3201 `  (if)`
- L3206 `  (if)`
- L3207 `  (if)`
- L3226 `  (for)`
- L3232 `  (if)`
- L3235 `  (if)`
- L3237 `  (if)`
- L3255 `  (if)`
- L3257 `  (if)`
- L3261 `  (if)`
- L3263 `  (if)`
- L3271 ` else(if)`
- L3276 `  (if)`
- L3281 `  (if)`
- L3316 `  (if)`
- L3330 `  (if)`
- L3337 `  (if)`
- L3343 `  (for)`
- L3346 `  (for)`
- L3347 `  (switch)`
- L3366 `  (if)`
- L3373 ` ArrayList<Fighter>(getFighters)`
- L3376 `  (if)`
- L3383 `  (if)`
- L3391 ` ArrayList<Fighter>(getTeamFighters)`
- L3403 ` Fighter(getFighterByPerso)`
- L3412 ` GameCase(getRandomCell)`
- L3427 `synchronized void(exchangePlace)`
- L3450 ` boolean(isOccuped)`
- L3454 ` int(getNextLowerFighterGuid)`
- L3458 ` void(addFighterInTeam)`
- L3465 ` void(addChevalier)`
- L3469 `  (for)`
- L3482 `  (for)`
- L3486 `  (for)`
- L3491 `  (if)`
- L3506 ` boolean(onPlayerDisconnection)`
- L3515 `  (if)`
- L3519 `  (if)`
- L3525 `  (if)`
- L3527 `  (if)`
- L3529 `  (for)`
- L3536 `  (if)`
- L3545 ` boolean(onPlayerReconnection)`
- L3571 `  (if)`
- L3583 `  (if)`
- L3592 `  (for)`
- L3600 `  (for)`
- L3607 `  (for)`
- L3616 ` void(sendBuffPacket)`
- L3620 `  (switch)`
- L3648 `  (if)`
- L3652 `  (for)`
- L3653 `  (if)`
- L3690 `  (if)`
- L3692 `  (if)`
- L3695 `  (if)`
- L3698 `  (if)`
- L3704 `  (for)`
- L3706 `  (if)`
- L3711 ` void(verifIfAllReady)`
- L3714 `  (if)`
- L3715 `  (for)`
- L3746 `  (for)`
- L3758 `  (for)`
- L3772 ` boolean(verifIfTeamIsDead)`
- L3775 `  (for)`
- L3778 `  (if)`
- L3785 ` void(verifIfTeamAllDead)`
- L3795 `  (if)`
- L3804 `  (if)`
- L3816 `  (for)`
- L3822 `  (for)`
- L3835 `  (if)`
- L3842 `  (if)`
- L3844 `  (for)`
- L3845 `  (if)`
- L3853 `  (if)`
- L3862 `  (if)`
- L3865 `  (for)`
- L3871 `  (if)`
- L3886 `  (for)`
- L3889 `  (if)`
- L3896 `  (switch)`
- L3901 `  (for)`
- L3903 `  (if)`
- L3911 `  (for)`
- L3914 `  (if)`
- L3931 `  (if)`
- L3934 `  (for)`
- L3936 `  (if)`
- L3950 `  (if)`
- L3969 `  (for)`
- L3970 `  (if)`
- L3982 `  (if)`
- L3985 `  (for)`
- L3989 `  (if)`
- L3999 `  (if)`
- L4028 `  (for)`
- L4037 `  (for)`
- L4040 `  (if)`
- L4041 `  (if)`
- L4042 `  (if)`
- L4056 `  (for)`
- L4058 `  (if)`
- L4066 `  (for)`
- L4087 `  (if)`
- L4090 `  (for)`
- L4091 `  (if)`
- L4105 ` void(earlyEndfightEvent)`
- L4113 ` void(onPlayerWin)`
- L4123 `  (if)`
- L4124 `  (if)`
- L4126 `  (if)`
- L4136 `  (if)`
- L4154 `  (if)`
- L4157 `  (if)`
- L4159 `  (for)`
- L4171 `  (if)`
- L4179 ` void(onPlayerLoose)`
- L4189 `  (if)`
- L4191 `  (if)`
- L4193 `  (if)`
- L4203 `  (if)`
- L4206 `  (if)`
- L4219 `  (if)`
- L4228 `  (if)`
- L4232 `  (if)`
- L4233 `  (if)`
- L4243 `  (if)`
- L4256 ` int(getAlignementOfTraquer)`
- L4265 ` void(onGK)`
- L4275 `  (if)`
- L4285 ` String(getGEBoufbawl)`
- L4294 `  (if)`
- L4311 `  (if)`
- L4326 `  (if)`
- L4334 ` String(getGE)`
- L4341 `  (if)`
- L4360 `  (while)`
- L4370 `  (while)`
- L4378 `  (if)`
- L4393 `  (for)`
- L4403 `  (for)`
- L4404 `  (if)`
- L4407 `  (if)`
- L4408 `  (for)`
- L4409 `  (if)`
- L4420 `  (for)`
- L4439 `  (if)`
- L4445 `  (if)`
- L4467 `  (if)`
- L4468 `  (for)`
- L4479 `  (for)`
- L4495 `  (if)`
- L4497 `  (if)`
- L4507 `  (if)`
- L4539 `  (for)`
- L4546 `  (if)`
- L4576 `  (for)`
- L4578 `  (if)`
- L4598 `  (if)`
- L4605 `  (for)`
- L4613 `  (switch)`
- L4620 `  (if)`
- L4635 `  (while)`
- L4637 `  (for)`
- L4638 `  (if)`
- L4654 `  (if)`
- L4664 `  (if)`
- L4666 `  (for)`
- L4667 `  (if)`
- L4687 `  (if)`
- L4701 `  (if)`
- L4717 `  (if)`
- L4719 `  (switch)`
- L4723 `  (for)`
- L4726 `  (if)`
- L4732 `  (while)`
- L4734 `  (if)`
- L4750 `  (if)`
- L4753 `  (for)`
- L4758 `  (if)`
- L4761 `  (if)`
- L4788 `  (for)`
- L4798 `  (if)`
- L4805 `  (if)`
- L4808 `  (if)`
- L4816 `  (if)`
- L4832 `  (if)`
- L4836 `  (for)`
- L4838 `  (if)`
- L4847 `  (if)`
- L4849 `  (if)`
- L4855 `  (for)`
- L4864 `  (switch)`
- L4871 `  (if)`
- L4879 `  (switch)`
- L4900 `  (switch)`
- L4937 `  (if)`
- L4938 `  (if)`
- L4951 `  (for)`
- L4972 `  (if)`
- L4979 `  (if)`
- L4985 `  (if)`
- L4987 `  (for)`
- L4990 `  (if)`
- L5001 `  (if)`
- L5002 `  (if)`
- L5003 `  (if)`
- L5012 `  (switch)`
- L5024 `  (if)`
- L5031 `  (if)`
- L5041 `  (for)`
- L5051 `  (for)`
- L5063 `  (for)`
- L5065 `  (if)`
- L5093 `  (if)`
- L5094 `  (for)`
- L5106 `  (if)`
- L5116 `  (if)`
- L5118 `  (for)`
- L5120 `  (switch)`
- L5129 `  (if)`
- L5131 `  (if)`
- L5132 `  (if)`
- L5141 `  (switch)`
- L5156 `  (switch)`
- L5169 `  (if)`
- L5172 `  (if)`
- L5173 `  (if)`
- L5197 `  (if)`
- L5212 `  (if)`
- L5216 `  (if)`
- L5218 `  (if)`
- L5226 `  (if)`
- L5239 `  (if)`
- L5252 `  (if)`
- L5264 `  (if)`
- L5342 `  (for)`
- L5352 `  (if)`
- L5362 `  (if)`
- L5369 `  (if)`
- L5391 `  (if)`
- L5455 `  (if)`
- L5457 `  (for)`
- L5460 `  (if)`
- L5468 `  (switch)`
- L5489 `  (switch)`
- L5530 `  (if)`
- L5539 `  (for)`
- L5570 ` ArrayList<Fighter>(insertChestsAfterSummoner)`
- L5579 `  (for)`
- L5580 `  (if)`
- L5584 `  (if)`
- L5592 `  (for)`
- L5593 `  (if)`
- L5597 `  (if)`
- L5607 ` String(getGTL)`
- L5616 ` String(parseFightInfos)`
- L5624 `  (switch)`
- L5677 ` int(getTeamSizeWithoutInvocation)`
- L5683 ` Fighter(getFighterByGameOrder)`
- L5701 ` int(getOrderPlayingSize)`
- L5709 ` boolean(haveFighterInOrdreJeu)`
- L5713 ` List<Fighter>(getOrderPlaying)`
- L5717 ` void(cast)`
- L5720 `  (if)`
- L5721 `  (if)`
- L5734 `static Map<Player, String>(give)`
- L5737 `  (if)`
- L5743 `  (if)`
- L5749 `  (if)`
- L5755 `  (while)`
- L5757 `  (if)`
- L5765 `  (if)`
- L5783 ` void(setMobGroup2)`
- L5787 ` List<Fighter>(getWinners)`
- L5791 ` List<Fighter>(getLosers)`

#### `org/starloco/locos/fight/Fighter.java` — 959 lignes
Rôle : Combattant abstrait : PDV/PA/PM, buffs, états, stats, invocation, dégâts, déplacement.
Classe(s) : class Fighter implements Comparable<Fighter>, Scripted<Object>, Actor, Cloneable
Fonctions :
- L60 ` protected(Fighter)`
- L65 ` void(init)`
- L69 `static Fighter(NewPlayer)`
- L75 `static Fighter(NewCollector)`
- L81 `static Fighter(NewMob)`
- L87 `static Fighter(NewPrism)`
- L93 `static Fighter(NewClone)`
- L99 `static Fighter(NewSummon)`
- L141 ` int(getId)`
- L155 ` r(clone)`
- L158 ` String(getMountColors)`
- L160 ` boolean(canPlay)`
- L167 ` void(setCanPlay)`
- L171 ` Fight(getFight)`
- L175 ` void(initFightBuffs)`
- L177 ` void(send)`
- L181 ` String(xpString)`
- L189 ` Optional<Mount>(getMount)`
- L191 ` int[](getColors)`
- L249 ` int(getTeam)`
- L253 ` void(setTeam)`
- L257 ` int(getTeam2)`
- L261 ` int(getOtherTeam)`
- L265 ` GameCase(getCell)`
- L269 ` void(setCell)`
- L273 ` int(getPdvMax)`
- L277 ` void(removePdvMax)`
- L282 ` int(getPdv)`
- L286 ` void(setPdv)`
- L290 ` void(removePdv)`
- L296 ` void(fullPdv)`
- L300 ` boolean(isFullPdv)`
- L304 ` boolean(isDead)`
- L308 ` void(setIsDead)`
- L312 ` boolean(hasLeft)`
- L316 ` void(setLeft)`
- L320 ` Fighter(getIsHolding)`
- L324 ` void(setIsHolding)`
- L328 ` Fighter(getHoldedBy)`
- L332 ` void(setHoldedBy)`
- L336 ` Fighter(getOldCible)`
- L340 ` void(setOldCible)`
- L344 ` Fighter(getInvocator)`
- L348 ` void(setInvocator)`
- L352 ` boolean(isInvocation)`
- L356 ` boolean(getLevelUp)`
- L360 ` void(setLevelUp)`
- L364 ` void(Disconnect)`
- L372 ` void(Reconnect)`
- L377 ` boolean(isDeconnected)`
- L381 ` int(getTurnRemaining)`
- L385 ` void(setTurnRemaining)`
- L389 ` int(getNbrDisconnection)`
- L393 ` boolean(getTraqued)`
- L397 ` void(setTraqued)`
- L401 ` void(setState)`
- L406 ` int(getState)`
- L410 ` boolean(haveState)`
- L415 ` void(sendState)`
- L421 ` int(nbInvocation)`
- L432 ` boolean(isTrappedOrGlyphed)`
- L436 ` boolean(isTrapped)`
- L440 ` void(setTrapped)`
- L444 ` boolean(isGlyphed)`
- L448 ` void(setGlyphed)`
- L452 ` ArrayList<SpellEffect>(getFightBuff)`
- L456 ` Stats(getFightBuffStats)`
- L463 ` int(getBuffValue)`
- L471 ` SpellEffect(getBuff)`
- L478 ` ArrayList<SpellEffect>(getBuffsByEffectID)`
- L482 ` Stats(getTotalStatsLessBuff)`
- L486 ` boolean(hasBuff)`
- L493 ` SpellEffect(addBuff)`
- L498 `  (if)`
- L503 `  (switch)`
- L519 `  (switch)`
- L528 `  (if)`
- L530 `  (switch)`
- L547 `  (switch)`
- L554 `  (if)`
- L555 `  (switch)`
- L567 `  (if)`
- L571 `  (if)`
- L576 ` void(debuff)`
- L579 `  (while)`
- L584 `  (switch)`
- L594 `  (if)`
- L597 `  (if)`
- L599 `  (switch)`
- L626 `  (if)`
- L634 `  (if)`
- L639 ` void(refreshEndTurnShield)`
- L641 `  (for)`
- L643 `  (while)`
- L646 `  (if)`
- L648 `  (switch)`
- L651 `  (if)`
- L652 `  (if)`
- L662 ` void(refreshEndTurnBuff)`
- L666 `  (while)`
- L672 `  (switch)`
- L677 `  (if)`
- L680 `  (switch)`
- L685 `  (for)`
- L693 `  (if)`
- L697 `  (if)`
- L711 `  (if)`
- L721 ` void(applyBeginningTurnBuff)`
- L724 `  (for)`
- L729 ` ArrayList<LaunchedSpell>(getLaunchedSorts)`
- L733 ` void(refreshLaunchedSort)`
- L737 ` void(addLaunchedSort)`
- L742 ` Stats(getTotalStats)`
- L746 ` int(getMaitriseDmg)`
- L754 ` boolean(getSpellValueBool)`
- L761 ` boolean(critStrikeCheck)`
- L775 ` int(criticalStrikeModifier)`
- L780 ` boolean(critStrikeCheck)`
- L787 ` int(getInitiative)`
- L791 ` int(getPa)`
- L795 ` int(getPm)`
- L799 ` int(getPros)`
- L803 ` int(getCurPa)`
- L807 ` void(setCurPa)`
- L811 ` int(getCurPm)`
- L815 ` void(setCurPm)`
- L819 ` boolean(canLaunchSpell)`
- L823 ` void(unHide)`
- L828 `  (switch)`
- L839 `  (for)`
- L847 ` boolean(isHidden)`
- L851 ` Map<Integer, Integer>(getChatiValue)`
- L855 ` String(getGmPacket)`
- L872 ` boolean(isStatic)`
- L880 ` t(compareTo)`
- L886 ` t(scripted)`
- L895 ` g(Id)`
- L900 ` g(name)`
- L905 ` void(setStatic)`
- L909 ` void(modNbrInvoc)`
- L913 ` int(getNbrInvoc)`
- L917 ` World.Couple<Byte, Long>(getKilledBy)`
- L921 ` void(setKilledBy)`
- L925 ` Optional<T>(as)`
- L929 ` boolean(aiControlled)`
- L931 ` boolean(canLoot)`
- L933 ` int(minKamasReward)`
- L935 ` int(maxKamasReward)`
- L936 ` Stream<World.Drop>(drops)`
- L940 ` r(getPlayer)`
- L944 ` e(getMob)`
- L949 ` r(getCollector)`
- L954 ` m(getPrism)`

#### `org/starloco/locos/fight/MobFighter.java` — 96 lignes
Rôle : Combattant abstrait : PDV/PA/PM, buffs, états, stats, invocation, dégâts, déplacement.
Classe(s) : class MobFighter extends Fighter
Fonctions :
- L17 ` protected(MobFighter)`
- L22 ` g(getPacketsName)`
- L27 ` t(getType)`
- L32 ` t(getLvl)`
- L37 ` t(baseMaxPdv)`
- L42 ` s(getBaseStats)`
- L47 ` t(getDefaultGfx)`
- L52 ` <Spell.SortStats>(spellRankForID)`
- L57 ` Monster(getTemplate)`
- L61 ` <String>(getGMPacketParts)`
- L75 ` n(canLoot)`
- L79 ` t(minKamasReward)`
- L82 ` t(maxKamasReward)`
- L85 ` <World.Drop>(drops)`
- L88 ` Object(scripted)`
- L92 ` e(getMob)`

#### `org/starloco/locos/fight/PlayerFighter.java` — 163 lignes
Rôle : Combattant abstrait : PDV/PA/PM, buffs, états, stats, invocation, dégâts, déplacement.
Classe(s) : class PlayerFighter extends Fighter
Fonctions :
- L19 ` protected(PlayerFighter)`
- L24 ` g(getPacketsName)`
- L29 ` t(getType)`
- L34 ` t(getLvl)`
- L39 ` t(baseMaxPdv)`
- L44 ` s(getBaseStats)`
- L49 ` t(getDefaultGfx)`
- L54 ` void(sendStats)`
- L58 ` d(initFightBuffs)`
- L63 ` d(send)`
- L69 ` <Spell.SortStats>(spellRankForID)`
- L74 ` t(criticalStrikeModifier)`
- L81 `  (if)`
- L88 ` g(xpString)`
- L96 ` Stream<String>(getGMPacketParts)`
- L104 `  (if)`
- L133 ` <Mount>(getMount)`
- L138 ` int[](getColors)`
- L142 ` g(getMountColors)`
- L147 ` n(aiControlled)`
- L150 ` n(canLoot)`
- L153 ` Object(scripted)`
- L157 ` r(getPlayer)`

#### `org/starloco/locos/fight/PrismFighter.java` — 80 lignes
Rôle : Combattant abstrait : PDV/PA/PM, buffs, états, stats, invocation, dégâts, déplacement.
Classe(s) : class PrismFighter extends Fighter
Fonctions :
- L13 ` protected(PrismFighter)`
- L18 ` g(getPacketsName)`
- L23 ` <String>(getGMPacketParts)`
- L44 ` t(getType)`
- L49 ` t(getLvl)`
- L54 ` t(baseMaxPdv)`
- L59 ` s(getBaseStats)`
- L64 ` t(getDefaultGfx)`
- L69 ` <Spell.SortStats>(spellRankForID)`
- L74 ` m(getPrism)`

#### `org/starloco/locos/fight/SummonFighter.java` — 53 lignes
Rôle : Combattant abstrait : PDV/PA/PM, buffs, états, stats, invocation, dégâts, déplacement.
Classe(s) : class SummonFighter extends MobFighter
Fonctions :
- L13 ` protected(SummonFighter)`
- L18 ` s(getBaseStats)`

#### `org/starloco/locos/fight/ia/AbstractEasyIA.java` — 127 lignes
Rôle : IA historique Ancestrar : décisions mobs/invocations.
Classe(s) : class AbstractEasyIA extends AbstractIA
Fonctions :
- L21 ` public(AbstractEasyIA)`
- L32 ` void(setNextParams)`
- L38 ` Function(get)`
- L42 ` d(apply)`
- L45 `  (if)`
- L46 `  (if)`
- L50 `  (if)`
- L68 ` List<Spell.SortStats>(getListSpellOf)`
- L71 `  (for)`
- L74 `  (switch)`
- L97 `  (if)`
- L106 ` List<Spell.SortStats>(getAttacksSpells)`
- L110 ` List<Spell.SortStats>(getFriendBuffsSpells)`
- L114 ` List<Spell.SortStats>(getHealsSpells)`
- L118 ` List<Spell.SortStats>(getTeleportations)`
- L122 ` List<Spell.SortStats>(getInvocations)`

#### `org/starloco/locos/fight/ia/AbstractIA.java` — 61 lignes
Rôle : IA historique Ancestrar : décisions mobs/invocations.
Classe(s) : class AbstractIA implements IA
Fonctions :
- L22 ` public(AbstractIA)`
- L28 ` Fight(getFight)`
- L32 ` Fighter(getFighter)`
- L36 ` boolean(isStop)`
- L40 ` void(setStop)`
- L44 ` void(endTurn)`
- L48 ` void(decrementCount)`
- L51 `  (if)`
- L57 ` void(addNext)`

#### `org/starloco/locos/fight/ia/AbstractNeedSpell.java` — 68 lignes
Rôle : Définition de sort et niveaux : coûts, portée, effets, conditions de lancer.
Classe(s) : class AbstractNeedSpell extends AbstractIA
Fonctions :
- L18 ` public(AbstractNeedSpell)`
- L28 `static List<SortStats>(getListSpellOf)`
- L31 `  (for)`
- L34 `  (switch)`
- L45 `  (if)`
- L54 `  (if)`

#### `org/starloco/locos/fight/ia/IA.java` — 20 lignes
Rôle : IA historique Ancestrar : décisions mobs/invocations.
Classe(s) : interface IA
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/fight/ia/IAHandler.java` — 128 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IAHandler
Fonctions :
- L17 `static void(select)`
- L21 `  (if)`
- L32 `  (switch)`

#### `org/starloco/locos/fight/ia/type/Blank.java` — 22 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Blank extends AbstractIA
Fonctions :
- L11 ` public(Blank)`
- L15 ` d(apply)`

#### `org/starloco/locos/fight/ia/type/IA1.java` — 82 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA1 extends AbstractEasyIA
Fonctions :
- L21 ` public(IA1)`
- L25 ` d(run)`
- L28 `  (if)`
- L34 `  (switch)`
- L54 `  (if)`
- L59 `  (if)`

#### `org/starloco/locos/fight/ia/type/IA2.java` — 192 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA2 extends AbstractEasyIA
Fonctions :
- L25 ` public(IA2)`
- L29 ` d(run)`
- L33 `  (if)`
- L40 `  (switch)`
- L75 `  (if)`
- L124 `  (if)`
- L125 `  (if)`
- L126 `  (if)`
- L127 `  (if)`
- L128 `  (if)`
- L130 `  (if)`
- L143 ` boolean(tryEnemyBuff)`
- L145 `  (if)`
- L147 `  (for)`
- L149 `  (for)`
- L152 `  (for)`
- L159 `  (if)`
- L172 ` boolean(tryTrap)`
- L179 `  (if)`
- L182 `  (for)`

#### `org/starloco/locos/fight/ia/type/IA3.java` — 174 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA3 extends AbstractEasyIA
Fonctions :
- L24 ` public(IA3)`
- L28 ` d(run)`
- L32 `  (if)`
- L39 `  (switch)`
- L70 `  (if)`
- L113 `  (if)`
- L114 `  (if)`
- L115 `  (if)`
- L117 `  (if)`
- L120 `  (if)`
- L134 ` boolean(tryEnemyBuff)`
- L136 `  (if)`
- L138 `  (for)`
- L140 `  (for)`
- L141 `  (if)`
- L154 ` boolean(tryTrap)`
- L161 `  (if)`
- L164 `  (for)`

#### `org/starloco/locos/fight/ia/type/IAPerco.java` — 138 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IAPerco extends AbstractIA
Fonctions :
- L23 ` public(IAPerco)`
- L28 ` d(apply)`
- L31 ` 	(if)`
- L34 ` 	(if)`
- L38 ` 	(switch)`
- L44 ` 	(if)`
- L52 ` 	(if)`
- L54 ` 	(if)`
- L55 ` 	(if)`
- L70 ` 	(if)`
- L72 ` 	(if)`
- L78 ` 	(if)`
- L82 ` 	(if)`
- L83 ` 	(if)`
- L100 ` 	(if)`
- L113 ` Spell.SortStats(getBestSpell)`
- L115 `  (if)`
- L116 ` 	(for)`
- L117 ` 	(for)`
- L126 ` Fighter(getFightersForDebuffing)`

#### `org/starloco/locos/fight/ia/type/boss/IA10.java` — 113 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA10 extends AbstractEasyIA
Fonctions :
- L20 ` public(IA10)`
- L24 ` d(run)`
- L27 `  (switch)`
- L51 `  (while)`
- L53 `  (switch)`
- L87 `  (if)`

#### `org/starloco/locos/fight/ia/type/boss/IA11.java` — 106 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA11 extends AbstractIA
Fonctions :
- L14 ` public(IA11)`
- L18 ` d(apply)`
- L21 `  (if)`
- L39 ` void(tryLaunchOtherSpell)`
- L43 `  (for)`
- L49 `  (if)`
- L63 ` boolean(tryLaunchSpellKraken)`
- L75 ` boolean(tryLaunchSpellKraken)`
- L79 `  (if)`
- L87 `  (if)`
- L88 `  (if)`

#### `org/starloco/locos/fight/ia/type/boss/IA17.java` — 93 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA17 extends AbstractNeedSpell
Fonctions :
- L13 ` public(IA17)`
- L17 ` d(apply)`
- L20 `  (if)`
- L34 `  (if)`
- L36 `  (if)`
- L41 `  (if)`
- L44 `  (if)`
- L49 `  (if)`
- L52 `  (if)`
- L58 `  (if)`
- L61 `  (if)`
- L67 `  (if)`
- L70 `  (if)`
- L76 `  (if)`
- L81 `  (if)`

#### `org/starloco/locos/fight/ia/type/boss/IA18.java` — 90 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA18 extends AbstractIA
Fonctions :
- L18 ` public(IA18)`
- L22 ` d(apply)`
- L25 `  (if)`
- L28 `  (if)`
- L30 `  (if)`
- L35 `  (if)`
- L36 `  (if)`
- L51 ` Fighter(findKimbo)`
- L55 `  (for)`
- L57 `  (if)`
- L58 `  (if)`
- L59 `  (if)`
- L64 `  (if)`
- L75 ` void(attackGlyph)`
- L81 `  (if)`

#### `org/starloco/locos/fight/ia/type/boss/IA20.java` — 110 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA20 extends AbstractIA
Fonctions :
- L21 ` public(IA20)`
- L25 ` d(apply)`
- L28 `  (if)`
- L36 `  (if)`
- L57 ` List<Integer>(getGlyphCells)`
- L65 ` int(tpIfPossibleKaskargo)`
- L73 ` int(attackIfPossibleKaskargo)`
- L89 `  (for)`
- L93 `  (if)`
- L97 `  (if)`

#### `org/starloco/locos/fight/ia/type/boss/IA22.java` — 43 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA22 extends AbstractIA
Fonctions :
- L12 ` public(IA22)`
- L16 ` d(apply)`
- L19 `  (if)`
- L21 `  (if)`

#### `org/starloco/locos/fight/ia/type/boss/IA23.java` — 30 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class IA23 extends AbstractIA
Fonctions :
- L12 ` public(IA23)`
- L16 ` d(apply)`
- L19 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/Blocker.java` — 65 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Blocker extends AbstractIA
Fonctions :
- L18 ` public(Blocker)`
- L22 ` d(apply)`
- L25 `  (if)`
- L28 `  (if)`
- L30 `  (switch)`
- L34 ` else(if)`

#### `org/starloco/locos/fight/ia/type/invocations/Chafer.java` — 123 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Chafer extends AbstractNeedSpell
Fonctions :
- L21 ` public(Chafer)`
- L27 ` d(apply)`
- L30 `  (if)`
- L34 `  (if)`
- L36 `  (if)`
- L40 `  (if)`
- L44 `  (switch)`
- L61 `  (if)`
- L65 `  (if)`
- L79 `  (if)`
- L81 `  (if)`
- L85 `  (if)`
- L99 `  (if)`
- L110 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/Lapino.java` — 81 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Lapino extends AbstractNeedSpell
Fonctions :
- L15 ` public(Lapino)`
- L19 ` d(apply)`
- L22 `  (if)`
- L25 `  (if)`
- L29 `  (switch)`
- L33 `  (if)`
- L45 `  (if)`
- L46 `  (if)`
- L54 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/Tonneau.java` — 65 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Tonneau extends AbstractNeedSpell
Fonctions :
- L20 ` public(Tonneau)`
- L24 ` d(apply)`
- L27 `  (if)`
- L29 `  (if)`
- L35 `  (if)`
- L49 ` List<Fighter>(getFightersInline)`
- L52 `  (for)`
- L53 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Cra.java` — 77 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Cra extends AbstractEasyIA
Fonctions :
- L15 ` public(Cra)`
- L19 ` d(run)`
- L22 `  (switch)`
- L45 `  (if)`
- L52 `  (if)`
- L56 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Ecaflip.java` — 58 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Ecaflip extends AbstractEasyIA
Fonctions :
- L12 ` public(Ecaflip)`
- L16 ` d(run)`
- L19 `  (switch)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Eniripsa.java` — 56 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Eniripsa extends AbstractEasyIA
Fonctions :
- L12 ` public(Eniripsa)`
- L16 ` d(run)`
- L19 `  (switch)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Enutrof.java` — 84 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Enutrof extends AbstractEasyIA
Fonctions :
- L18 ` public(Enutrof)`
- L22 ` d(run)`
- L26 `  (switch)`
- L47 `  (if)`
- L57 ` else(if)`
- L68 ` boolean(hasEnnemiesArround)`
- L71 `  (if)`
- L72 `  (for)`
- L73 `  (if)`
- L75 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Feca.java` — 64 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Feca extends AbstractEasyIA
Fonctions :
- L12 ` public(Feca)`
- L16 ` d(run)`
- L19 `  (switch)`
- L23 `  (if)`
- L34 `  (if)`
- L42 `  (if)`
- L52 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Iop.java` — 70 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Iop extends AbstractEasyIA
Fonctions :
- L15 ` public(Iop)`
- L19 ` d(run)`
- L22 `  (switch)`
- L44 `  (if)`
- L53 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Osamodas.java` — 68 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Osamodas extends AbstractEasyIA
Fonctions :
- L15 ` public(Osamodas)`
- L19 ` d(run)`
- L22 `  (switch)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Pandawa.java` — 63 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Pandawa extends AbstractEasyIA
Fonctions :
- L12 ` public(Pandawa)`
- L16 ` d(run)`
- L19 `  (switch)`
- L23 `  (if)`
- L25 `  (if)`
- L37 `  (if)`
- L42 `  (if)`
- L48 `  (if)`
- L54 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Sacrieur.java` — 75 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Sacrieur extends AbstractEasyIA
Fonctions :
- L15 ` public(Sacrieur)`
- L19 ` d(run)`
- L22 `  (switch)`
- L25 `  (if)`
- L33 `  (if)`
- L39 `  (if)`
- L53 `  (if)`
- L61 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Sadida.java` — 57 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Sadida extends AbstractEasyIA
Fonctions :
- L12 ` public(Sadida)`
- L16 ` d(run)`
- L19 `  (switch)`
- L22 `  (if)`
- L30 `  (if)`
- L43 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Sram.java` — 74 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Sram extends AbstractEasyIA
Fonctions :
- L19 ` public(Sram)`
- L23 ` d(run)`
- L26 `  (switch)`
- L43 `  (if)`
- L57 `  (if)`

#### `org/starloco/locos/fight/ia/type/invocations/dopeuls/Xelor.java` — 56 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Xelor extends AbstractEasyIA
Fonctions :
- L12 ` public(Xelor)`
- L16 ` d(run)`
- L19 `  (switch)`
- L28 `  (if)`

#### `org/starloco/locos/fight/ia/util/AStarPathFinding.java` — 156 lignes
Rôle : Pathfinding/cellules : déplacements, portée, ligne de vue, distances, push/pull, cases libres.
Classe(s) : class AStarPathFinding
Fonctions :
- L17 ` public(AStarPathFinding)`
- L24 ` public(AStarPathFinding)`
- L33 `  (for)`
- L35 `  (for)`
- L37 `  (if)`
- L46 ` ArrayList<GameCase>(getShortestPath)`
- L52 `  (while)`
- L77 `  (if)`
- L79 `  (if)`
- L98 ` ArrayList<GameCase>(getPath)`
- L114 `  (while)`
- L121 ` Node(getLastNode)`
- L128 ` Node(bestNode)`
- L132 `  (for)`
- L133 `  (if)`
- L140 ` void(addListClose)`
- L146 ` int(getCostG)`
- L149 `  (while)`

#### `org/starloco/locos/fight/ia/util/Function.java` — 1947 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Function
Fonctions :
- L28 `static Function(getInstance)`
- L38 `  (for)`
- L48 `  (if)`
- L55 ` boolean(invoctantaIfPossible)`
- L69 `  (if)`
- L75 `  (if)`
- L82 `  (if)`
- L89 `  (if)`
- L96 `  (while)`
- L100 `  (if)`
- L112 ` boolean(hasMobInFight)`
- L124 `  (for)`
- L137 ` SortStats(findSpell)`
- L138 `  (for)`
- L144 ` boolean(moveNearIfPossible)`
- L170 `  (if)`
- L173 `  (for)`
- L176 `  (if)`
- L198 `  (for)`
- L203 `  (if)`
- L214 `  (catch)`
- L225 ` int(getMaxCellForTP)`
- L262 `  (for)`
- L267 `  (for)`
- L272 `  (if)`
- L280 `  (if)`
- L290 `  (if)`
- L306 ` int(moveFarIfPossible)`
- L320 `  (for)`
- L336 `  (if)`
- L341 `  (if)`
- L393 `  (if)`
- L416 `  (if)`
- L439 `  (if)`
- L477 `  (for)`
- L482 `  (if)`
- L495 `  (catch)`
- L511 `  (if)`
- L521 `  (if)`
- L531 `  (if)`
- L537 `  (if)`
- L544 ` boolean(invocIfPossible)`
- L572 ` boolean(invocIfPossibleloin)`
- L596 ` SortStats(getInvocSpell)`
- L607 `  (for)`
- L616 ` SortStats(getInvocSpellDopeul)`
- L627 `  (for)`
- L631 `  (for)`
- L650 `  (if)`
- L653 `  (if)`
- L665 `  (for)`
- L673 `  (if)`
- L676 `  (if)`
- L679 `  (if)`
- L696 `  (for)`
- L707 `  (if)`
- L741 `  (if)`
- L752 `  (for)`
- L760 `  (if)`
- L763 `  (if)`
- L766 `  (if)`
- L783 `  (for)`
- L794 `  (if)`
- L817 ` boolean(buffIfPossible)`
- L832 ` SortStats(getBuffSpell)`
- L839 `  (if)`
- L856 `  (for)`
- L870 ` boolean(buffIfPossible)`
- L885 ` SortStats(getBuffSpellDopeul)`
- L892 `  (for)`
- L904 ` SortStats(getHealSpell)`
- L911 `  (if)`
- L928 `  (for)`
- L943 ` int(moveautourIfPossible)`
- L970 `  (if)`
- L973 `  (for)`
- L976 `  (if)`
- L999 `  (for)`
- L1004 `  (if)`
- L1017 `  (catch)`
- L1029 ` int(moveenfaceIfPossible)`
- L1056 `  (if)`
- L1059 `  (for)`
- L1062 `  (if)`
- L1090 `  (for)`
- L1095 `  (if)`
- L1108 `  (catch)`
- L1120 ` Fighter(getNearestFriendNoInvok)`
- L1127 `  (for)`
- L1136 `  (if)`
- L1145 ` Fighter(getNearestFriend)`
- L1152 `  (for)`
- L1161 `  (if)`
- L1170 ` Fighter(getNearestEnnemy)`
- L1177 `  (for)`
- L1184 `  (if)`
- L1193 ` Map<Integer, Fighter>(getLowHpEnnemyList)`
- L1200 `  (for)`
- L1206 `  (if)`
- L1214 `  (while)`
- L1219 `  (for)`
- L1221 `  (if)`
- L1241 `  (for)`
- L1244 `  (if)`
- L1253 `  (for)`
- L1261 `  (if)`
- L1268 `  (if)`
- L1284 ` int(getInfluence)`
- L1290 `  (for)`
- L1292 `  (switch)`
- L1316 ` SortStats(getBestSpellForTargetDopeul)`
- L1323 `  (for)`
- L1336 `  (if)`
- L1343 `  (for)`
- L1354 `  (if)`
- L1361 `  (for)`
- L1372 `  (if)`
- L1382 ` int(getBestTargetZone)`
- L1395 `  (if)`
- L1400 `  (if)`
- L1415 `  (if)`
- L1421 `  (for)`
- L1429 `  (for)`
- L1438 `  (if)`
- L1446 `  (catch)`
- L1456 ` int(calculInfluenceHeal)`
- L1460 `  (for)`
- L1469 ` int(calculInfluence)`
- L1473 `  (for)`
- L1476 `  (switch)`
- L1610 ` boolean(moveToAttack)`
- L1618 ` boolean(moveToAttack)`
- L1630 `  (if)`
- L1635 `  (if)`
- L1636 `  (for)`
- L1670 `  (for)`
- L1672 `  (if)`
- L1698 `  (for)`
- L1718 ` boolean(moveToCell)`
- L1728 `  (for)`
- L1757 ` int(getCellToBeInTheSameLine)`
- L1766 `  (if)`
- L1783 `  (for)`
- L1784 `  (if)`
- L1791 ` SortStats(getBestBuffSpell)`
- L1798 `  (for)`
- L1801 `  (if)`
- L1808 ` SortStats(getBestHealSpell)`
- L1816 `  (for)`
- L1817 `  (if)`
- L1824 ` SortStats(getSpellByPo)`
- L1828 `  (for)`
- L1829 `  (if)`
- L1836 ` Fighter(getEnnemyWithDistance)`
- L1843 `  (while)`
- L1845 `  (for)`
- L1851 `  (if)`
- L1853 `  (if)`
- L1863 ` int(tryCastSpell)`
- L1869 ` int(tryCastSpell)`
- L1885 ` boolean(tryCastSpell)`
- L1891 ` List<GameCase>(getCellsAround)`
- L1896 `  (for)`
- L1912 ` List<GameCase>(getCellsAvailableAround)`
- L1919 `  (for)`
- L1925 `  (if)`
- L1926 `  (for)`
- L1928 `  (for)`
- L1937 ` List<GameCase>(getCellsAvailableAround)`
- L1941 ` boolean(cellAvailable)`

#### `org/starloco/locos/fight/ia/util/Node.java` — 60 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class Node
Fonctions :
- L10 ` public(Node)`
- L15 ` int(getCountG)`
- L19 ` void(setCountG)`
- L23 ` int(getCountF)`
- L27 ` void(setCountF)`
- L31 ` int(getHeristic)`
- L35 ` void(setHeristic)`
- L39 ` GameCase(getCell)`
- L43 ` Node(getParent)`
- L47 ` void(setParent)`
- L51 ` void(setChild)`
- L55 ` Node(getChild)`

#### `org/starloco/locos/fight/ia/util/newia/AttackFighterMind.java` — 97 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class AttackFighterMind extends FighterMind
Fonctions :
- L19 ` public(AttackFighterMind)`
- L24 `  (for)`
- L30 `  (for)`
- L46 ` void(init)`
- L48 `  (for)`
- L51 `  (for)`
- L67 `  (if)`
- L84 `  (for)`
- L87 `  (for)`

#### `org/starloco/locos/fight/ia/util/newia/AttackInvocationFighterMind.java` — 74 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class AttackInvocationFighterMind extends FighterMind
Fonctions :
- L23 ` public(AttackInvocationFighterMind)`
- L31 `  (for)`
- L35 `  (for)`
- L46 ` void(init)`
- L48 `  (for)`
- L50 `  (for)`
- L63 `  (for)`
- L65 `  (for)`

#### `org/starloco/locos/fight/ia/util/newia/BuffFighterMind.java` — 66 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class BuffFighterMind extends FighterMind
Fonctions :
- L18 ` public(BuffFighterMind)`
- L21 `  (for)`
- L26 `  (for)`
- L36 ` void(init)`
- L38 `  (for)`
- L39 `  (for)`
- L41 `  (if)`
- L56 `  (for)`
- L57 `  (for)`

#### `org/starloco/locos/fight/ia/util/newia/FighterCase.java` — 65 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class FighterCase
Fonctions :
- L22 `  (FighterCase)`
- L28 ` Fighter(getFighter)`
- L32 ` GameCase(getFighterCell)`
- L34 `  (if)`
- L37 `  (if)`
- L39 `  (if)`
- L41 `  (if)`
- L52 ` void(setShortestPath)`
- L56 ` List<GameCase>(getShortestPath)`
- L60 ` LinkedList<Spell.SortStats>(getSortedSpells)`

#### `org/starloco/locos/fight/ia/util/newia/FighterMind.java` — 59 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class FighterMind
Fonctions :
- L20 ` public(FighterMind)`
- L28 ` IAAction(executeActions)`
- L30 `  (if)`
- L38 ` LinkedList<IAAction>(getHighPriorityActions)`
- L42 ` LinkedList<IAAction>(getLowPriorityActions)`
- L46 ` Collection<Fighter>(getEnemies)`
- L50 ` Collection<Fighter>(getFriends)`
- L54 ` Collection<Fighter>(getInvocations)`

#### `org/starloco/locos/fight/ia/util/newia/HealFighterMind.java` — 71 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class HealFighterMind extends FighterMind
Fonctions :
- L18 ` public(HealFighterMind)`
- L21 `  (for)`
- L26 `  (for)`
- L37 ` void(init)`
- L39 `  (for)`
- L40 `  (for)`
- L42 `  (if)`
- L57 `  (for)`
- L58 `  (for)`
- L66 ` int(getPdvPer)`

#### `org/starloco/locos/fight/ia/util/newia/InvocationFighterMind.java` — 78 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class InvocationFighterMind extends FighterMind
Fonctions :
- L21 ` public(InvocationFighterMind)`
- L27 `  (for)`
- L32 `  (for)`
- L42 ` void(init)`
- L44 `  (for)`
- L45 `  (for)`
- L47 `  (if)`
- L66 `  (for)`
- L67 `  (for)`

#### `org/starloco/locos/fight/ia/util/newia/RandomAttackFighterMind.java` — 74 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class RandomAttackFighterMind extends FighterMind
Fonctions :
- L23 ` public(RandomAttackFighterMind)`
- L31 `  (for)`
- L35 `  (for)`
- L46 ` void(init)`
- L48 `  (for)`
- L50 `  (for)`
- L63 `  (for)`
- L65 `  (for)`

#### `org/starloco/locos/fight/ia/util/newia/action/AttackAction.java` — 45 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class AttackAction implements IAAction
Fonctions :
- L15 ` public(AttackAction)`
- L21 ` GameCase(getCell)`
- L25 ` Spell.SortStats(getSpell)`
- L29 ` e(getType)`
- L34 ` t(getWaitingTime)`
- L39 ` n(execute)`

#### `org/starloco/locos/fight/ia/util/newia/action/IAAction.java` — 14 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : interface IAAction
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/fight/ia/util/newia/action/MoveAction.java` — 41 lignes
Rôle : IA combat : choix déplacement/sort/cible selon profil de monstre/invocation.
Classe(s) : class MoveAction implements IAAction
Fonctions :
- L17 ` public(MoveAction)`
- L23 ` e(getType)`
- L28 ` t(getWaitingTime)`
- L34 ` n(execute)`

#### `org/starloco/locos/fight/spells/LaunchedSpell.java` — 83 lignes
Rôle : Définition de sort et niveaux : coûts, portée, effets, conditions de lancer.
Classe(s) : class LaunchedSpell
Fonctions :
- L13 ` public(LaunchedSpell)`
- L24 `static boolean(cooldownGood)`
- L26 `  (for)`
- L32 `static int(getNbLaunch)`
- L40 `static int(getNbLaunchTarget)`
- L52 `static int(haveEffectTarget)`
- L65 ` Fighter(getTarget)`
- L69 ` int(getSpellId)`
- L73 ` int(getCooldown)`
- L77 ` int(decrementCooldown)`

#### `org/starloco/locos/fight/spells/ResEffectInfo.java` — 65 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : enum ResEffectInfo
Fonctions :
- L20 `  (ResEffectInfo)`
- L28 ` int(getPercentElem)`
- L32 ` int(getFixedElem)`
- L36 ` int(getFixed)`
- L40 ` int(getPercentElemPvP)`
- L44 ` int(getFixedElemPvP)`
- L48 `static ResEffectInfo(forElement)`
- L50 `  (switch)`

#### `org/starloco/locos/fight/spells/Spell.java` — 498 lignes
Rôle : Définition de sort et niveaux : coûts, portée, effets, conditions de lancer.
Classe(s) : class Spell
Fonctions :
- L29 ` public(Spell)`
- L40 ` void(setInfo)`
- L48 ` int(getId)`
- L52 ` String(getName)`
- L56 ` int(getSpriteId)`
- L60 ` String(getSpriteInfo)`
- L64 ` int(getType)`
- L68 ` void(setType)`
- L72 ` short(getDuration)`
- L76 ` ArrayList<Integer>(getEffectTargets)`
- L80 ` ArrayList<Integer>(getEffectTargetsCC)`
- L84 ` SortStats(getStatsByLevel)`
- L88 ` Map<Integer, SortStats>(getSpellsStats)`
- L92 ` void(addSpellStats)`
- L98 ` boolean(hasInvalidState)`
- L100 `  (if)`
- L101 `  (for)`
- L108 ` boolean(hasNeededState)`
- L111 `  (if)`
- L112 `  (for)`
- L119 ` void(parseEffectTargets)`
- L123 `  (if)`
- L132 `  (for)`
- L139 `  (for)`
- L148 ` void(parseStates)`
- L150 `  (if)`
- L152 `  (for)`
- L156 `  (if)`
- L158 `  (for)`
- L185 ` public(SortStats)`
- L213 ` ArrayList<SpellEffect>(parseEffect)`
- L217 `  (for)`
- L231 ` int(getSpellID)`
- L235 ` Spell(getSpell)`
- L239 ` int(getSpriteID)`
- L243 ` String(getSpriteInfos)`
- L247 ` int(getLevel)`
- L251 ` int(getPACost)`
- L255 ` int(getMinPO)`
- L259 ` int(getMaxPO)`
- L263 ` int(getTauxCC)`
- L267 ` int(getTauxEC)`
- L271 ` boolean(isLineLaunch)`
- L275 ` boolean(hasLDV)`
- L279 ` boolean(isEmptyCell)`
- L283 ` boolean(isModifPO)`
- L287 ` int(getMaxLaunchbyTurn)`
- L291 ` int(getMaxLaunchByTarget)`
- L295 ` int(getCoolDown)`
- L299 ` int(getReqLevel)`
- L303 ` boolean(isEcEndTurn)`
- L307 ` ArrayList<SpellEffect>(getEffects)`
- L311 ` ArrayList<SpellEffect>(getCCeffects)`
- L315 ` String(getPorteeType)`
- L319 ` void(applySpellEffectToFight)`
- L324 `  (for)`
- L326 `  (if)`
- L336 `  (for)`
- L345 ` ArrayList<Fighter>(getTargets)`
- L348 `  (for)`
- L349 `  (if)`
- L356 ` void(applySpellEffectToFight)`
- L375 `  (for)`
- L390 `  (if)`
- L403 `  (for)`
- L447 `  (for)`

#### `org/starloco/locos/fight/spells/SpellEffect.java` — 4784 lignes
Rôle : Effets de sorts : application des dommages, buffs, soins, invocations, états, pièges/glyphes.
Classe(s) : class SpellEffect implements Cloneable
Fonctions :
- L35 ` public(SpellEffect)`
- L49 ` public(SpellEffect)`
- L63 ` boolean(isPoison)`
- L67 `static ArrayList<Fighter>(getTargets)`
- L70 ` 	(for)`
- L78 ` int(applyOnHitBuffs)`
- L80 ` 	(for)`
- L82 ` 	(if)`
- L84 ` 	(switch)`
- L86 ` 	(for)`
- L87 ` 	(if)`
- L94 ` 	(for)`
- L98 ` 	(if)`
- L108 ` 	(if)`
- L124 ` 	(if)`
- L128 ` 	(if)`
- L131 ` 	(if)`
- L134 ` 	(if)`
- L141 ` 	(if)`
- L149 ` 	(for)`
- L150 ` 	(switch)`
- L152 ` 	(if)`
- L165 ` 	(if)`
- L184 ` 	(if)`
- L191 ` 	(if)`
- L196 ` 	(if)`
- L214 ` 	(if)`
- L301 ` 	(if)`
- L325 ` int(getTurn)`
- L329 ` void(setTurn)`
- L333 ` boolean(isDebuffabe)`
- L337 ` int(getEffectID)`
- L341 ` void(setEffectID)`
- L345 ` String(getJet)`
- L349 ` int(getValue)`
- L353 ` void(setValue)`
- L357 ` int(getChance)`
- L361 ` String(getArgs)`
- L365 ` void(setArgs)`
- L369 ` int(getMaxMinSpell)`
- L372 ` 	(if)`
- L381 ` int(decrementDuration)`
- L386 ` void(applyBeginingBuff)`
- L395 ` void(applyToFight)`
- L401 ` Fighter(getCaster)`
- L405 ` int(getSpell)`
- L409 ` void(applyToFight)`
- L419 ` 	(if)`
- L422 ` 	(if)`
- L433 ` 	(switch)`
- L844 ` void(applyEffect_4)`
- L858 ` 	(for)`
- L868 ` void(applyEffect_5)`
- L873 ` 	(if)`
- L875 ` 	(switch)`
- L883 ` 	(for)`
- L898 ` 	(if)`
- L908 ` 	(if)`
- L913 ` 	(if)`
- L919 ` 	(if)`
- L923 ` 	(if)`
- L937 ` 	(if)`
- L962 ` 	(if)`
- L975 ` 	(if)`
- L977 ` 	(if)`
- L978 ` 	(if)`
- L982 ` 	(if)`
- L991 ` void(applyEffect_6)`
- L993 ` 	(if)`
- L994 ` 	(for)`
- L1000 ` 	(if)`
- L1027 ` 	(if)`
- L1044 ` void(applyEffect_8)`
- L1055 ` 	(switch)`
- L1057 ` 	(if)`
- L1066 ` 	(if)`
- L1095 ` 	(for)`
- L1107 ` void(applyEffect_9)`
- L1109 ` 	(for)`
- L1113 ` void(applyEffect_50)`
- L1141 ` void(applyEffect_51)`
- L1163 ` void(applyEffect_77)`
- L1166 ` 	(for)`
- L1180 ` 	(if)`
- L1187 ` void(applyEffect_78)`
- L1191 ` 	(for)`
- L1196 ` void(applyEffect_79)`
- L1199 ` 	(for)`
- L1204 ` void(applyEffect_81)`
- L1206 ` 	(if)`
- L1209 ` 	(if)`
- L1215 ` 	(for)`
- L1234 ` 	(for)`
- L1235 ` 	(if)`
- L1241 ` void(applyEffect_82)`
- L1243 ` 	(if)`
- L1244 ` 	(for)`
- L1247 ` 	(if)`
- L1255 ` 	(if)`
- L1275 ` 	(if)`
- L1285 ` 	(for)`
- L1290 ` void(applyEffect_84)`
- L1293 ` 	(for)`
- L1307 ` 	(if)`
- L1310 ` 	(if)`
- L1316 ` void(applyEffect_85)`
- L1318 ` 	(if)`
- L1319 ` 	(for)`
- L1323 ` 	(if)`
- L1330 ` 	(if)`
- L1360 ` 	(if)`
- L1369 ` 	(for)`
- L1374 ` void(applyEffect_86)`
- L1376 ` 	(if)`
- L1377 ` 	(for)`
- L1381 ` 	(if)`
- L1388 ` 	(if)`
- L1419 ` 	(if)`
- L1428 ` 	(for)`
- L1433 ` void(applyEffect_87)`
- L1435 ` 	(if)`
- L1436 ` 	(for)`
- L1440 ` 	(if)`
- L1447 ` 	(if)`
- L1477 ` 	(if)`
- L1486 ` 	(for)`
- L1491 ` void(applyEffect_88)`
- L1493 ` 	(if)`
- L1494 ` 	(for)`
- L1497 ` 	(if)`
- L1504 ` 	(if)`
- L1535 ` 	(if)`
- L1544 ` 	(for)`
- L1549 ` void(applyEffect_89)`
- L1551 ` 	(if)`
- L1552 ` 	(for)`
- L1555 ` 	(if)`
- L1562 ` 	(if)`
- L1582 ` 	(for)`
- L1589 ` 	(if)`
- L1603 ` 	(if)`
- L1612 ` 	(for)`
- L1617 ` void(applyEffect_90)`
- L1634 ` 	(for)`
- L1644 ` 	(for)`
- L1653 ` 	(if)`
- L1654 ` 	(for)`
- L1657 ` 	(if)`
- L1683 ` 	(if)`
- L1694 ` 	(for)`
- L1698 ` 	(if)`
- L1705 ` 	(if)`
- L1729 ` 	(if)`
- L1738 ` 	(for)`
- L1749 ` 	(if)`
- L1750 ` 	(for)`
- L1753 ` 	(if)`
- L1779 ` 	(if)`
- L1788 ` 	(for)`
- L1791 ` 	(if)`
- L1798 ` 	(if)`
- L1822 ` 	(if)`
- L1831 ` 	(for)`
- L1842 ` 	(if)`
- L1843 ` 	(for)`
- L1846 ` 	(if)`
- L1872 ` 	(if)`
- L1881 ` 	(for)`
- L1884 ` 	(if)`
- L1891 ` 	(if)`
- L1916 ` 	(if)`
- L1925 ` 	(for)`
- L1930 ` void(applyEffect_94)`
- L1937 ` 	(for)`
- L1940 ` 	(if)`
- L1965 ` 	(if)`
- L1974 ` 	(for)`
- L1978 ` 	(if)`
- L1985 ` 	(if)`
- L2009 ` 	(if)`
- L2018 ` 	(for)`
- L2023 ` void(applyEffect_95)`
- L2030 ` 	(for)`
- L2033 ` 	(if)`
- L2060 ` 	(if)`
- L2069 ` 	(for)`
- L2073 ` 	(if)`
- L2080 ` 	(if)`
- L2105 ` 	(if)`
- L2114 ` 	(for)`
- L2127 ` 	(for)`
- L2133 ` 	(if)`
- L2143 ` 	(for)`
- L2144 ` 	(if)`
- L2170 ` 	(if)`
- L2181 ` 	(for)`
- L2187 ` 	(if)`
- L2194 ` 	(if)`
- L2203 ` 	(for)`
- L2204 ` 	(if)`
- L2231 ` 	(if)`
- L2233 ` 	(if)`
- L2254 ` 	(for)`
- L2255 ` 	(if)`
- L2262 ` 	(if)`
- L2271 ` 	(for)`
- L2272 ` 	(if)`
- L2299 ` 	(if)`
- L2310 ` 	(for)`
- L2316 ` 	(if)`
- L2323 ` 	(if)`
- L2333 ` 	(if)`
- L2336 ` 	(for)`
- L2339 ` 	(if)`
- L2365 ` 	(if)`
- L2367 ` 	(if)`
- L2376 ` 	(if)`
- L2377 ` 	(for)`
- L2383 ` 	(for)`
- L2396 ` 	(for)`
- L2401 ` 	(if)`
- L2411 ` 	(for)`
- L2412 ` 	(if)`
- L2441 ` 	(if)`
- L2452 ` 	(for)`
- L2458 ` 	(if)`
- L2465 ` 	(if)`
- L2474 ` 	(for)`
- L2475 ` 	(if)`
- L2503 ` 	(if)`
- L2506 ` 	(if)`
- L2515 ` 	(for)`
- L2529 ` 	(for)`
- L2534 ` 	(if)`
- L2544 ` 	(for)`
- L2545 ` 	(if)`
- L2571 ` 	(if)`
- L2580 ` 	(for)`
- L2590 ` 	(if)`
- L2597 ` 	(if)`
- L2606 ` 	(for)`
- L2607 ` 	(if)`
- L2634 ` 	(if)`
- L2636 ` 	(if)`
- L2645 ` 	(for)`
- L2650 ` void(applyEffect_100)`
- L2659 ` 	(for)`
- L2665 ` 	(if)`
- L2675 ` 	(for)`
- L2676 ` 	(if)`
- L2702 ` 	(if)`
- L2711 ` 	(for)`
- L2717 ` 	(if)`
- L2724 ` 	(if)`
- L2733 ` 	(for)`
- L2734 ` 	(if)`
- L2760 ` 	(if)`
- L2762 ` 	(if)`
- L2771 ` 	(for)`
- L2776 ` void(applyEffect_101)`
- L2778 ` 	(for)`
- L2779 ` 	(if)`
- L2781 ` 	(if)`
- L2786 ` 	(if)`
- L2795 ` 	(if)`
- L2799 ` 	(if)`
- L2803 ` 	(if)`
- L2807 ` 	(if)`
- L2809 ` 	(if)`
- L2819 ` void(applyEffect_105)`
- L2823 ` 	(for)`
- L2827 ` void(applyEffect_106)`
- L2837 ` 	(for)`
- L2842 ` void(applyEffect_107)`
- L2848 ` void(applyEffect_108)`
- L2852 ` 	(if)`
- L2855 ` 	(if)`
- L2861 ` 	(for)`
- L2864 ` 	(if)`
- L2894 ` 	(if)`
- L2905 ` 	(if)`
- L2914 ` void(applyEffect_110)`
- L2918 ` 	(for)`
- L2923 ` void(applyEffect_111)`
- L2929 ` 	(for)`
- L2930 ` 	(if)`
- L2931 ` 	(if)`
- L2955 ` void(applyEffect_112)`
- L2959 ` 	(if)`
- L2965 ` 	(for)`
- L2970 ` void(applyEffect_114)`
- L2974 ` 	(for)`
- L2980 ` void(applyEffect_115)`
- L2984 ` 	(for)`
- L2989 ` void(applyEffect_116)`
- L2993 ` 	(for)`
- L2998 ` void(applyEffect_117)`
- L3003 ` 	(for)`
- L3007 ` 	(if)`
- L3012 ` void(applyEffect_118)`
- L3016 ` 	(if)`
- L3020 ` 	(for)`
- L3025 ` void(applyEffect_119)`
- L3029 ` 	(for)`
- L3043 ` void(applyEffect_121)`
- L3047 ` 	(for)`
- L3052 ` void(applyEffect_122)`
- L3056 ` 	(for)`
- L3061 ` void(applyEffect_123)`
- L3065 ` 	(for)`
- L3070 ` void(applyEffect_124)`
- L3074 ` 	(for)`
- L3079 ` void(applyEffect_125)`
- L3083 ` 	(for)`
- L3088 ` void(applyEffect_126)`
- L3092 ` 	(if)`
- L3096 ` 	(for)`
- L3101 ` void(applyEffect_127)`
- L3103 ` 	(for)`
- L3106 ` 	(if)`
- L3110 ` 	(if)`
- L3113 ` 	(if)`
- L3117 ` 	(if)`
- L3123 ` void(applyEffect_128)`
- L3129 ` 	(for)`
- L3130 ` 	(if)`
- L3131 ` 	(if)`
- L3152 ` void(applyEffect_130)`
- L3154 ` 	(if)`
- L3155 ` 	(for)`
- L3158 ` 	(if)`
- L3165 ` 	(if)`
- L3178 ` void(applyEffect_131)`
- L3180 ` 	(for)`
- L3184 ` void(applyEffect_132)`
- L3186 ` 	(for)`
- L3193 ` void(applyEffect_138)`
- L3197 ` 	(for)`
- L3202 ` void(applyEffect_140)`
- L3204 ` 	(for)`
- L3208 ` void(applyEffect_141)`
- L3210 ` 	(for)`
- L3214 ` 	(if)`
- L3215 ` 	(if)`
- L3225 ` void(applyEffect_142)`
- L3229 ` 	(if)`
- L3233 ` 	(for)`
- L3237 ` void(applyEffect_143)`
- L3239 ` 	(if)`
- L3242 ` 	(if)`
- L3248 ` 	(for)`
- L3266 ` 	(if)`
- L3269 ` 	(if)`
- L3275 ` 	(for)`
- L3278 ` 	(if)`
- L3288 ` 	(if)`
- L3301 ` 	(for)`
- L3308 ` void(applyEffect_144)`
- L3314 ` 	(for)`
- L3320 ` void(applyEffect_145)`
- L3324 ` 	(for)`
- L3329 ` void(applyEffect_149)`
- L3338 ` 	(for)`
- L3355 ` void(applyEffect_150)`
- L3361 ` 	(if)`
- L3367 ` 	(for)`
- L3375 ` void(applyEffect_152)`
- L3381 ` 	(for)`
- L3387 ` void(applyEffect_153)`
- L3393 ` 	(for)`
- L3399 ` void(applyEffect_154)`
- L3405 ` 	(for)`
- L3411 ` void(applyEffect_155)`
- L3415 ` 	(for)`
- L3420 ` void(applyEffect_156)`
- L3426 ` 	(for)`
- L3432 ` void(applyEffect_157)`
- L3438 ` 	(for)`
- L3444 ` void(applyEffect_160)`
- L3448 ` 	(for)`
- L3453 ` void(applyEffect_161)`
- L3457 ` 	(for)`
- L3462 ` void(applyEffect_162)`
- L3466 ` 	(for)`
- L3471 ` void(applyEffect_163)`
- L3475 ` 	(if)`
- L3480 ` 	(for)`
- L3484 ` void(applyEffect_164)`
- L3488 ` 	(for)`
- L3493 ` void(applyEffect_165)`
- L3505 ` void(applyEffect_168)`
- L3509 ` 	(for)`
- L3513 ` 	(if)`
- L3518 ` 	(if)`
- L3520 ` 	(if)`
- L3531 ` 	(if)`
- L3535 ` 	(if)`
- L3540 ` void(applyEffect_169)`
- L3545 ` 	(if)`
- L3548 ` 	(if)`
- L3555 ` 	(for)`
- L3559 ` 	(if)`
- L3560 ` 	(if)`
- L3570 ` 	(if)`
- L3574 ` 	(if)`
- L3579 ` void(applyEffect_171)`
- L3583 ` 	(for)`
- L3588 ` void(applyEffect_176)`
- L3592 ` 	(for)`
- L3597 ` void(applyEffect_177)`
- L3601 ` 	(for)`
- L3606 ` void(applyEffect_178)`
- L3612 ` 	(for)`
- L3618 ` void(applyEffect_179)`
- L3624 ` 	(for)`
- L3668 ` 	(if)`
- L3675 ` 	(if)`
- L3691 ` 	(if)`
- L3720 ` void(applyEffect_182)`
- L3724 ` 	(for)`
- L3729 ` void(applyEffect_183)`
- L3733 ` 	(for)`
- L3738 ` void(applyEffect_184)`
- L3742 ` 	(for)`
- L3747 ` void(applyEffect_185)`
- L3783 ` void(applyEffect_186)`
- L3787 ` 	(for)`
- L3793 ` void(applyEffect_202)`
- L3795 ` 	(if)`
- L3796 ` 	(for)`
- L3797 ` 	(if)`
- L3801 ` 	(for)`
- L3808 ` void(applyEffect_210)`
- L3831 ` 	(for)`
- L3836 ` void(applyEffect_211)`
- L3842 ` 	(for)`
- L3847 ` void(applyEffect_212)`
- L3854 ` 	(for)`
- L3859 ` void(applyEffect_213)`
- L3866 ` 	(for)`
- L3871 ` void(applyEffect_214)`
- L3878 ` 	(for)`
- L3883 ` void(applyEffect_215)`
- L3887 ` 	(for)`
- L3892 ` void(applyEffect_216)`
- L3896 ` 	(for)`
- L3901 ` void(applyEffect_217)`
- L3905 ` 	(for)`
- L3910 ` void(applyEffect_218)`
- L3914 ` 	(for)`
- L3919 ` void(applyEffect_219)`
- L3923 ` 	(for)`
- L3928 ` void(applyEffect_220)`
- L3931 ` 	(for)`
- L3936 ` void(applyEffect_265)`
- L3940 ` 	(for)`
- L3945 ` void(applyEffect_266)`
- L3949 ` 	(for)`
- L3958 ` void(applyEffect_267)`
- L3962 ` 	(for)`
- L3971 ` void(applyEffect_268)`
- L3975 ` 	(for)`
- L3985 ` void(applyEffect_269)`
- L3989 ` 	(for)`
- L3998 ` void(applyEffect_270)`
- L4002 ` 	(for)`
- L4011 ` void(applyEffect_271)`
- L4015 ` 	(for)`
- L4024 ` void(applyEffect_293)`
- L4029 ` void(applyEffect_320)`
- L4032 ` 	(for)`
- L4037 ` 	(if)`
- L4040 ` 	(if)`
- L4046 ` void(applyEffect_400)`
- L4050 ` 	(for)`
- L4052 ` 	(if)`
- L4071 ` void(applyEffect_401)`
- L4090 ` void(applyEffect_402)`
- L4109 ` void(applyEffect_671)`
- L4111 ` 	(if)`
- L4112 ` 	(for)`
- L4113 ` 	(if)`
- L4114 ` 	(if)`
- L4120 ` 	(if)`
- L4125 ` 	(if)`
- L4153 ` void(applyEffect_672)`
- L4165 ` 	(for)`
- L4170 ` 	(if)`
- L4177 ` 	(if)`
- L4186 ` 	(if)`
- L4190 ` 	(if)`
- L4194 ` 	(if)`
- L4205 ` 	(if)`
- L4215 ` void(applyEffect_765)`
- L4217 ` 	(for)`
- L4221 ` void(applyEffect_765B)`
- L4241 ` void(applyEffect_776)`
- L4245 ` 	(for)`
- L4250 ` void(applyEffect_780)`
- L4253 ` 	(for)`
- L4255 ` 	(if)`
- L4271 ` 	(if)`
- L4299 ` void(applyEffect_781)`
- L4301 ` 	(for)`
- L4305 ` void(applyEffect_782)`
- L4307 ` 	(if)`
- L4310 ` 	(for)`
- L4314 ` void(applyEffect_783)`
- L4353 ` void(applyEffect_784)`
- L4359 ` 	(for)`
- L4373 ` void(applyEffect_786)`
- L4375 ` 	(for)`
- L4379 ` void(applyEffect_787)`
- L4381 ` 	(for)`
- L4385 ` void(applyEffect_788)`
- L4387 ` 	(for)`
- L4391 ` void(applyEffect_950)`
- L4401 ` 	(if)`
- L4402 ` 	(for)`
- L4408 ` 	(for)`
- L4411 ` 	(if)`
- L4419 ` 	(if)`
- L4425 ` void(applyEffect_951)`
- L4428 ` 	(for)`
- L4437 ` 	(for)`
- L4445 ` void(applyEffect_1000)`
- L4455 ` 	(for)`
- L4457 ` 	(if)`
- L4462 ` 	(if)`
- L4471 ` 	(switch)`
- L4499 ` void(applyEffect_1001)`
- L4509 ` 	(for)`
- L4511 ` 	(if)`
- L4516 ` 	(if)`
- L4536 ` void(applyEffect_1002)`
- L4560 ` ArrayList<Fighter>(sortTargets)`
- L4565 ` 	(for)`
- L4574 ` 	(while)`
- L4577 ` 	(if)`
- L4586 ` void(checkMonsters)`
- L4588 ` 	(switch)`
- L4590 ` 	(if)`
- L4596 ` 	(if)`
- L4611 ` 	(if)`
- L4624 ` 	(if)`
- L4628 ` 	(if)`
- L4637 ` 	(if)`
- L4645 ` 	(if)`
- L4702 ` 	(if)`
- L4706 ` 	(if)`
- L4710 ` 	(if)`
- L4718 ` 	(if)`
- L4719 ` 	(if)`
- L4728 ` 	(if)`
- L4737 ` 	(if)`
- L4746 ` 	(if)`
- L4755 ` 	(if)`
- L4763 ` void(checkLifeTree)`
- L4765 ` 	(if)`
- L4772 ` t(clone)`
- L4779 ` 	(catch)`

#### `org/starloco/locos/fight/traps/Glyph.java` — 109 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Glyph
Fonctions :
- L22 ` public(Glyph)`
- L33 ` Fighter(getCaster)`
- L37 ` GameCase(getCell)`
- L41 ` byte(getSize)`
- L45 ` int(getSpell)`
- L49 ` int(decrementDuration)`
- L55 ` int(getColor)`
- L59 ` void(onTrapped)`
- L61 `  (if)`
- L62 `  (if)`
- L63 `  (if)`
- L64 `  (if)`
- L104 ` void(disappear)`

#### `org/starloco/locos/fight/traps/Trap.java` — 144 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Trap
Fonctions :
- L26 ` public(Trap)`
- L36 ` int(getSpell)`
- L40 ` GameCase(getCell)`
- L44 ` byte(getSize)`
- L48 ` Fighter(getCaster)`
- L52 ` int(getColor)`
- L56 ` void(setIsUnHide)`
- L61 ` void(disappear)`
- L73 `  (if)`
- L82 ` void(appear)`
- L93 ` void(refresh)`
- L100 ` void(onTrapped)`
- L114 `  (for)`
- L117 `  (for)`

#### `org/starloco/locos/fight/turn/Turn.java` — 56 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Turn implements Runnable
Fonctions :
- L16 ` public(Turn)`
- L24 ` long(getStartTime)`
- L28 ` void(stop)`
- L32 ` d(run)`
- L35 `  (if)`
- L39 `  (if)`

#### `org/starloco/locos/game/GameClient.java` — 7198 lignes
Rôle : Connexion client jeu : session, parsing, envoi, fermeture, contexte joueur.
Classe(s) : class GameClient
Fonctions :
- L95 ` public(GameClient)`
- L100 ` IoSession(getSession)`
- L104 ` Player(getPlayer)`
- L108 ` Account(getAccount)`
- L112 ` LangEnum(getLanguage)`
- L116 ` String(getPreparedKeys)`
- L120 ` void(parsePacket)`
- L123 `  (if)`
- L128 `  (if)`
- L129 `  (if)`
- L136 `  (if)`
- L139 `  (if)`
- L142 `  (if)`
- L152 `  (switch)`
- L240 ` void(parseAccountPacket)`
- L241 `  (switch)`
- L293 ` void(switchCharacter)`
- L312 ` void(parseAuthPackets)`
- L314 `  (switch)`
- L322 ` void(mimibiote)`
- L325 `  (if)`
- L326 `  (if)`
- L333 ` void(createMimibiote)`
- L376 ` void(dissociateMimibiote)`
- L399 `  (if)`
- L417 ` void(addCharacter)`
- L420 `  (if)`
- L428 `  (if)`
- L435 `  (if)`
- L439 `  (for)`
- L444 `  (if)`
- L448 `  (if)`
- L452 `  (if)`
- L453 `  (if)`
- L463 `  (if)`
- L467 `  (if)`
- L478 ` void(boost)`
- L481 `  (if)`
- L488 `  (if)`
- L500 ` void(deleteCharacter)`
- L505 `  (if)`
- L516 ` void(getQueuePosition)`
- L521 ` void(getGifts)`
- L523 `  (for)`
- L534 `  (if)`
- L537 `  (for)`
- L541 `  (if)`
- L542 `  (if)`
- L555 ` void(attributeGiftToCharacter)`
- L569 `  (for)`
- L574 `  (if)`
- L580 `  (if)`
- L618 ` void(sendIdentity)`
- L620 ` void(getCharacters)`
- L623 `  (for)`
- L625 `  (if)`
- L634 ` void(hardcodeRevive)`
- L639 `  (if)`
- L647 ` void(setCharacter)`
- L650 `  (if)`
- L653 `  (if)`
- L663 ` void(parseTicket)`
- L668 `  (if)`
- L696 `  (if)`
- L711 ` void(requestRegionalVersion)`
- L720 ` void(parseBasicsPacket)`
- L721 `  (switch)`
- L746 ` void(authorisedCommand)`
- L749 `  (if)`
- L759 ` void(getDate)`
- L767 ` void(tchat)`
- L771 `  (if)`
- L777 `  (if)`
- L779 `  (if)`
- L789 `  (switch)`
- L794 `  (if)`
- L803 `  (if)`
- L807 `  (if)`
- L811 `  (if)`
- L823 `  (if)`
- L837 `  (if)`
- L854 `  (if)`
- L888 `  (if)`
- L892 `  (if)`
- L897 `  (if)`
- L927 `  (if)`
- L932 `  (if)`
- L937 `  (if)`
- L974 `  (if)`
- L980 `  (if)`
- L1005 `  (if)`
- L1013 `  (if)`
- L1017 `  (if)`
- L1021 `  (if)`
- L1045 ` void(whoIs)`
- L1049 `  (if)`
- L1060 `  (if)`
- L1081 ` void(chooseState)`
- L1083 `  (switch)`
- L1085 `  (if)`
- L1094 `  (if)`
- L1106 ` void(goToMap)`
- L1141 ` void(parseConquestPacket)`
- L1142 `  (switch)`
- L1160 ` void(requestBalance)`
- L1163 `  (if)`
- L1167 ` void(getAlignedBonus)`
- L1175 ` void(worldInfos)`
- L1177 `  (switch)`
- L1188 ` void(prismInfos)`
- L1190 `  (if)`
- L1191 `  (switch)`
- L1193 `  (if)`
- L1195 `  (if)`
- L1205 ` void(prismFight)`
- L1207 `  (switch)`
- L1229 `  (if)`
- L1237 `  (if)`
- L1242 `  (if)`
- L1262 ` void(parseChanelPacket)`
- L1263 `  (switch)`
- L1269 ` void(subscribeChannels)`
- L1272 `  (switch)`
- L1288 ` void(parseDialogPacket)`
- L1289 `  (switch)`
- L1310 ` void(npcCreateDialog)`
- L1313 `  (if)`
- L1320 `  (if)`
- L1328 `  (if)`
- L1333 ` d(npcResponse)`
- L1345 `  (if)`
- L1349 `  (if)`
- L1359 ` void(quitDialog)`
- L1377 ` void(parseDocumentPacket)`
- L1378 `  (switch)`
- L1380 `  (if)`
- L1395 `synchronized void(parseExchangePacket)`
- L1398 `  (switch)`
- L1449 ` void(accept)`
- L1466 `  (switch)`
- L1493 ` void(buy)`
- L1501 `  (if)`
- L1504 `  (if)`
- L1515 `  (if)`
- L1531 `  (if)`
- L1556 `  (if)`
- L1578 `  (if)`
- L1584 `  (if)`
- L1590 `  (if)`
- L1606 ` void(bigStore)`
- L1614 `  (if)`
- L1620 `  (switch)`
- L1630 `  (if)`
- L1691 `  (if)`
- L1703 ` void(ready)`
- L1709 `  (if)`
- L1729 `  (if)`
- L1738 `  (if)`
- L1748 `  (for)`
- L1750 `  (for)`
- L1751 `  (if)`
- L1752 `  (if)`
- L1784 `  (if)`
- L1804 ` void(replayCraft)`
- L1810 `synchronized void(movementItemOrKamas)`
- L1813 `  (if)`
- L1817 `  (switch)`
- L1819 `  (switch)`
- L1821 `  (if)`
- L1876 `  (switch)`
- L1889 `  (if)`
- L1892 `  (if)`
- L1924 `  (if)`
- L1936 `  (if)`
- L1938 `  (if)`
- L2006 `  (switch)`
- L2020 `  (if)`
- L2024 `  (switch)`
- L2039 `  (switch)`
- L2041 `  (if)`
- L2105 `  (switch)`
- L2107 `  (if)`
- L2175 `  (switch)`
- L2177 `  (if)`
- L2247 `  (if)`
- L2251 `  (switch)`
- L2316 `  (if)`
- L2347 `  (if)`
- L2359 `  (switch)`
- L2418 `  (switch)`
- L2470 `  (switch)`
- L2478 `  (if)`
- L2479 `  (if)`
- L2481 `  (if)`
- L2498 `  (switch)`
- L2549 `  (switch)`
- L2565 `  (switch)`
- L2567 `  (if)`
- L2568 `  (for)`
- L2637 ` void(recursiveBreakingObject)`
- L2639 `  (if)`
- L2654 `synchronized void(movementItemOrKamasDons)`
- L2656 `  (if)`
- L2659 `  (switch)`
- L2674 ` void(askOfflineExchange)`
- L2680 `  (if)`
- L2688 `  (if)`
- L2689 `  (if)`
- L2697 `  (for)`
- L2698 `  (if)`
- L2706 `  (if)`
- L2714 ` void(offlineExchange)`
- L2722 `  (if)`
- L2723 `  (if)`
- L2732 `  (if)`
- L2736 `  (if)`
- L2749 `synchronized void(putInInventory)`
- L2751 `  (if)`
- L2759 `  (switch)`
- L2771 `  (if)`
- L2820 `  (if)`
- L2824 `  (if)`
- L2841 `  (if)`
- L2848 `  (if)`
- L2873 `synchronized void(putInMountPark)`
- L2875 `  (if)`
- L2882 `  (switch)`
- L2891 `  (if)`
- L2910 `  (if)`
- L2912 `  (if)`
- L2922 `  (if)`
- L2952 ` void(request)`
- L2954 `  (if)`
- L2958 `  (if)`
- L2966 `  (if)`
- L2971 `  (if)`
- L2975 `  (if)`
- L2986 `  (if)`
- L2992 `  (for)`
- L2998 `  (for)`
- L3021 `  (if)`
- L3050 `  (if)`
- L3055 `  (if)`
- L3059 `  (if)`
- L3071 `  (for)`
- L3093 `  (if)`
- L3117 `  (if)`
- L3123 `  (if)`
- L3132 `  (if)`
- L3144 `  (if)`
- L3153 `  (if)`
- L3159 `  (if)`
- L3170 `  (if)`
- L3178 `  (if)`
- L3182 `  (switch)`
- L3186 `  (if)`
- L3199 `  (if)`
- L3204 `  (if)`
- L3208 `  (if)`
- L3209 `  (if)`
- L3230 `  (if)`
- L3261 `  (if)`
- L3275 `  (if)`
- L3284 ` void(sell)`
- L3289 `  (if)`
- L3301 ` void(bookOfArtisant)`
- L3303 `  (switch)`
- L3307 `  (for)`
- L3319 `  (for)`
- L3337 ` void(setPublicMode)`
- L3339 `  (switch)`
- L3344 `  (for)`
- L3357 `  (for)`
- L3366 `static void(leaveExchange)`
- L3372 `  (switch)`
- L3375 `  (if)`
- L3377 `  (if)`
- L3392 `  (if)`
- L3394 `  (if)`
- L3413 `  (if)`
- L3429 `  (for)`
- L3448 `  (if)`
- L3483 ` void(parseEnvironementPacket)`
- L3484 `  (switch)`
- L3493 ` void(setDirection)`
- L3507 ` void(useEmote)`
- L3520 `  (switch)`
- L3526 `  (if)`
- L3543 `  (if)`
- L3546 `  (for)`
- L3555 `  (if)`
- L3557 `  (switch)`
- L3584 ` void(parseFrienDDacket)`
- L3585 `  (switch)`
- L3596 `  (switch)`
- L3612 ` void(addFriend)`
- L3617 `  (switch)`
- L3631 `  (if)`
- L3648 `  (if)`
- L3654 ` void(removeFriend)`
- L3659 `  (switch)`
- L3673 `  (if)`
- L3690 `  (if)`
- L3696 ` void(joinWife)`
- L3701 `  (if)`
- L3710 `  (switch)`
- L3713 `  (if)`
- L3746 ` void(parseFightPacket)`
- L3748 `  (switch)`
- L3796 ` void(parseGamePacket)`
- L3798 `  (switch)`
- L3841 `  (if)`
- L3847 `synchronized void(parseAction)`
- L3849 `  (if)`
- L3861 `  (if)`
- L3867 `  (switch)`
- L3886 `  (if)`
- L3903 `  (if)`
- L3984 `  (if)`
- L3995 `  (if)`
- L4004 ` void(gameParseDeplacementPacket)`
- L4007 `  (if)`
- L4009 `  (if)`
- L4014 `  (if)`
- L4019 `  (if)`
- L4024 `  (if)`
- L4032 `  (if)`
- L4041 `  (if)`
- L4047 `  (if)`
- L4056 `  (if)`
- L4061 `  (if)`
- L4070 `  (if)`
- L4087 `  (if)`
- L4119 `  (if)`
- L4125 ` void(gameTryCastSpell)`
- L4135 `  (if)`
- L4147 ` void(gameTryCac)`
- L4158 `synchronized void(gameAction)`
- L4170 `  (if)`
- L4185 ` void(houseAction)`
- L4191 `  (switch)`
- L4204 ` void(gameAskDuel)`
- L4216 `  (if)`
- L4231 `  (if)`
- L4235 `  (if)`
- L4239 `  (if)`
- L4243 `  (if)`
- L4247 `  (if)`
- L4260 ` void(gameAcceptDuel)`
- L4285 ` void(gameCancelDuel)`
- L4300 ` void(gameJoinFight)`
- L4308 `  (if)`
- L4319 `  (if)`
- L4325 `  (if)`
- L4335 `  (if)`
- L4339 `  (if)`
- L4365 ` void(gameAggro)`
- L4370 `  (if)`
- L4385 `  (if)`
- L4389 `  (if)`
- L4403 ` void(gameCollector)`
- L4420 `  (if)`
- L4432 ` void(gamePrism)`
- L4441 `  (if)`
- L4444 `  (if)`
- L4457 ` void(clearAllPanels)`
- L4461 ` void(clearPanelsForPlayer)`
- L4463 `  (if)`
- L4464 `  (if)`
- L4468 `  (if)`
- L4470 `  (if)`
- L4481 ` void(showMonsterTarget)`
- L4485 `  (if)`
- L4491 ` void(setFlag)`
- L4507 ` void(getExtraInformations)`
- L4510 `  (if)`
- L4511 `  (if)`
- L4523 ` void(sendExtraInformations)`
- L4527 `  (if)`
- L4575 ` void(actionAck)`
- L4592 `  (switch)`
- L4594 `  (if)`
- L4595 `  (if)`
- L4620 `  (if)`
- L4634 `  (if)`
- L4660 `  (if)`
- L4676 ` void(setPlayerPosition)`
- L4687 ` void(leaveFight)`
- L4690 `  (if)`
- L4703 `  (if)`
- L4720 ` void(readyFight)`
- L4745 ` void(parseGuildPacket)`
- L4746 `  (switch)`
- L4788 ` void(boostCaracteristique)`
- L4795 `  (switch)`
- L4832 ` void(boostSpellGuild)`
- L4840 `  (if)`
- L4851 ` void(createGuild)`
- L4855 `  (if)`
- L4869 `  (if)`
- L4878 `  (if)`
- L4884 `  (if)`
- L4886 `  (for)`
- L4892 `  (if)`
- L4893 `  (if)`
- L4900 `  (if)`
- L4901 `  (if)`
- L4911 `  (if)`
- L4917 `  (if)`
- L4944 ` void(teleportToGuildFarm)`
- L4946 `  (if)`
- L4954 `  (if)`
- L4959 `  (if)`
- L4967 ` void(removeTaxCollector)`
- L4982 `  (for)`
- L4983 `  (if)`
- L4995 ` void(teleportToGuildHouse)`
- L4997 `  (if)`
- L5008 `  (if)`
- L5012 `  (if)`
- L5016 `  (if)`
- L5024 ` void(placeTaxCollector)`
- L5031 `  (if)`
- L5037 `  (if)`
- L5063 `  (if)`
- L5068 `  (if)`
- L5089 `  (for)`
- L5091 `  (if)`
- L5101 ` void(getInfos)`
- L5103 `  (switch)`
- L5126 ` void(invitationGuild)`
- L5128 `  (switch)`
- L5131 `  (if)`
- L5135 `  (if)`
- L5139 `  (if)`
- L5143 `  (if)`
- L5147 `  (if)`
- L5194 ` void(banToGuild)`
- L5202 `  (if)`
- L5223 `  (if)`
- L5228 `  (if)`
- L5234 `  (if)`
- L5267 ` void(changeMemberProfil)`
- L5360 ` void(joinOrLeaveTaxCollector)`
- L5373 `  (if)`
- L5375 `  (switch)`
- L5383 `  (if)`
- L5408 `  (if)`
- L5415 ` void(leavePanelGuildCreate)`
- L5425 ` void(parseHousePacket)`
- L5426 `  (switch)`
- L5456 ` void(parseEnemyPacket)`
- L5457 `  (switch)`
- L5469 ` void(addEnemy)`
- L5474 `  (switch)`
- L5478 `  (if)`
- L5488 `  (if)`
- L5497 `  (if)`
- L5504 `  (if)`
- L5510 ` void(removeEnemy)`
- L5515 `  (switch)`
- L5519 `  (if)`
- L5529 `  (if)`
- L5538 `  (if)`
- L5545 `  (if)`
- L5557 ` void(parseJobOption)`
- L5558 `  (switch)`
- L5579 ` void(parseHouseKodePacket)`
- L5580 `  (switch)`
- L5589 ` void(sendKey)`
- L5591 `  (switch)`
- L5594 `  (if)`
- L5612 `  (if)`
- L5618 `  (if)`
- L5619 `  (switch)`
- L5637 ` void(parseObjectPacket)`
- L5640 `  (switch)`
- L5666 ` void(destroyObject)`
- L5678 `  (if)`
- L5688 `  (if)`
- L5704 ` void(dropObject)`
- L5722 `  (if)`
- L5727 `  (if)`
- L5738 `  (if)`
- L5756 `synchronized void(movementObject)`
- L5773 `  (if)`
- L5781 `  (if)`
- L5782 `  (if)`
- L5785 `  (if)`
- L5809 `  (if)`
- L5814 `  (if)`
- L5836 `  (if)`
- L5838 `  (if)`
- L5865 `  (if)`
- L5868 `  (for)`
- L5880 `  (if)`
- L5882 `  (for)`
- L5902 `  (if)`
- L5903 `  (if)`
- L5909 `  (if)`
- L5918 `  (if)`
- L5933 `  (if)`
- L5934 `  (if)`
- L5938 `  (if)`
- L5953 `  (if)`
- L5990 `  (if)`
- L5993 `  (for)`
- L6035 `  (if)`
- L6039 `  (if)`
- L6043 `  (if)`
- L6059 `  (if)`
- L6063 `  (if)`
- L6076 `  (if)`
- L6112 `  (if)`
- L6114 `  (if)`
- L6126 `  (if)`
- L6138 `  (if)`
- L6147 `  (if)`
- L6150 `  (if)`
- L6189 `  (if)`
- L6205 ` void(setItemShortcut)`
- L6214 `  (switch)`
- L6227 ` void(useObject)`
- L6269 `  (if)`
- L6281 ` void(dissociateObvi)`
- L6296 `  (if)`
- L6299 `  (switch)`
- L6321 `  (if)`
- L6334 ` void(feedObvi)`
- L6355 `  (if)`
- L6365 ` void(setSkinObvi)`
- L6395 ` void(parseGroupPacket)`
- L6396 `  (switch)`
- L6420 ` void(acceptInvitation)`
- L6431 `  (if)`
- L6451 ` void(followMember)`
- L6486 ` void(followAllMember)`
- L6506 `  (for)`
- L6519 `  (for)`
- L6530 ` void(inviteParty)`
- L6537 `  (if)`
- L6542 `  (if)`
- L6546 `  (if)`
- L6547 `  (if)`
- L6552 `  (if)`
- L6562 ` void(refuseInvitation)`
- L6568 `  (if)`
- L6576 ` void(leaveParty)`
- L6579 `  (if)`
- L6581 `  (if)`
- L6600 ` void(whereIsParty)`
- L6609 `  (for)`
- L6625 ` void(parseMountPacket)`
- L6626 `  (switch)`
- L6662 ` void(buyMountPark)`
- L6667 `  (if)`
- L6671 `  (if)`
- L6675 `  (if)`
- L6679 `  (if)`
- L6690 `  (if)`
- L6694 `  (if)`
- L6700 `  (if)`
- L6703 `  (if)`
- L6713 `  (for)`
- L6717 ` void(dataMount)`
- L6721 `  (if)`
- L6729 ` void(killMount)`
- L6731 `  (if)`
- L6743 ` void(renameMount)`
- L6751 ` void(rideMount)`
- L6753 `  (if)`
- L6760 ` void(sellMountPark)`
- L6765 `  (if)`
- L6769 `  (if)`
- L6773 `  (if)`
- L6781 `  (for)`
- L6785 ` void(setXpMount)`
- L6799 ` void(castrateMount)`
- L6801 `  (if)`
- L6808 ` void(removeObjectInMountPark)`
- L6815 `  (if)`
- L6820 `  (if)`
- L6830 `  (for)`
- L6831 `  (if)`
- L6850 ` void(parseQuestData)`
- L6851 `  (switch)`
- L6867 ` void(parseSpellPacket)`
- L6868 `  (switch)`
- L6883 ` void(boostSpell)`
- L6887 `  (if)`
- L6899 ` void(forgetSpell)`
- L6906 `  (if)`
- L6912 ` void(removeSpell)`
- L6923 ` void(moveSpell)`
- L6929 `  (if)`
- L6934 `  (if)`
- L6949 ` void(parseWaypointPacket)`
- L6950 `  (switch)`
- L6971 ` void(waypointUse)`
- L6989 ` void(zaapiUse)`
- L6991 `  (if)`
- L7006 ` void(prismUse)`
- L7008 `  (if)`
- L7014 ` void(waypointLeave)`
- L7018 ` void(zaapiLeave)`
- L7022 ` void(prismLeave)`
- L7032 ` void(parseTutorialsPacket)`
- L7037 `  (if)`
- L7056 ` void(kick)`
- L7061 ` void(disconnect)`
- L7066 ` void(addAction)`
- L7071 `  (if)`
- L7077 `synchronized void(removeAction)`
- L7107 ` boolean(distPecheur)`
- L7127 ` void(changeName)`
- L7129 `  (if)`
- L7140 `  (if)`
- L7144 `  (for)`
- L7149 `  (if)`
- L7153 `  (if)`
- L7157 `  (if)`
- L7158 `  (if)`
- L7167 `  (if)`
- L7178 ` void(send)`
- L7189 ` void(send)`
- L7191 `  (if)`

#### `org/starloco/locos/game/GameHandler.java` — 129 lignes
Rôle : Dispatch des packets de jeu vers handlers/méthodes métier.
Classe(s) : class GameHandler implements IoHandler
Fonctions :
- L18 ` d(sessionCreated)`
- L28 ` d(messageReceived)`
- L33 `  (if)`
- L35 `  (if)`
- L42 `  (for)`
- L44 `  (if)`
- L49 `  (if)`
- L51 `  (if)`
- L64 `  (if)`
- L71 ` d(sessionClosed)`
- L77 ` d(exceptionCaught)`
- L89 ` d(messageSent)`
- L93 `  (if)`
- L95 `  (if)`
- L104 ` d(inputClosed)`
- L109 ` d(sessionIdle)`
- L114 ` d(sessionOpened)`
- L119 ` void(kick)`
- L122 `  (if)`

#### `org/starloco/locos/game/GameServer.java` — 111 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class GameServer
Fonctions :
- L29 ` public(GameServer)`
- L39 ` void(initialize)`
- L54 ` void(close)`
- L64 ` ArrayList<GameClient>(getClients)`
- L68 ` int(getPlayersNumberByIp)`
- L77 `static void(setState)`
- L82 ` Account(getWaitingAccount)`
- L89 ` void(deleteWaitingAccount)`
- L93 ` void(addWaitingAccount)`
- L97 `static void(a)`
- L99 ` void(kickAll)`
- L102 `  (if)`

#### `org/starloco/locos/game/action/ExchangeAction.java` — 65 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ExchangeAction <T>
Fonctions :
- L43 ` public(ExchangeAction)`
- L48 ` byte(getType)`
- L52 ` T(getValue)`
- L56 ` void(putContextValue)`
- L60 ` Object(getContextValue)`

#### `org/starloco/locos/game/action/GameAction.java` — 14 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class GameAction
Fonctions :
- L8 ` public(GameAction)`

#### `org/starloco/locos/game/action/type/ActionDataInterface.java` — 5 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : interface ActionDataInterface
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/game/action/type/BigStoreActionData.java` — 28 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class BigStoreActionData implements ActionDataInterface
Fonctions :
- L7 ` public(BigStoreActionData)`
- L11 ` int(getCategoryId)`
- L15 ` void(setCategoryId)`
- L19 ` int(getTemplateId)`
- L23 ` void(setTemplateId)`

#### `org/starloco/locos/game/action/type/DocumentActionData.java` — 20 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class DocumentActionData implements ActionDataInterface
Fonctions :
- L8 ` public(DocumentActionData)`
- L12 ` void(onQuestHRef)`

#### `org/starloco/locos/game/action/type/EmptyActionData.java` — 8 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class EmptyActionData
Fonctions :
- L5 ` private(EmptyActionData)`

#### `org/starloco/locos/game/action/type/NpcDialogActionData.java` — 51 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class NpcDialogActionData implements ActionDataInterface
Fonctions :
- L17 ` public(NpcDialogActionData)`
- L22 ` NpcTemplate(getNpcTemplate)`
- L26 ` int(getQuestionId)`
- L30 ` void(setQuestionId)`
- L34 ` boolean(hasAnswer)`
- L38 ` void(setAnswers)`
- L42 ` Npc(getNpc)`
- L46 ` boolean(isValid)`

#### `org/starloco/locos/game/action/type/ScenarioActionData.java` — 22 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ScenarioActionData implements ActionDataInterface
Fonctions :
- L11 ` public(ScenarioActionData)`
- L16 ` void(onCompletion)`

#### `org/starloco/locos/game/filter/IpInstance.java` — 44 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class IpInstance
Fonctions :
- L11 ` public(IpInstance)`
- L15 ` void(addConnection)`
- L19 ` void(resetConnections)`
- L23 ` void(updateLastConnection)`
- L27 ` void(ban)`
- L31 ` boolean(isBanned)`
- L35 ` long(getLastConnection)`
- L39 ` int(getConnections)`

#### `org/starloco/locos/game/filter/PacketFilter.java` — 70 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class PacketFilter
Fonctions :
- L12 ` public(PacketFilter)`
- L17 `synchronized boolean(safeCheck)`
- L21 ` boolean(unSafeCheck)`
- L24 `  (if)`
- L29 `  (if)`
- L44 ` boolean(authorizes)`
- L48 ` PacketFilter(activeSafeMode)`
- L53 ` IpInstance(find)`
- L65 ` String(clearIp)`

#### `org/starloco/locos/game/scheduler/IUpdatable.java` — 11 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : interface IUpdatable <T>
Fonctions :
- L9 ` default T(get)`

#### `org/starloco/locos/game/scheduler/Updatable.java` — 24 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Updatable <T> implements IUpdatable<T>
Fonctions :
- L10 ` public(Updatable)`
- L14 ` boolean(verify)`
- L16 `  (if)`

#### `org/starloco/locos/game/scheduler/entity/WorldPub.java` — 41 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class WorldPub extends Updatable<Void>
Fonctions :
- L16 ` public(WorldPub)`
- L20 ` d(update)`
- L23 `  (if)`
- L24 `  (if)`
- L36 ` d(get)`

#### `org/starloco/locos/game/scheduler/entity/WorldSave.java` — 131 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class WorldSave extends Updatable<Void>
Fonctions :
- L21 ` private(WorldSave)`
- L25 ` d(update)`
- L29 `  (if)`
- L36 `static void(cast)`
- L103 `  (if)`
- L114 `  (if)`
- L126 ` d(get)`

#### `org/starloco/locos/game/world/HouseManager.java` — 299 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class HouseManager
Fonctions :
- L23 ` House(getHouseIdByCoord)`
- L31 ` void(load)`
- L36 `  (if)`
- L54 `  (if)`
- L75 `  (if)`
- L79 `  (if)`
- L93 `  (if)`
- L117 `  (if)`
- L140 `  (if)`
- L150 ` void(closeCode)`
- L155 ` void(closeBuy)`
- L159 ` void(lockIt)`
- L162 `  (if)`
- L165 `  (if)`
- L171 ` String(parseHouseToGuild)`
- L175 `  (for)`
- L176 `  (if)`
- L180 `  (if)`
- L182 `  (if)`
- L186 `  (if)`
- L219 ` boolean(alreadyHaveHouse)`
- L226 ` void(parseHG)`
- L231 `  (if)`
- L232 `  (if)`
- L239 `  (if)`
- L253 `  (if)`
- L262 ` byte(houseOnGuild)`
- L270 ` void(leave)`
- L283 ` House(getHouseByPerso)`
- L290 ` void(removeHouseGuild)`

#### `org/starloco/locos/game/world/World.java` — 1753 lignes
Rôle : Registre global : maps, joueurs, comptes, templates, mobs, sorts, guildes, maisons, cache.
Classe(s) : class World implements Scripted<SWorld>
Fonctions :
- L100 ` public(World)`
- L104 ` Map<Integer, Long>(getDelayCollectors)`
- L110 ` HouseManager(getHouseManager)`
- L116 ` CryptManager(getCryptManager)`
- L122 ` ConditionParser(getConditionManager)`
- L128 ` void(addAccount)`
- L131 ` Account(ensureAccountLoaded)`
- L134 `  (if)`
- L140 ` Collection<Account>(getAccounts)`
- L144 ` List<Account>(getAccountsByIp)`
- L151 ` Account(getAccountByPseudo)`
- L161 ` Collection<Player>(getPlayers)`
- L164 ` void(addPlayer)`
- L168 ` Player(getPlayerByName)`
- L175 ` Player(getPlayer)`
- L179 ` List<Player>(getOnlinePlayers)`
- L186 ` Collection<GameMap>(getMaps)`
- L189 ` void(addMapData)`
- L195 `  (if)`
- L205 ` Optional<ScriptMapData>(getMapData)`
- L209 ` GameMap(getMap)`
- L222 ` CopyOnWriteArrayList<GameObject>(getGameObjects)`
- L225 ` void(addGameObject)`
- L231 ` GameObject(getGameObject)`
- L234 `  (if)`
- L239 ` void(removeGameObject)`
- L241 `  (if)`
- L247 ` Map<Integer, Spell>(getSpells)`
- L251 ` Map<Integer, ObjectTemplate>(getObjectsTemplates)`
- L255 ` Map<Integer, Mount>(getMounts)`
- L259 ` Map<Integer, Area>(getAreas)`
- L263 ` Map<Integer, SubArea>(getSubAreas)`
- L267 ` Map<Integer, Guild>(getGuilds)`
- L271 ` Map<Integer, MountPark>(getMountparks)`
- L275 ` Map<Integer, Trunk>(getTrunks)`
- L279 ` Map<Integer, Collector>(getCollectors)`
- L283 ` Map<Integer, House>(getHouses)`
- L287 ` Map<Integer, Prism>(getPrisms)`
- L291 ` Map<Integer, Map<String, Map<String, Integer>>>(getExtraMonsters)`
- L295 ` void(loadScripts)`
- L306 ` void(createWorld)`
- L441 ` void(addExtraMonster)`
- L449 ` Map<Integer, GameMap>(getExtraMonsterOnMap)`
- L453 ` void(loadExtraMonster)`
- L456 `  (for)`
- L459 `  (for)`
- L462 `  (for)`
- L467 `  (if)`
- L468 `  (for)`
- L470 `  (for)`
- L487 `  (for)`
- L489 `  (for)`
- L498 `  (for)`
- L515 `  (if)`
- L541 ` Map<String, String>(getGroupFix)`
- L545 ` void(addGroupFix)`
- L551 ` void(loadMonsterOnMap)`
- L564 ` Area(getArea)`
- L568 ` SubArea(getSubArea)`
- L573 ` void(addArea)`
- L577 ` void(addSubArea)`
- L582 ` String(getSousZoneStateString)`
- L586 `  (for)`
- L596 ` double(getBalanceArea)`
- L599 `  (for)`
- L608 ` double(getBalanceWorld)`
- L611 `  (for)`
- L619 ` double(getConquestBonus)`
- L627 ` void(registerObjectTemplate)`
- L629 `  (synchronized)`
- L633 ` void(setObjectForSprites)`
- L635 `  (synchronized)`
- L640 ` Optional<Integer>(getObjectIDForSprite)`
- L642 `  (synchronized)`
- L646 ` Optional<InteractiveObjectTemplate>(getObject)`
- L648 `  (synchronized)`
- L652 ` Optional<InteractiveObjectTemplate>(getObjectBySprite)`
- L656 ` ExperienceTables(getExperiences)`
- L660 ` void(setExperiences)`
- L664 ` NpcTemplate(getNPCTemplate)`
- L668 ` void(addNpcTemplate)`
- L675 ` void(removePlayer)`
- L677 `  (if)`
- L678 `  (if)`
- L695 `  (if)`
- L697 `  (if)`
- L706 ` void(unloadPerso)`
- L711 ` void(addSort)`
- L715 ` Spell(getSort)`
- L719 ` void(addObjTemplate)`
- L723 ` ObjectTemplate(getObjTemplate)`
- L727 ` ArrayList<ObjectTemplate>(getEtherealWeapons)`
- L735 ` void(addMobTemplate)`
- L739 ` Monster(getMonstre)`
- L743 ` Collection<Monster>(getMonstres)`
- L747 ` String(getStatOfAlign)`
- L752 `  (for)`
- L770 ` Mount(getMountById)`
- L774 `  (if)`
- L780 ` void(addMount)`
- L784 ` void(removeMount)`
- L788 ` Collection<Job>(getJobs)`
- L792 ` Job(getMetier)`
- L796 ` void(addJob)`
- L800 ` void(addCraft)`
- L804 ` ArrayList<Couple<Integer, Integer>>(getCraft)`
- L808 ` void(addFullMorph)`
- L819 `  (if)`
- L834 ` Map<String, String>(getFullMorph)`
- L838 ` int(getObjectByIngredientForJob)`
- L843 `  (for)`
- L850 `  (for)`
- L859 ` void(addItemSet)`
- L864 ` ObjectSet(getItemSet)`
- L868 ` int(getItemSetNumber)`
- L872 ` ArrayList<GameMap>(getMapByPosInArray)`
- L880 ` List<Integer>(getMapIdByPosInSuperArea)`
- L889 ` void(addGuild)`
- L893 ` boolean(guildNameIsUsed)`
- L900 ` boolean(guildEmblemIsUsed)`
- L902 `  (for)`
- L908 ` Guild(getGuild)`
- L911 `  (if)`
- L917 ` int(getGuildByName)`
- L919 `  (for)`
- L925 ` long(getGuildXpMax)`
- L929 ` int(getZaapCellIdByMapId)`
- L933 ` int(getEncloCellIdByMapId)`
- L940 ` void(delDragoByID)`
- L944 ` void(removeGuild)`
- L953 ` void(unloadPerso)`
- L961 ` GameObject(newObjet)`
- L963 `  (if)`
- L966 `  (if)`
- L984 ` Map<Integer, Integer>(getChangeHdv)`
- L1009 ` int(changeHdv)`
- L1011 `  (if)`
- L1016 ` BigStore(getHdv)`
- L1020 ` void(addHdvItem)`
- L1026 ` void(removeHdvItem)`
- L1030 ` void(addHdv)`
- L1034 ` Map<Integer, List<BigStoreListing>>(getMyItems)`
- L1041 ` Collection<ObjectTemplate>(getObjTemplates)`
- L1045 ` void(priestRequest)`
- L1047 `  (if)`
- L1049 `  (if)`
- L1054 `  (if)`
- L1063 ` void(wedding)`
- L1066 `  (if)`
- L1078 ` Optional<Animation>(getAnimation)`
- L1082 ` void(addAnimation)`
- L1086 ` void(addHouse)`
- L1090 ` House(getHouse)`
- L1094 ` void(addCollector)`
- L1098 ` Collector(getCollector)`
- L1102 ` void(addTrunk)`
- L1106 ` Trunk(getTrunk)`
- L1110 ` void(addMountPark)`
- L1114 ` Map<Integer, MountPark>(getMountParks)`
- L1118 ` String(parseMPtoGuild)`
- L1124 `  (for)`
- L1126 `  (if)`
- L1129 `  (if)`
- L1132 `  (for)`
- L1134 `  (if)`
- L1150 ` int(totalMPGuild)`
- L1158 ` void(addChallenge)`
- L1164 `synchronized void(addPrisme)`
- L1168 ` Prism(getPrisme)`
- L1172 ` void(removePrisme)`
- L1176 ` Collection<Prism>(AllPrisme)`
- L1182 ` String(PrismesGeoposition)`
- L1187 `  (for)`
- L1209 `  (for)`
- L1228 ` Map<Integer, Long>(subAreaCountByFaction)`
- L1232 ` void(showPrismes)`
- L1234 `  (for)`
- L1241 `synchronized int(getNextIDPrisme)`
- L1249 ` void(addPets)`
- L1253 ` Pet(getPets)`
- L1257 ` Collection<Pet>(getPets)`
- L1261 ` void(addPetsEntry)`
- L1265 ` PetEntry(getPetsEntry)`
- L1269 ` PetEntry(removePetsEntry)`
- L1273 ` String(getChallengeFromConditions)`
- L1282 `  (for)`
- L1328 `  (switch)`
- L1338 `  (switch)`
- L1341 `  (switch)`
- L1354 `  (switch)`
- L1362 `  (switch)`
- L1372 `  (switch)`
- L1385 `  (switch)`
- L1395 `  (switch)`
- L1405 `  (switch)`
- L1412 `  (switch)`
- L1423 `  (switch)`
- L1438 ` void(verifyClone)`
- L1440 `  (if)`
- L1441 `  (if)`
- L1449 ` ArrayList<String>(getRandomChallenge)`
- L1466 `  (while)`
- L1482 `  (if)`
- L1489 `  (if)`
- L1496 `  (if)`
- L1503 `  (if)`
- L1515 ` Collector(getCollectorByMap)`
- L1517 `  (for)`
- L1520 `  (if)`
- L1526 ` void(addSeller)`
- L1532 `  (if)`
- L1545 ` Collection<Integer>(getSeller)`
- L1549 ` void(removeSeller)`
- L1554 ` double(getTauxObtentionIntermediaire)`
- L1560 `  (if)`
- L1573 ` int(getMetierByMaging)`
- L1576 `  (switch)`
- L1613 ` int(getTempleByClasse)`
- L1616 `  (switch)`
- L1656 ` void(sendMessageToAll)`
- L1663 ` d(scripted)`
- L1668 ` Account(getAccount)`
- L1682 ` public(Drop)`
- L1691 ` public(Drop)`
- L1700 ` int(getObjectId)`
- L1704 ` int(getCeil)`
- L1708 ` int(getAction)`
- L1712 ` int(getLevel)`
- L1716 ` String(getCondition)`
- L1720 ` double(getLocalPercent)`
- L1724 ` Drop(copy)`
- L1742 ` public(Couple)`
- L1747 ` L(getFirst)`
- L1749 ` R(getSecond)`

#### `org/starloco/locos/guild/Guild.java` — 275 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Guild
Fonctions :
- L31 ` public(Guild)`
- L42 ` public(Guild)`
- L55 ` void(addMember)`
- L59 `  (if)`
- L63 ` GuildMember(addNewMember)`
- L70 ` int(getId)`
- L74 ` void(setId)`
- L78 ` int(getNbCollectors)`
- L82 ` void(setNbCollectors)`
- L86 ` int(getCapital)`
- L90 ` void(setCapital)`
- L94 ` Map<Integer, SortStats>(getSpells)`
- L98 ` Map<Integer, Integer>(getStats)`
- L102 ` long(getDate)`
- L106 ` void(boostSpell)`
- L113 ` void(unBoostSpell)`
- L116 `  (if)`
- L121 ` String(getName)`
- L125 ` void(setName)`
- L129 ` String(getEmblem)`
- L133 ` long(getXp)`
- L137 ` int(getLvl)`
- L141 ` boolean(haveTenMembers)`
- L145 ` List<Player>(getPlayers)`
- L154 ` GuildMember(getMember)`
- L161 ` void(removeMember)`
- L170 ` void(addXp)`
- L175 ` void(levelUp)`
- L180 ` void(decompileSpell)`
- L185 ` String(compileSpell)`
- L192 `  (for)`
- L202 ` void(decompileStats)`
- L207 ` String(compileStats)`
- L214 `  (for)`
- L226 ` void(upgradeStats)`
- L230 ` int(resetStats)`
- L236 ` int(getStats)`
- L242 ` String(parseCollectorToGuild)`
- L245 ` String(encodeTaxCollectorDQ)`
- L249 ` String(parseMembersToGM)`
- L252 `  (for)`

#### `org/starloco/locos/guild/GuildMember.java` — 160 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class GuildMember
Fonctions :
- L27 `  (GuildMember)`
- L38 ` Player(getPlayer)`
- L42 ` int(getPlayerId)`
- L46 ` String(getName)`
- L50 ` int(getAlign)`
- L54 ` int(getGfx)`
- L58 ` int(getLvl)`
- L62 ` Guild(getGuild)`
- L66 ` int(getRank)`
- L70 ` void(setRank)`
- L74 ` long(getXpGave)`
- L78 ` int(getXpGive)`
- L82 ` void(giveXpToGuild)`
- L87 ` String(parseRights)`
- L91 ` int(getRights)`
- L95 ` String(getLastCo)`
- L99 ` void(setLastCo)`
- L103 ` int(getHoursFromLastCo)`
- L109 ` boolean(canDo)`
- L113 ` void(setAllRights)`
- L129 ` void(initRights)`
- L133 `  (for)`
- L137 ` void(parseIntToRight)`
- L139 `  (if)`
- L142 `  (if)`
- L148 `  (while)`
- L151 `  (if)`

#### `org/starloco/locos/hdv/BigStore.java` — 269 lignes
Rôle : HDV classique : listes, lots, prix, achat/vente.
Classe(s) : class BigStore, interface CategoryFilter
Fonctions :
- L39 ` public(CheapestListings)`
- L65 ` public(BigStore)`
- L74 ` int(getHdvId)`
- L78 ` float(getTaxe)`
- L82 ` short(getDuration)`
- L86 ` short(getMaxAccountItem)`
- L90 ` String(getStrCategory)`
- L94 ` short(getLvlMax)`
- L98 ` Stream<BigStoreListing>(getEntriesByTemplate)`
- L103 ` List<CheapestListings>(linesForTemplate)`
- L105 `  (synchronized)`
- L121 ` Optional<BigStoreListing>(cheapestListing)`
- L128 ` CategoryFilter(categoryFilters)`
- L130 `  (switch)`
- L152 ` Function<BigStoreListing, Stream<Integer>>(categoryTemplateMapper)`
- L154 `  (switch)`
- L164 ` boolean(addEntry)`
- L168 `  (synchronized)`
- L170 `  (if)`
- L172 `  (if)`
- L192 ` boolean(removeListing)`
- L198 `  (synchronized)`
- L207 `  (if)`
- L217 ` boolean(deleteListing)`
- L225 ` Optional<CheapestListings>(getCheapestListings)`
- L229 ` Optional<BigStoreListing>(buyItem)`
- L231 `  (synchronized)`
- L236 `  (if)`
- L253 ` String(parseTaxe)`
- L257 ` List<Integer>(getCategoryContent)`
- L261 `  (synchronized)`

#### `org/starloco/locos/hdv/BigStoreListing.java` — 80 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class BigStoreListing
Fonctions :
- L14 ` public(BigStoreListing)`
- L18 ` public(BigStoreListing)`
- L26 ` int(getId)`
- L30 ` void(setId)`
- L34 ` int(getHdvId)`
- L38 ` void(setHdvId)`
- L42 ` void(setLineId)`
- L46 ` int(getLineId)`
- L49 ` int(getOwner)`
- L53 ` int(getPrice)`
- L57 ` BigStoreListingLotSize(getLotSize)`
- L61 ` GameObject(getGameObject)`
- L65 ` String(parseToEL)`
- L73 ` String(parseToEmK)`

#### `org/starloco/locos/hdv/BigStoreListingLotSize.java` — 23 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : enum BigStoreListingLotSize
Fonctions :
- L10 `  (BigStoreListingLotSize)`
- L15 `static BigStoreListingLotSize(fromValue)`
- L17 `  (for)`

#### `org/starloco/locos/invoker/EventDispatcherInvoker.java` — 32 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class EventDispatcherInvoker <T>
Fonctions :
- L13 ` public(EventDispatcherInvoker)`
- L19 ` T(getMessage)`
- L23 ` Method(getMethods)`
- L27 ` Object(getHandler)`

#### `org/starloco/locos/job/Job.java` — 87 lignes
Rôle : Métier : XP métier, actions, recettes, récolte/craft.
Classe(s) : class Job
Fonctions :
- L13 ` public(Job)`
- L16 `  (if)`
- L17 `  (for)`
- L25 `  (if)`
- L26 `  (for)`
- L38 `  (if)`
- L39 `  (for)`
- L51 ` int(getId)`
- L55 ` Map<Integer, ArrayList<Integer>>(getSkills)`
- L59 ` Map<Integer, ArrayList<Integer>>(getCrafts)`
- L63 ` boolean(isValidTool)`
- L70 ` ArrayList<Integer>(getListBySkill)`
- L74 ` boolean(canCraft)`
- L82 ` boolean(isMaging)`

#### `org/starloco/locos/job/JobAction.java` — 2758 lignes
Rôle : Action métier : récolte/craft, temps, chance, quantité.
Classe(s) : class JobAction
Fonctions :
- L35 ` public(JobAction)`
- L45 ` int(getId)`
- L49 ` int(getMin)`
- L53 ` int(getMax)`
- L57 ` boolean(isCraft)`
- L61 ` int(getChance)`
- L65 ` int(getTime)`
- L69 ` int(getXpWin)`
- L73 ` JobStat(getJobStat)`
- L77 ` JobCraft(getJobCraft)`
- L81 ` void(setJobCraft)`
- L85 ` void(startCraft)`
- L89 ` int(addCraftObject)`
- L91 `  (for)`
- L106 ` void(addIngredient)`
- L113 `  (if)`
- L121 ` byte(sizeList)`
- L124 `  (for)`
- L126 `  (for)`
- L128 `  (if)`
- L136 ` void(putLastCraftIngredients)`
- L146 ` void(resetCraft)`
- L153 ` boolean(craftPublicMode)`
- L160 `  (if)`
- L168 `  (for)`
- L171 `  (for)`
- L173 `  (if)`
- L180 `  (if)`
- L185 `  (if)`
- L195 `  (if)`
- L226 `  (if)`
- L239 `  (if)`
- L263 `  (if)`
- L273 ` boolean(isMaging)`
- L278 `synchronized void(craft)`
- L281 `  (if)`
- L289 `  (for)`
- L296 `  (if)`
- L301 `  (if)`
- L308 `  (if)`
- L322 `  (if)`
- L331 `  (if)`
- L336 `  (if)`
- L350 `  (if)`
- L356 `  (switch)`
- L365 `  (if)`
- L371 `  (if)`
- L391 `  (if)`
- L400 `  (if)`
- L409 `  (if)`
- L429 `  (if)`
- L439 `synchronized boolean(craftMaging)`
- L448 `  (if)`
- L450 `  (for)`
- L451 `  (for)`
- L456 `  (for)`
- L459 `  (if)`
- L474 `  (switch)`
- L1019 `  (if)`
- L1025 `  (if)`
- L1047 `  (if)`
- L1049 `  (if)`
- L1062 `  (if)`
- L1072 `  (if)`
- L1084 `  (if)`
- L1101 `  (if)`
- L1113 `  (if)`
- L1120 `  (if)`
- L1134 `  (if)`
- L1145 `  (if)`
- L1147 `  (if)`
- L1156 `  (if)`
- L1159 `  (if)`
- L1169 `  (if)`
- L1178 `  (if)`
- L1180 `  (for)`
- L1202 `  (if)`
- L1204 `  (if)`
- L1208 `  (if)`
- L1212 `  (if)`
- L1216 `  (if)`
- L1220 `  (if)`
- L1224 `  (if)`
- L1229 `  (if)`
- L1256 `  (if)`
- L1262 ` else(if)`
- L1264 `  (if)`
- L1270 `  (if)`
- L1272 `  (if)`
- L1276 `  (if)`
- L1280 `  (if)`
- L1284 `  (if)`
- L1288 `  (if)`
- L1292 `  (if)`
- L1297 `  (if)`
- L1303 `  (if)`
- L1327 `  (if)`
- L1339 `  (if)`
- L1367 `  (if)`
- L1370 `  (if)`
- L1376 `  (if)`
- L1384 `  (if)`
- L1392 `  (if)`
- L1393 `  (for)`
- L1395 `  (for)`
- L1411 `  (if)`
- L1426 ` void(decrementObjectQuantity)`
- L1427 `  (if)`
- L1429 `  (if)`
- L1438 `static int(getStatBaseMaxs)`
- L1441 `  (for)`
- L1454 `static int(getStatBaseMins)`
- L1457 `  (for)`
- L1466 `static int(WeithTotalBaseMin)`
- L1475 `  (for)`
- L1500 `  (if)`
- L1540 `static int(WeithTotalBase)`
- L1549 `  (for)`
- L1577 `  (if)`
- L1617 `static int(currentWeithStats)`
- L1619 `  (for)`
- L1626 `  (if)`
- L1631 `  (if)`
- L1672 `static int(currentStats)`
- L1674 `  (for)`
- L1683 `static int(currentTotalWeigthBase)`
- L1690 `  (for)`
- L1723 `  (if)`
- L1728 `  (if)`
- L1766 `static int(getBaseMaxJet)`
- L1770 `  (for)`
- L1784 `static int(getActualJet)`
- L1786 `  (for)`
- L1800 `  (if)`
- L1801 `  (for)`
- L1839 `  (for)`
- L1843 `  (if)`
- L1870 `static double(getPwrPerEffet)`
- L1873 `  (switch)`
- L2018 `static double(getOverPerEffet)`
- L2021 `  (switch)`
- L2170 `synchronized void(craftMaging1)`
- L2177 `  (for)`
- L2191 `  (if)`
- L2196 `  (if)`
- L2204 `  (if)`
- L2206 `  (if)`
- L2218 `  (for)`
- L2228 `  (for)`
- L2237 `  (if)`
- L2243 `  (for)`
- L2246 `  (if)`
- L2251 `  (if)`
- L2269 `  (for)`
- L2297 `  (if)`
- L2300 `  (if)`
- L2326 `  (if)`
- L2333 `  (if)`
- L2369 `  (if)`
- L2375 `  (if)`
- L2409 `  (if)`
- L2416 `  (if)`
- L2428 `  (if)`
- L2435 `  (if)`
- L2445 `  (if)`
- L2467 `  (if)`
- L2486 `  (if)`
- L2497 `  (if)`
- L2503 `  (if)`
- L2508 `  (while)`
- L2516 `  (if)`
- L2526 `  (if)`
- L2549 `  (if)`
- L2551 `  (while)`
- L2590 `  (if)`
- L2592 `  (if)`
- L2620 `  (if)`
- L2636 ` boolean(analyzeObject)`
- L2638 `  (for)`
- L2639 `  (switch)`
- L2679 ` List<String>(getStatsToLoose)`
- L2682 `  (for)`
- L2693 `  (for)`
- L2701 `  (if)`
- L2715 ` float(getPWR)`
- L2719 `  (switch)`
- L2729 ` boolean(isAvailableObject)`
- L2731 `  (switch)`

#### `org/starloco/locos/job/JobConstant.java` — 656 lignes
Rôle : Constantes StarLoco : stats, items positions, classes, sorts level-up, métiers.
Classe(s) : class JobConstant
Fonctions :
- L143 `static int(getTotalCaseByJobLevel)`
- L149 `static int(getChanceForMaxCase)`
- L157 `static boolean(isJobAction)`
- L164 `static int(getObjectByJobSkill)`
- L166 `  (for)`
- L167 `  (if)`
- L168 `  (if)`
- L176 `static int(getChanceByNbrCaseByLvl)`
- L182 `static boolean(isMageJob)`
- L186 `static String(actionMetier)`
- L188 `  (switch)`
- L214 `static int(getProtectorLvl)`
- L228 `static ArrayList<JobAction>(getPosActionsToJob)`
- L232 `  (switch)`
- L383 `  (if)`
- L387 `  (if)`
- L391 `  (if)`
- L395 `  (if)`
- L399 `  (if)`
- L405 `  (if)`
- L409 `  (if)`
- L413 `  (if)`
- L417 `  (if)`
- L429 `  (if)`
- L433 `  (if)`
- L437 `  (if)`
- L441 `  (if)`
- L445 `  (if)`
- L449 `  (if)`
- L465 `  (if)`
- L471 `  (if)`
- L475 `  (if)`
- L479 `  (if)`
- L483 `  (if)`
- L493 `  (if)`
- L497 `  (if)`
- L501 `  (if)`
- L507 `  (if)`
- L511 `  (if)`
- L515 `  (if)`
- L519 `  (if)`
- L525 `  (if)`
- L529 `  (if)`
- L535 `  (if)`
- L539 `  (if)`
- L543 `  (if)`
- L554 `  (if)`
- L558 `  (if)`
- L562 `  (if)`
- L568 `  (if)`
- L572 `  (if)`
- L576 `  (if)`
- L580 `  (if)`
- L594 `static int(getDistCanne)`
- L596 `  (switch)`
- L622 `static int(getPoissonRare)`
- L624 `  (switch)`

#### `org/starloco/locos/job/JobCraft.java` — 76 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class JobCraft
Fonctions :
- L16 `  (JobCraft)`
- L27 ` JobAction(getJobAction)`
- L31 ` void(setAction)`
- L37 ` void(repeat)`
- L42 `  (if)`
- L49 ` boolean(check)`
- L51 `  (if)`
- L66 ` void(end)`
- L71 `  (if)`

#### `org/starloco/locos/job/JobStat.java` — 155 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class JobStat
Fonctions :
- L26 ` public(JobStat)`
- L34 ` int(getId)`
- L38 ` Job(getTemplate)`
- L42 ` int(get_lvl)`
- L46 ` long(getXp)`
- L50 ` int(getSlotsPublic)`
- L54 ` void(setSlotsPublic)`
- L58 ` int(getPosition)`
- L62 ` JobAction(getJobActionBySkill)`
- L69 ` void(addXp)`
- L80 `  (if)`
- L92 ` String(getXpString)`
- L97 ` void(levelUp)`
- L101 `  (if)`
- L112 ` String(parseJS)`
- L117 `  (for)`
- L130 ` int(getOptBinValue)`
- L138 ` void(setOptBinValue)`
- L147 ` boolean(isValidMapAction)`

#### `org/starloco/locos/job/maging/BreakingObject.java` — 74 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class BreakingObject
Fonctions :
- L12 ` void(setCount)`
- L16 ` int(getCount)`
- L20 ` void(setStop)`
- L24 ` boolean(isStop)`
- L28 ` void(setObjects)`
- L32 ` ArrayList<Couple<Integer, Integer>>(getObjects)`
- L36 `synchronized int(addObject)`
- L39 `  (if)`
- L48 `synchronized int(removeObject)`
- L51 `  (if)`
- L53 `  (if)`
- L58 `  (if)`
- L67 ` Couple<Integer, Integer>(search)`

#### `org/starloco/locos/job/maging/Rune.java` — 76 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Rune
Fonctions :
- L12 `static Rune(getRuneById)`
- L19 `static Rune(getRuneByCharacteristic)`
- L26 `static Rune(getRuneByCharacteristicAndByWeight)`
- L30 `  (for)`
- L31 `  (if)`
- L42 ` public(Rune)`
- L50 ` short(getId)`
- L54 ` void(setCharacteristic)`
- L60 ` short(getCharacteristic)`
- L64 ` float(getWeight)`
- L68 ` byte(getBonus)`
- L72 ` byte[](getChance)`

#### `org/starloco/locos/kernel/Config.java` — 287 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Config, enum Params
Fonctions :
- L29 `static void(verify)`
- L34 `static void(load)`
- L118 `static void(create)`
- L134 `static void(write)`
- L136 `  (if)`
- L214 `static void(waiting)`
- L223 `static boolean(isVersionGreaterThan)`
- L278 `  (Params)`
- L282 ` String(toString)`

#### `org/starloco/locos/kernel/Constant.java` — 3956 lignes
Rôle : Constantes StarLoco : stats, items positions, classes, sorts level-up, métiers.
Classe(s) : class Constant
Fonctions :
- L407 `static int(getQuestByMobSkin)`
- L415 `static int(getSkinByHuntMob)`
- L422 `static int(getItemByHuntMob)`
- L429 `static int(getItemByMobSkin)`
- L437 `static String(getDocNameByBornePos)`
- L445 `static short(getClassStatueMap)`
- L448 `  (switch)`
- L478 `static int(getClassStatueCell)`
- L481 `  (switch)`
- L510 `static short(getStartMap)`
- L512 `  (switch)`
- L540 `static int(getStartCell)`
- L543 `  (switch)`
- L583 `static HashMap<Integer, Integer>(getStartSortsPlaces)`
- L586 `  (switch)`
- L650 `static HashMap<Integer, SortStats>(getStartSorts)`
- L653 `  (switch)`
- L717 `static int(getReqPtsToBoostStatsByClass)`
- L720 `  (switch)`
- L726 `  (switch)`
- L844 `  (switch)`
- L970 `  (switch)`
- L1096 `  (switch)`
- L1222 `static void(onLevelUpSpells)`
- L1224 `  (switch)`
- L1694 `static int(getGlyphColor)`
- L1696 `  (switch)`
- L1722 `static int(getTrapsColor)`
- L1724 `  (switch)`
- L1746 `static Stats(getMountStats)`
- L1749 `  (switch)`
- L2152 `static ObjectTemplate(getParchoTemplateByMountColor)`
- L2154 `  (switch)`
- L2373 `static int(getMountColorByParchoTemplate)`
- L2375 `  (if)`
- L2378 `  (while)`
- L2386 `  (if)`
- L2387 `  (if)`
- L2394 `static boolean(isValidPlaceForItem)`
- L2398 `  (switch)`
- L2513 `static void(tpCim)`
- L2517 `  (if)`
- L2522 `  (switch)`
- L2624 `static boolean(isTaverne)`
- L2626 `  (switch)`
- L2650 `static int(getLevelForChevalier)`
- L2667 `static String(getStatsOfCandy)`
- L2673 `static String(getStatsOfMascotte)`
- L2679 `static String(getStringColorDragodinde)`
- L2682 `  (switch)`
- L2825 `static int(getGeneration)`
- L2827 `  (switch)`
- L2907 `static int(colorToEtable)`
- L2916 `  (for)`
- L2921 `  (switch)`
- L2942 `  (for)`
- L2947 `  (switch)`
- L2970 `  (if)`
- L3106 `  (if)`
- L3110 `  (for)`
- L3115 `  (switch)`
- L3131 `  (for)`
- L3136 `  (switch)`
- L3162 `static int(getParchoByIdPets)`
- L3164 `  (switch)`
- L3266 `static int(getPetsByIdParcho)`
- L3268 `  (switch)`
- L3370 `static int(getDoplonDopeul)`
- L3372 `  (switch)`
- L3400 `static int(getIDdoplonByMapID)`
- L3402 `  (switch)`
- L3430 `static int(getArmeSoin)`
- L3432 `  (switch)`
- L3453 `static int(getSectionByDopeuls)`
- L3455 `  (switch)`
- L3483 `static int(getCertificatByDopeuls)`
- L3485 `  (switch)`
- L3513 `static boolean(isCertificatDopeuls)`
- L3515 `  (switch)`
- L3532 `static int(getItemIdByMascotteId)`
- L3534 `  (switch)`
- L3602 `static boolean(isIncarnationWeapon)`
- L3604 `  (switch)`
- L3618 `static boolean(isTourmenteurWeapon)`
- L3620 `  (switch)`
- L3630 `static boolean(isBanditsWeapon)`
- L3632 `  (switch)`
- L3641 `static int(getSpecialSpellByClasse)`
- L3643 `  (switch)`
- L3671 `static boolean(isFlacGelee)`
- L3673 `  (switch)`
- L3682 `static boolean(isDoplon)`
- L3684 `  (switch)`
- L3701 `static boolean(isInMorphDonjon)`
- L3703 `  (switch)`
- L3721 `static int[](getOppositeStats)`
- L3735 `static int(getNearestCellIdUnused)`
- L3742 `  (for)`
- L3751 `static float(getWeaponBonusByClass)`
- L3753 `  (switch)`
- L3755 `  (switch)`
- L3763 `  (switch)`
- L3771 `  (switch)`
- L3779 `  (switch)`
- L3787 `  (switch)`
- L3795 `  (switch)`
- L3803 `  (switch)`
- L3811 `  (switch)`
- L3819 `  (switch)`
- L3827 `  (switch)`
- L3836 `  (switch)`
- L3848 `static String(getClassNameById)`
- L3850 `  (switch)`
- L3879 `static int[](getPositionByItemType)`
- L3881 `  (switch)`
- L3917 `static boolean(isTypeWeapon)`
- L3919 `  (switch)`
- L3937 `static boolean(isTypeForMimibiote)`
- L3942 `static byte(getColorByElement)`
- L3944 `  (switch)`

#### `org/starloco/locos/kernel/Logging.java` — 96 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Logging
Fonctions :
- L14 `static Logging(getInstance)`
- L18 ` void(initialize)`
- L22 ` void(stop)`
- L33 ` void(write)`
- L36 `  (for)`
- L38 `  (if)`
- L63 ` public(Log)`
- L74 ` void(write)`
- L87 ` String(getName)`
- L91 ` BufferedWriter(getBuffer)`

#### `org/starloco/locos/kernel/Main.java` — 170 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Main
Fonctions :
- L38 `static void(main)`
- L41 `  (if)`
- L65 `static void(start)`
- L75 `  (if)`
- L84 `  (if)`
- L94 `  (while)`
- L97 `  (for)`
- L108 `  (if)`
- L111 `  (while)`
- L137 `static void(stop)`
- L144 `  (for)`
- L146 `  (for)`
- L158 `static void(checkStop)`
- L165 `static void(clear)`

#### `org/starloco/locos/kernel/Reboot.java` — 52 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Reboot
Fonctions :
- L10 ` void(initialize)`
- L14 `static boolean(check)`
- L29 `  (switch)`
- L41 `static String(toStr)`
- L44 `  (if)`

#### `org/starloco/locos/lang/LangEnum.java` — 49 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : enum LangEnum
Fonctions :
- L19 `  (LangEnum)`
- L24 ` String(getFlag)`
- L28 ` String(trans)`
- L30 `  (if)`
- L38 `  (for)`
- L44 `static Map<String, Object>(loadYAML)`

#### `org/starloco/locos/object/GameObject.java` — 1206 lignes
Rôle : Instance d’objet : template, quantité, stats, texte stats, position, sérialisation packet/DB.
Classe(s) : class GameObject
Fonctions :
- L38 ` public(GameObject)`
- L50 ` public(GameObject)`
- L59 ` public(GameObject)`
- L74 ` GameObject(getClone)`
- L89 ` void(setId)`
- L93 ` int(getPuit)`
- L97 ` void(setPuit)`
- L101 ` int(getObvijevanPos)`
- L105 ` void(setObvijevanPos)`
- L109 ` int(getObvijevanLook)`
- L113 ` void(setObvijevanLook)`
- L117 ` void(parseStringToStats)`
- L122 `  (if)`
- L123 `  (for)`
- L131 `  (if)`
- L135 `  (if)`
- L142 `  (if)`
- L149 `  (if)`
- L153 `  (if)`
- L157 `  (if)`
- L161 `  (if)`
- L165 `  (if)`
- L169 `  (if)`
- L174 `  (if)`
- L178 `  (if)`
- L182 `  (if)`
- L188 `  (if)`
- L196 `  (if)`
- L200 `  (if)`
- L206 `  (switch)`
- L219 `  (if)`
- L224 `  (for)`
- L225 `  (if)`
- L240 ` void(addTxtStat)`
- L244 ` String(getTraquedName)`
- L246 `  (for)`
- L253 ` Stats(getStats)`
- L257 ` void(setStats)`
- L261 ` ArrayList<String>(getSpellStats)`
- L265 ` int(getQuantity)`
- L269 ` void(setQuantity)`
- L279 ` int(getPosition)`
- L283 ` void(setPosition)`
- L287 ` ObjectTemplate(getTemplate)`
- L291 ` void(setTemplate)`
- L295 ` int(getGuid)`
- L299 ` Map<Integer, Integer>(getSoulStat)`
- L303 ` Map<Integer, String>(getTxtStat)`
- L307 ` Mount(setMountStats)`
- L319 ` void(attachToPlayer)`
- L324 ` boolean(isAttach)`
- L327 `  (if)`
- L335 ` String(encodeItem)`
- L343 ` String(encodeStats)`
- L352 `  (if)`
- L353 `  (for)`
- L354 `  (if)`
- L361 `  (for)`
- L368 `  (switch)`
- L382 `  (for)`
- L386 `  (if)`
- L393 `  (if)`
- L397 `  (if)`
- L408 `  (if)`
- L424 `  (for)`
- L430 `  (if)`
- L443 `  (if)`
- L475 `  (for)`
- L486 `  (for)`
- L496 `  (if)`
- L509 `  (if)`
- L512 `  (if)`
- L536 ` String(parseStatsStringSansUserObvi)`
- L543 `  (if)`
- L546 `  (for)`
- L552 `  (for)`
- L555 `  (if)`
- L560 `  (if)`
- L585 `  (if)`
- L612 `  (if)`
- L613 `  (for)`
- L614 `  (if)`
- L621 `  (for)`
- L634 `  (for)`
- L640 `  (for)`
- L643 `  (if)`
- L655 ` String(parseToSave)`
- L659 ` String(obvijevanOCO_Packet)`
- L672 ` void(obvijevanNourir)`
- L676 `  (for)`
- L685 ` void(obvijevanChangeStat)`
- L687 `  (for)`
- L693 ` void(removeAllObvijevanStats)`
- L698 `  (for)`
- L707 ` void(removeAll_ExepteObvijevanStats)`
- L711 `  (for)`
- L720 ` String(getObvijevanStatsOnly)`
- L728 ` Stats(generateNewStatsFromTemplate)`
- L737 `  (for)`
- L754 `  (if)`
- L774 ` ArrayList<SpellEffect>(getEffects)`
- L778 ` ArrayList<SpellEffect>(getCritEffects)`
- L781 `  (for)`
- L788 `  (if)`
- L807 ` void(clearStats)`
- L816 ` void(refreshStatsObjet)`
- L821 ` int(getResistance)`
- L826 `  (for)`
- L828 `  (if)`
- L834 ` int(getResistanceMax)`
- L839 `  (for)`
- L841 `  (if)`
- L847 ` int(getRandomValue)`
- L854 `  (for)`
- L872 ` String(parseStringStatsEC_FM)`
- L876 `  (for)`
- L894 `  (if)`
- L898 `  (if)`
- L912 `  (for)`
- L916 `  (if)`
- L973 `  (for)`
- L982 ` String(parseFMStatsString)`
- L988 `  (for)`
- L1002 `  (for)`
- L1008 `  (if)`
- L1026 `  (for)`
- L1037 ` boolean(isOverFm)`
- L1045 `  (for)`
- L1081 ` boolean(isSameStats)`
- L1084 `  (for)`
- L1085 `  (for)`
- L1091 `  (if)`
- L1097 `  (for)`
- L1098 `  (for)`
- L1099 `  (if)`
- L1104 `  (if)`
- L1110 `  (for)`
- L1111 `  (for)`
- L1112 `  (if)`
- L1117 `  (if)`
- L1123 `  (for)`
- L1124 `  (for)`
- L1125 `  (if)`
- L1130 `  (if)`
- L1137 ` boolean(isOverFm2)`
- L1146 `  (for)`
- L1174 `  (catch)`
- L1180 `  (catch)`
- L1191 ` int(getAppearanceTemplateId)`
- L1193 `  (if)`
- L1198 ` boolean(isMimibiote)`
- L1202 ` SItem(scripted)`

#### `org/starloco/locos/object/ItemHash.java` — 100 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ItemHash
Fonctions :
- L40 ` public(ItemHash)`
- L46 `static String(hash)`
- L62 `  (try)`
- L63 `  (try)`
- L65 `  (for)`
- L70 `  (for)`
- L82 `  (synchronized)`
- L84 `  (for)`
- L90 ` int(hashCode)`
- L94 ` boolean(equals)`

#### `org/starloco/locos/object/ObjectAction.java` — 1051 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ObjectAction
Fonctions :
- L45 ` public(ObjectAction)`
- L51 ` void(apply)`
- L54 `  (if)`
- L61 `  (if)`
- L63 `  (if)`
- L68 `  (if)`
- L83 `  (for)`
- L87 `  (switch)`
- L99 `  (if)`
- L132 `  (for)`
- L134 `  (if)`
- L141 `  (switch)`
- L143 `  (if)`
- L157 `  (if)`
- L174 `  (if)`
- L199 `  (for)`
- L202 `  (switch)`
- L267 `  (if)`
- L279 `  (if)`
- L285 `  (if)`
- L289 `  (if)`
- L298 `  (if)`
- L304 `  (if)`
- L307 `  (if)`
- L309 `  (if)`
- L311 `  (switch)`
- L328 `  (if)`
- L346 `  (if)`
- L352 `  (if)`
- L401 `  (if)`
- L420 `  (if)`
- L467 `  (if)`
- L471 `  (if)`
- L486 `  (for)`
- L487 `  (if)`
- L493 `  (if)`
- L536 `  (if)`
- L540 `  (if)`
- L544 `  (if)`
- L548 `  (if)`
- L565 `  (if)`
- L571 `  (if)`
- L576 `  (if)`
- L587 `  (if)`
- L592 `  (if)`
- L600 `  (for)`
- L604 `  (if)`
- L614 `  (if)`
- L631 `  (for)`
- L638 `  (if)`
- L649 `  (if)`
- L666 `  (if)`
- L678 `  (for)`
- L720 `  (if)`
- L737 `  (if)`
- L750 `  (if)`
- L753 `  (if)`
- L754 `  (switch)`
- L761 `  (if)`
- L762 `  (switch)`
- L782 `  (if)`
- L785 `  (if)`
- L802 `  (for)`
- L804 `  (switch)`
- L827 `  (for)`
- L829 `  (if)`
- L847 `  (switch)`
- L916 `  (if)`
- L920 `  (if)`
- L926 ` boolean(haveEffect)`
- L929 `  (switch)`
- L941 `  (if)`

#### `org/starloco/locos/object/ObjectSet.java` — 62 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ObjectSet
Fonctions :
- L13 ` public(ObjectSet)`
- L16 `  (for)`
- L29 `  (for)`
- L32 `  (for)`
- L33 `  (if)`
- L48 ` int(getId)`
- L52 ` Stats(getBonusStatByItemNumb)`
- L58 ` ArrayList<ObjectTemplate>(getItemTemplates)`

#### `org/starloco/locos/object/ObjectTemplate.java` — 661 lignes
Rôle : Template item : génération de jets, stats min/max, actions, type, prix, conditions.
Classe(s) : class ObjectTemplate
Fonctions :
- L36 ` String(toString)`
- L40 ` public(ObjectTemplate)`
- L77 ` void(setInfos)`
- L110 ` int(getId)`
- L114 ` void(setId)`
- L118 ` String(getStrTemplate)`
- L122 ` String(getName)`
- L126 ` void(setName)`
- L130 ` int(getType)`
- L134 ` void(setType)`
- L138 ` int(getLevel)`
- L142 ` void(setLevel)`
- L146 ` int(getPod)`
- L150 ` void(setPod)`
- L154 ` int(getPrice)`
- L158 ` void(setPrice)`
- L162 ` int(getPanoId)`
- L166 ` String(getConditions)`
- L170 ` void(setConditions)`
- L174 ` int(getPACost)`
- L178 ` void(setPACost)`
- L182 ` int(getPOmin)`
- L186 ` void(setPOmin)`
- L190 ` int(getPOmax)`
- L194 ` void(setPOmax)`
- L198 ` int(getTauxCC)`
- L202 ` void(setTauxCC)`
- L206 ` int(getTauxEC)`
- L210 ` void(setTauxEC)`
- L214 ` int(getBonusCC)`
- L218 ` void(setBonusCC)`
- L222 ` boolean(isTwoHanded)`
- L226 ` void(setTwoHanded)`
- L230 ` int(getAvgPrice)`
- L234 ` long(getSold)`
- L238 ` int(getPoints)`
- L242 ` void(addAction)`
- L248 ` ArrayList<ObjectAction>(getOnUseActions)`
- L252 ` GameObject(createNewCertificat)`
- L255 `  (if)`
- L277 ` GameObject(createNewFamilier)`
- L292 ` GameObject(createNewBenediction)`
- L301 ` GameObject(createNewMalediction)`
- L310 ` GameObject(createNewRoleplayBuff)`
- L319 ` GameObject(createNewCandy)`
- L328 ` GameObject(createNewFollowPnj)`
- L338 ` GameObject(createNewItem)`
- L360 `  (if)`
- L369 `  (switch)`
- L380 `  (for)`
- L395 ` GameObject(createNewItemWithoutDuplication)`
- L416 `  (if)`
- L425 `  (switch)`
- L435 `  (for)`
- L438 `  (if)`
- L449 `  (for)`
- L451 `  (if)`
- L459 ` Map<Integer, String>(getStringResistance)`
- L463 `  (for)`
- L472 ` ArrayList<String>(getSpellStatsTemplate)`
- L475 `  (if)`
- L477 `  (for)`
- L480 `  (if)`
- L488 ` Stats(generateNewStatsFromTemplate)`
- L497 `  (for)`
- L513 `  (switch)`
- L531 `  (if)`
- L551 ` ArrayList<SpellEffect>(getEffectTemplate)`
- L558 `  (for)`
- L561 `  (for)`
- L562 `  (if)`
- L570 `  (switch)`
- L585 ` void(applyAction)`
- L589 `  (if)`
- L605 `synchronized void(newSold)`
- L612 ` boolean(isAnEquipment)`
- L618 `  (switch)`
- L650 ` boolean(isFilledSoulStone)`
- L652 `  (switch)`

#### `org/starloco/locos/object/ShopObject.java` — 41 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class ShopObject
Fonctions :
- L17 ` public(ShopObject)`
- L24 ` ObjectTemplate(getTemplate)`
- L28 ` short(getPrice)`
- L32 ` short(getId)`
- L36 ` boolean(isJp)`

#### `org/starloco/locos/object/entity/Fragment.java` — 55 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Fragment extends GameObject
Fonctions :
- L13 ` public(Fragment)`
- L19 ` public(Fragment)`
- L26 ` void(parseRunes)`
- L28 `  (if)`
- L29 `  (for)`
- L35 ` ArrayList<Couple<Integer, Integer>>(getRunes)`
- L39 ` void(addRune)`
- L48 ` Couple<Integer, Integer>(search)`

#### `org/starloco/locos/object/entity/SoulStone.java` — 98 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class SoulStone extends GameObject
Fonctions :
- L17 ` public(SoulStone)`
- L24 ` public(SoulStone)`
- L31 ` List<Pair<Integer, Integer>>(getMonsters)`
- L35 ` Stream<Integer>(getMonsterIDs)`
- L39 ` void(stringToStats)`
- L41 `  (if)`
- L46 `  (for)`
- L58 `static Optional<SoulStone>(safeCast)`
- L63 ` g(encodeStats)`
- L68 ` String(parseGroupData)`
- L72 `  (for)`
- L80 ` g(parseToSave)`
- L85 `  (for)`
- L93 `static boolean(isInArenaMap)`

#### `org/starloco/locos/other/Action.java` — 3734 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Action
Fonctions :
- L37 ` public(Action)`
- L43 `static java.util.Map<Integer, Couple<Integer, Integer>>(getDopeul)`
- L60 `static Couple<Integer, Integer>(Couple)`
- L64 ` int(getId)`
- L68 ` void(setId)`
- L72 ` String(getArgs)`
- L76 ` void(setArgs)`
- L80 ` void(setCond)`
- L84 ` boolean(apply)`
- L90 `  (if)`
- L94 `  (if)`
- L100 `  (switch)`
- L102 `  (if)`
- L105 `  (if)`
- L124 `  (for)`
- L162 `  (if)`
- L169 `  (if)`
- L173 `  (if)`
- L218 `  (switch)`
- L229 `  (if)`
- L242 `  (if)`
- L246 `  (if)`
- L265 `  (if)`
- L268 `  (if)`
- L297 `  (if)`
- L302 `  (if)`
- L324 `  (if)`
- L339 `  (if)`
- L340 `  (if)`
- L364 `  (if)`
- L377 `  (if)`
- L421 `  (if)`
- L438 `  (if)`
- L449 `  (if)`
- L515 `  (switch)`
- L551 `  (if)`
- L604 `  (if)`
- L606 `  (if)`
- L657 `  (if)`
- L671 `  (if)`
- L675 `  (if)`
- L679 `  (if)`
- L708 `  (if)`
- L712 `  (if)`
- L716 `  (if)`
- L742 `  (if)`
- L862 `  (for)`
- L868 `  (if)`
- L904 `  (if)`
- L962 `  (if)`
- L1101 `  (if)`
- L1115 `  (if)`
- L1120 `  (if)`
- L1123 `  (for)`
- L1133 `  (if)`
- L1168 `  (if)`
- L1178 `  (for)`
- L1186 `  (if)`
- L1207 `  (if)`
- L1236 `  (if)`
- L1239 `  (if)`
- L1248 `  (if)`
- L1249 `  (if)`
- L1259 `  (if)`
- L1286 `  (if)`
- L1293 `  (if)`
- L1346 `  (switch)`
- L1348 `  (if)`
- L1355 `  (if)`
- L1369 `  (if)`
- L1371 `  (if)`
- L1476 `  (if)`
- L1494 `  (if)`
- L1509 `  (if)`
- L1511 `  (if)`
- L1520 `  (if)`
- L1529 `  (if)`
- L1550 `  (if)`
- L1559 `  (if)`
- L1564 `  (if)`
- L1588 `  (if)`
- L1599 `  (if)`
- L1630 `  (if)`
- L1680 `  (if)`
- L1691 `  (if)`
- L1733 `  (if)`
- L1742 `  (if)`
- L1779 `  (if)`
- L1786 `  (if)`
- L1808 `  (if)`
- L1815 `  (if)`
- L1820 `  (if)`
- L1837 `  (if)`
- L1900 `  (if)`
- L1903 `  (if)`
- L1925 `  (if)`
- L1929 `  (for)`
- L1935 `  (if)`
- L1964 `  (if)`
- L1969 `  (if)`
- L1984 `  (if)`
- L1985 `  (if)`
- L2006 `  (if)`
- L2008 `  (if)`
- L2018 `  (if)`
- L2019 `  (if)`
- L2028 `  (if)`
- L2047 `  (if)`
- L2070 `  (if)`
- L2072 `  (if)`
- L2076 `  (if)`
- L2110 `  (if)`
- L2121 `  (if)`
- L2135 `  (if)`
- L2144 `  (if)`
- L2156 `  (if)`
- L2190 `  (if)`
- L2203 `  (if)`
- L2227 `  (if)`
- L2235 `  (if)`
- L2253 `  (if)`
- L2265 `  (if)`
- L2269 `  (if)`
- L2286 `  (if)`
- L2299 `  (if)`
- L2308 `  (if)`
- L2320 `  (if)`
- L2327 `  (if)`
- L2338 `  (if)`
- L2345 `  (if)`
- L2347 `  (if)`
- L2354 `  (if)`
- L2356 `  (if)`
- L2363 `  (if)`
- L2365 `  (if)`
- L2372 `  (if)`
- L2374 `  (if)`
- L2386 `  (if)`
- L2387 `  (if)`
- L2397 `  (if)`
- L2399 `  (if)`
- L2414 `  (if)`
- L2417 `  (if)`
- L2418 `  (if)`
- L2433 `  (switch)`
- L2435 `  (if)`
- L2458 `  (if)`
- L2481 `  (if)`
- L2504 `  (if)`
- L2527 `  (if)`
- L2553 `  (if)`
- L2583 `  (if)`
- L2586 `  (if)`
- L2598 `  (if)`
- L2605 `  (if)`
- L2614 `  (if)`
- L2618 `  (if)`
- L2630 `  (if)`
- L2633 `  (if)`
- L2643 `  (if)`
- L2646 `  (if)`
- L2656 `  (if)`
- L2670 `  (if)`
- L2673 `  (if)`
- L2683 `  (if)`
- L2686 `  (if)`
- L2702 `  (if)`
- L2706 `  (if)`
- L2752 `  (if)`
- L2765 `  (if)`
- L2860 `  (if)`
- L2865 `  (if)`
- L2871 `  (if)`
- L2887 `  (if)`
- L2968 `  (if)`
- L2970 `  (for)`
- L2973 `  (if)`
- L2996 `  (if)`
- L3032 `  (if)`
- L3081 `  (switch)`
- L3117 `  (switch)`
- L3161 `  (if)`
- L3167 `  (for)`
- L3168 `  (if)`
- L3172 `  (if)`
- L3181 `  (if)`
- L3182 `  (if)`
- L3191 `  (if)`
- L3202 `  (if)`
- L3290 `  (if)`
- L3301 `  (if)`
- L3303 `  (for)`
- L3305 `  (if)`
- L3320 `  (if)`
- L3348 `  (if)`
- L3355 `  (if)`
- L3361 `  (if)`
- L3365 `  (if)`
- L3394 `  (if)`
- L3401 `  (if)`
- L3412 `  (if)`
- L3414 `  (if)`
- L3442 `  (if)`
- L3449 `  (if)`
- L3452 `  (if)`
- L3454 `  (if)`
- L3475 `  (if)`
- L3476 `  (if)`
- L3495 `  (if)`
- L3496 `  (if)`
- L3501 `  (if)`
- L3510 `  (if)`
- L3514 `  (for)`
- L3516 `  (if)`
- L3539 `  (if)`
- L3544 `  (if)`
- L3566 `  (if)`
- L3572 `  (if)`
- L3573 `  (if)`
- L3574 `  (if)`
- L3579 `  (if)`
- L3583 `  (if)`
- L3603 `  (if)`
- L3604 `  (if)`
- L3631 `  (if)`
- L3635 `  (if)`
- L3658 `  (if)`
- L3672 `  (if)`
- L3689 `  (if)`
- L3717 `  (for)`
- L3720 `  (if)`

#### `org/starloco/locos/proto/AccountQueuePositionMessage.java` — 44 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class AccountQueuePositionMessage extends AbstractDofusMessage
Fonctions :
- L14 ` public(AccountQueuePositionMessage)`
- L21 ` public(AccountQueuePositionMessage)`
- L23 ` d(serialize)`
- L28 ` d(deserialize)`
- L33 ` g(toString)`

#### `org/starloco/locos/quest/QuestInfo.java` — 23 lignes
Rôle : Quêtes : progression, objectifs, état joueur.
Classe(s) : class QuestInfo
Fonctions :
- L13 ` public(QuestInfo)`

#### `org/starloco/locos/quest/QuestProgress.java` — 67 lignes
Rôle : Quêtes : progression, objectifs, état joueur.
Classe(s) : class QuestProgress
Fonctions :
- L15 ` public(QuestProgress)`
- L23 ` public(QuestProgress)`
- L32 ` boolean(isFinished)`
- L36 ` int(getCurrentStep)`
- L40 ` void(setCurrentStep)`
- L45 ` boolean(hasCompletedObjective)`
- L49 ` void(completeObjective)`
- L53 ` Set<Integer>(getCompletedObjectives)`
- L57 ` void(markFinished)`
- L62 ` int(getQuestId)`

#### `org/starloco/locos/script/DataScriptVM.java` — 238 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class DataScriptVM extends ScriptVM
Fonctions :
- L34 ` private(DataScriptVM)`
- L38 ` d(loadData)`
- L45 ` void(safeLoadData)`
- L53 ` void(customizeEnv)`
- L65 `synchronized void(init)`
- L75 `static DataScriptVM(getInstance)`
- L81 ` d(invoke)`
- L86 ` d(resume)`
- L94 ` d(invoke)`
- L103 ` d(resume)`
- L110 ` g(name)`
- L114 ` d(invoke)`
- L127 ` g(name)`
- L131 ` d(invoke)`
- L138 `  (if)`
- L152 ` g(name)`
- L156 ` d(invoke)`
- L177 ` g(name)`
- L181 ` d(invoke)`
- L191 `  (if)`
- L203 ` g(name)`
- L207 ` d(invoke)`
- L221 ` g(name)`
- L225 ` d(invoke)`

#### `org/starloco/locos/script/EventHandlers.java` — 61 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class EventHandlers extends DefaultTable
Fonctions :
- L13 ` public(EventHandlers)`
- L18 ` LuaFunction<?,?,?,?,?>(getHandler)`
- L25 ` void(onDialog)`
- L29 ` void(onMapEnter)`
- L33 ` void(onSkillUse)`
- L37 ` void(onFightEnd)`
- L41 ` QuestInfo(questInfo)`
- L56 ` void(onDocQuestHref)`

#### `org/starloco/locos/script/ScriptMapper.java` — 7 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : interface ScriptMapper <T>
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/script/ScriptVM.java` — 390 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class ScriptVM, class LoadPack extends AbstractLibFunction
Fonctions :
- L47 ` protected(ScriptVM)`
- L65 ` void(loadData)`
- L69 ` void(runArchive)`
- L74 `  (try)`
- L85 ` void(runPathStream)`
- L98 ` void(runDirectoryOrArchive)`
- L101 `  (if)`
- L106 `  (try)`
- L113 ` void(runFile)`
- L121 ` Object[](call)`
- L123 `  (synchronized)`
- L131 `static AbstractLibFunction(printOverwrite)`
- L133 ` return new(AbstractLibFunction)`
- L134 ` g(name)`
- L138 ` d(invoke)`
- L153 ` Object[](runCustomized)`
- L155 `  (synchronized)`
- L177 ` Object[](runAdminCommand)`
- L185 `static Object(recursiveGet)`
- L199 ` g(name)`
- L203 ` d(invoke)`
- L212 ` g(name)`
- L216 ` d(invoke)`
- L226 `static int(rawOptionalInt)`
- L232 `static Optional<Object>(rawOptional)`
- L236 `static String(rawOptionalString)`
- L240 `static int(rawInt)`
- L244 `static Integer(rawInteger)`
- L250 `static int(rawInt)`
- L254 ` List<T>(listFromLuaTable)`
- L266 ` Pair<K, V>(toPair)`
- L271 `static List<Pair<Integer, Integer>>(listOfIntPairs)`
- L284 `static List<String>(listOfString)`
- L298 `static List<Integer>(intsFromLuaTable)`
- L311 `static int[](intArrayFromLuaTable)`
- L324 `static long[](longArrayFromLuaTable)`
- L337 `static Table(ItemStack)`
- L344 `static Couple<Integer, Integer>(ItemStackFromLua)`
- L350 `static Map<K, V>(mapFromScript)`
- L362 `static Table(scriptedValsTable)`
- L366 `static Table(scriptedValsTable)`
- L372 `  (while)`
- L378 `static Table(listOf)`
- L383 `  (while)`

#### `org/starloco/locos/script/Scripted.java` — 6 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : interface Scripted <R>
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/script/proxy/SAccount.java` — 32 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class SAccount extends DefaultUserdata<Account>
Fonctions :
- L13 ` public(SAccount)`
- L17 ` t(id)`
- L22 ` e(friends)`

#### `org/starloco/locos/script/proxy/SArea.java` — 20 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class SArea extends DefaultUserdata<Area>
Fonctions :
- L10 ` public(SArea)`
- L14 ` t(id)`

#### `org/starloco/locos/script/proxy/SItem.java` — 86 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class SItem extends DefaultUserdata<GameObject>
Fonctions :
- L16 ` public(SItem)`
- L20 ` t(guid)`
- L25 ` t(id)`
- L30 ` t(type)`
- L35 ` g(dateStatTS)`
- L42 `  (if)`
- L47 ` n(hasTxtStat)`
- L54 `  (if)`
- L59 ` n(consumeTxtStat)`
- L74 `  (if)`

#### `org/starloco/locos/script/proxy/SItemStack.java` — 5 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class SItemStack
Fonctions : aucune fonction détectée automatiquement.

#### `org/starloco/locos/script/proxy/SMap.java` — 135 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class SMap extends DefaultUserdata<GameMap>
Fonctions :
- L24 ` public(SMap)`
- L26 ` t(id)`
- L31 ` e(def)`
- L36 ` a(area)`
- L41 ` a(subArea)`
- L46 ` e(cellPlayers)`
- L53 ` e(mobGroupById)`
- L64 ` e(mobGroups)`
- L75 ` t(spawnGroupDef)`
- L82 ` d(updateNpcExtraForPlayer)`
- L92 ` g(getAnimationState)`
- L98 ` d(setAnimationState)`
- L109 ` d(sendAction)`
- L122 ` d(setCellData)`

#### `org/starloco/locos/script/proxy/SMobGrade.java` — 30 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class SMobGrade extends DefaultUserdata<MonsterGrade>
Fonctions :
- L10 ` public(SMobGrade)`
- L14 ` t(id)`
- L19 ` t(grade)`
- L24 ` t(level)`

#### `org/starloco/locos/script/proxy/SPlayer.java` — 687 lignes
Rôle : Joueur/personnage : stats, inventaire, sorts, XP, level-up, banque, monture/familier, échanges, groupes, paquets perso.
Classe(s) : class SPlayer extends DefaultUserdata<Player>
Fonctions :
- L35 ` public(SPlayer)`
- L41 ` t(id)`
- L45 ` g(name)`
- L50 ` t(level)`
- L55 ` t(breed)`
- L60 ` t(gender)`
- L65 ` t(faction)`
- L70 ` d(openBank)`
- L75 ` t(account)`
- L78 ` d(savePosition)`
- L86 `  (if)`
- L90 ` d(openZaap)`
- L95 ` d(openTrunk)`
- L101 ` n(setExchangeAction)`
- L112 ` n(clearExchangeAction)`
- L118 `  (if)`
- L125 ` d(useCraftSkill)`
- L133 ` t(getCtxVal)`
- L139 ` n(setCtxVal)`
- L150 ` n(addXP)`
- L160 ` t(life)`
- L166 ` t(maxLife)`
- L171 ` d(modLife)`
- L178 ` d(setLifePercent)`
- L185 ` t(energy)`
- L190 ` d(modEnergy)`
- L200 ` n(isGhost)`
- L205 ` n(resurrect)`
- L212 ` d(sendAction)`
- L224 ` d(sendInfoMsg)`
- L233 ` d(startScenario)`
- L242 ` d(openDocument)`
- L253 ` d(ask)`
- L266 ` d(endDialog)`
- L269 `  (if)`
- L276 ` d(pauseDialog)`
- L279 `  (if)`
- L289 ` n(_questAvailable)`
- L294 ` n(_questFinished)`
- L299 ` n(_questOngoing)`
- L304 ` e(_ongoingQuests)`
- L309 ` n(_startQuest)`
- L326 ` t(_currentStep)`
- L337 ` e(_completedObjectives)`
- L346 ` n(_completeObjective)`
- L363 ` n(_setCurrentStep)`
- L378 ` n(_completeQuest)`
- L388 `  (if)`
- L401 ` n(hasEmote)`
- L406 ` n(learnEmote)`
- L415 ` <Integer,Integer>(savedPosition)`
- L419 ` d(setSavedPosition)`
- L427 `  (if)`
- L431 ` t(mapID)`
- L436 ` p(map)`
- L441 ` t(cell)`
- L446 ` t(orientation)`
- L451 ` d(teleport)`
- L458 ` d(compassTo)`
- L470 ` g(kamas)`
- L474 ` n(modKamas)`
- L483 ` m(gearAt)`
- L491 ` <Integer, Integer>(pods)`
- L496 ` m(getItem)`
- L502 `  (if)`
- L508 ` n(consumeItem)`
- L515 ` n(addItem)`
- L536 ` n(tryBuyItem)`
- L551 ` d(showReceivedItem)`
- L562 ` e(jobs)`
- L566 ` n(tryLearnJob)`
- L572 ` n(tryUnlearnJob)`
- L578 ` n(canLearnJob)`
- L585 ` t(jobLevel)`
- L594 ` n(addJobXP)`
- L613 ` t(spellLevel)`
- L620 ` n(setSpellLevel)`
- L629 ` d(spellResetPanel)`
- L638 ` n(setFaction)`
- L655 ` t(baseStat)`
- L660 ` t(modScrollStat)`
- L668 ` d(resetStats)`
- L678 ` d(forceFight)`

#### `org/starloco/locos/script/proxy/SSubArea.java` — 31 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class SSubArea extends DefaultUserdata<SubArea>
Fonctions :
- L10 ` public(SSubArea)`
- L14 ` t(id)`
- L19 ` a(area)`
- L22 ` t(faction)`
- L27 ` n(conquerable)`

#### `org/starloco/locos/script/proxy/SWorld.java` — 101 lignes
Rôle : Registre global : maps, joueurs, comptes, templates, mobs, sorts, guildes, maisons, cache.
Classe(s) : class SWorld extends DefaultUserdata<World>
Fonctions :
- L24 ` public(SWorld)`
- L28 ` e(datetime)`
- L43 ` g(clock)`
- L48 ` t(account)`
- L54 `  (if)`
- L64 ` r(player)`
- L70 `  (if)`
- L80 ` a(subArea)`
- L85 ` p(map)`
- L90 ` d(delayForMs)`

#### `org/starloco/locos/script/types/IntMap.java` — 98 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class IntMap extends Table implements Map<String, Integer>
Fonctions :
- L12 ` t(size)`
- L17 ` n(isEmpty)`
- L22 ` n(containsKey)`
- L27 ` n(containsValue)`
- L32 ` r(get)`
- L37 ` r(put)`
- L42 ` r(remove)`
- L47 ` d(putAll)`
- L52 ` d(clear)`
- L57 ` <String>(keySet)`
- L62 ` <Integer>(values)`
- L67 ` >(entrySet)`
- L72 ` t(rawget)`
- L77 ` d(rawset)`
- L82 ` t(initialKey)`
- L87 ` t(successorKeyOf)`
- L92 ` d(setMode)`

#### `org/starloco/locos/script/types/MetaTables.java` — 88 lignes
Rôle : Système script Lua/proxy : expose des objets Java aux scripts et mappe les événements.
Classe(s) : class MetaTables
Fonctions :
- L34 ` > Table(ReflectIndexTable)`
- L42 `  (for)`
- L56 ` g(name)`
- L58 ` d(invoke)`
- L65 `  (if)`
- L83 `static ImmutableTable(MetaTable)`

#### `org/starloco/locos/util/Pair.java` — 53 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Pair <K, V> implements Serializable
Fonctions :
- L13 ` K(getFirst)`
- L17 ` V(getSecond)`
- L21 ` public(Pair)`
- L26 ` String(toString)`
- L30 ` String(toString)`
- L33 ` int(hashCode)`
- L37 ` boolean(equals)`
- L48 ` Pair<OK,OV>(map)`

#### `org/starloco/locos/util/Predicates.java` — 10 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class Predicates
Fonctions :
- L6 `static Predicate<T>(not)`

#### `org/starloco/locos/util/RandomStats.java` — 27 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class RandomStats <Stats>
Fonctions :
- L10 ` public(RandomStats)`
- L14 ` void(add)`
- L19 ` int(size)`
- L23 ` Stats(get)`

#### `org/starloco/locos/util/TimerWaiter.java` — 51 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class TimerWaiter
Fonctions :
- L14 `static ScheduledFuture<?>(addNext)`
- L18 `static ScheduledFuture<?>(addNext)`
- L22 `static void(update)`
- L28 `static int(getNumberOfThread)`
- L34 `static int(getNumberOfFight)`
- L40 `static Runnable(catchRunnable)`

#### `org/starloco/locos/util/generator/NameGenerator.java` — 277 lignes
Rôle : Classe utilitaire/métier à vérifier selon ses méthodes listées.
Classe(s) : class NameGenerator
Fonctions :
- L64 ` public(NameGenerator)`
- L76 ` void(refresh)`
- L80 `  (while)`
- L83 `  (if)`
- L84 `  (if)`
- L87 ` else(if)`
- L97 ` String(upper)`
- L101 ` boolean(doesNotContainsConsFirst)`
- L108 ` boolean(doesNotContainsVocFirst)`
- L115 ` boolean(disallowCons)`
- L122 ` boolean(disallowVocs)`
- L129 ` boolean(expectsVowel)`
- L133 ` boolean(expectsConsonant)`
- L136 ` boolean(hatesPreviousVowels)`
- L139 ` boolean(hatesPreviousConsonants)`
- L142 ` String(pureSyl)`
- L148 ` boolean(VowelFirst)`
- L152 ` boolean(consonantFirst)`
- L156 ` boolean(VowelLast)`
- L160 ` boolean(consonantLast)`
- L171 ` String(compose)`
- L184 `  (if)`
- L249 `  (if)`
