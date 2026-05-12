# PHASE 17 — CellWalkable officiel-like

## But

Empêcher le joueur, les bots et les groupes de monstres d'aller sur des cellules bloquées par la map ou par des interactifs comme puits/ressources/ateliers.

## Fichiers à modifier

```text
src/org/dofus/utils/MapCellDecoder.java
src/org/dofus/objects/maps/MapTemplate.java
src/org/dofus/game/actions/RolePlayMovement.java
src/org/dofus/objects/actors/BotBehavior.java
src/org/dofus/database/objects/MapsData.java
src/org/dofus/database/objects/MapSpawnExclusionsData.java
CHANGELOG.md
```

## Fichiers à créer

```text
src/org/dofus/objects/maps/MapInteractiveCell.java
src/org/dofus/database/objects/MapCellsData.java
sql/phase17_map_cells_interactives.sql
```

## Étapes

1. Étendre `MapCellDecoder.Cell` avec `layerObject1Num`, `layerObject2Num`, `layerObject2Interactive`.
2. Ajouter une méthode `hasInteractiveObject()`.
3. Ajouter une méthode `hasBlockingInteractiveObject()` basée sur table SQL et overrides.
4. Charger les overrides de `map_cell_overrides`.
5. Ajouter dans `MapTemplate` :

```java
isWalkableForPlayer(short cellId)
isWalkableForBot(short cellId)
isValidMonsterCell(short cellId)
isValidNpcCell(short cellId)
```

6. Modifier `RolePlayMovement` pour refuser une destination non marchable.
7. Modifier `BotBehavior` pour utiliser `isWalkableForBot`.
8. Modifier placement monstre pour utiliser `isValidMonsterCell` enrichi.
9. Ajouter une commande admin temporaire pour debug cellule :

```text
/cellinfo
```

Elle doit afficher : cellId, active, movement, LOS, layerObject1, layerObject2, interactive, blockedPlayer, blockedMonsterSpawn.

## Patch minimal recommandé

Ne pas bloquer tous les interactifs pour les joueurs au premier patch.

Pour la v1 :

```text
spawn monstre : bloque tous les interactifs
joueur/bot : bloque seulement les interactifs explicitement marqués blocking_player_movement
```

Comme ça, on règle les mobs sur ressources sans rendre certaines maps impraticables.
