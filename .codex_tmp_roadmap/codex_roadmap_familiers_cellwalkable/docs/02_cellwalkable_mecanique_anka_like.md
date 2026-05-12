# CellWalkable — mécanique Anka'like Dofus 1.29

## Problème observé

Le personnage peut marcher sur un puits. Certains mobs peuvent aussi encore apparaître sur des ressources ou des éléments interactifs.

La cause probable : le serveur valide encore trop souvent une cellule avec une logique minimale :

```text
cell_id entre 0 et 559
pas occupée
pas trigger selon cas
```

Mais une cellule Dofus peut être techniquement dans la map tout en étant interdite au déplacement ou au spawn parce qu'elle contient un objet de décor/interactif.

## Source logique côté client 1.29

Dans le décodeur de cellule, chaque cellule est encodée sur 10 caractères. Les champs importants sont :

```text
active
lineOfSight
movement
layerGroundNum
layerObject1Num
layerObject2Num
layerObject2Interactive
```

Pour le serveur, il faut au minimum exploiter :

```text
active == true
movement != Unwalkable
layerObject2Interactive == false pour déplacement normal si objet bloquant
layerObject2Num pour reconnaître puits/ressource/atelier/porte/zaap
```

## Règle serveur proposée

Séparer plusieurs niveaux de validation.

### 1. Cellule existante

```java
cell != null
cell.active
```

### 2. Cellule marchable brute

```java
cell.isWalkable()
```

Basé sur `movement`.

### 3. Cellule bloquée par décor/interactif

```java
!cell.hasBlockingInteractiveObject()
```

Il ne faut pas bloquer tous les interactifs de la même manière. Un zaap, un puits, un atelier, une porte, une ressource n'ont pas forcément le même comportement.

### 4. Cellule occupée dynamiquement

```java
pas de joueur
pas de PNJ
pas de groupe monstre
pas de marchand
pas d'objet bloquant futur
```

### 5. Règle selon contexte

Créer des méthodes séparées :

```java
isWalkableForPlayer(cellId)
isWalkableForBot(cellId)
isValidMonsterSpawnCell(cellId)
isValidNpcSpawnCell(cellId)
isValidFightPlacementCell(cellId)
isValidFightMovementCell(cellId)
isValidHarvestCell(cellId)
```

Ne pas utiliser une seule méthode globale pour tout.

## Décodage à ajouter dans MapCellDecoder

Étendre `Cell` avec :

```java
private final int layerObject1Num;
private final int layerObject2Num;
private final boolean layerObject2Interactive;
```

Décodage compatible 1.29 :

```java
int layerObject1Num = ((values[0] & 4) << 11) + (values[5] << 6) + values[6];
boolean layerObject2Interactive = (((values[7] & 2) >> 1) == 1);
int layerObject2Num = ((values[0] & 2) << 12) + ((values[7] & 1) << 12) + (values[8] << 6) + values[9];
```

## Tables SQL conseillées

Deux niveaux :

1. table issue du décodage map ;
2. table de correction manuelle.

Voir `sql/phase17_map_cells_interactives.sql`.

## Règle pour le puits

Un puits doit être considéré comme :

```text
interactive = true
blocking_player_movement = true
blocking_monster_spawn = true
harvest/usable = true si système métier/eau plus tard
```

Tant que le mapping précis `gfx_id -> type interactif` n'est pas complet, toute cellule interactive peut être interdite au spawn monstre, et les interactifs explicitement connus comme puits peuvent être interdits au déplacement joueur.

## Attention

Ne pas bloquer tous les `layerObject2Interactive` pour les joueurs dès le début sans test, sinon certaines maps peuvent devenir injouables si le client marque des éléments interactifs non bloquants. Commencer par une table de types bloquants + override manuel.
