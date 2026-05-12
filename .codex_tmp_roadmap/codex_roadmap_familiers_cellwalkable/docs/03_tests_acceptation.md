# Tests d'acceptation

## Familiers

### Equipement

- Un item `type_id = 18` peut aller uniquement en position `8`.
- Un seul familier équipé à la fois.
- Le familier apparaît visuellement dans GM/Oa.
- Les bonus du familier sont pris en compte dans les stats.
- Un familier mort/fantôme ne donne pas ses bonus, selon politique choisie.

### Nourriture

- Donner une nourriture non autorisée échoue proprement.
- Donner une nourriture autorisée consomme 1 exemplaire.
- Après 1 repas correct : pas encore de bonus.
- Après 2 repas corrects : pas encore de bonus.
- Après 3 repas corrects : +1 bonus selon la dernière nourriture/règle.
- Le bonus ne dépasse pas le plafond normal.
- Avec hormone activée, le bonus peut atteindre le plafond hormone.
- Un repas trop tôt ne donne pas de bonus.
- Un repas trop tard applique la règle de retard choisie.
- La persistence fonctionne après redémarrage serveur.

## CellWalkable

### Joueur

- Impossible de marcher sur un puits connu.
- Impossible de marcher sur une cellule `movement = Unwalkable`.
- Impossible de marcher sur une cellule bloquée par décor/interactif configuré.
- Les triggers de changement de map restent accessibles quand ils doivent l'être.
- Une cellule interactive non bloquante reste accessible si elle est configurée non bloquante.

### Bots

- Les bots ne peuvent plus marcher sur les puits connus.
- Les bots ne restent pas bloqués en boucle infinie si la route est impossible.
- Les bots peuvent toujours changer de map via trigger.

### Monstres

- Un groupe ne spawn pas sur une ressource.
- Un groupe ne spawn pas sur un puits.
- Un groupe ne spawn pas sur une cellule non marchable.
- Un groupe multi-monstres reste groupé, pas séparé en plusieurs groupes unitaires.
- Si la cellule demandée est mauvaise, le groupe est replacé naturellement.
- Si aucune cellule valide n'existe, warning propre et spawn ignoré.

## Logs attendus

- DEBUG quand une cellule est corrigée automatiquement.
- WARN seulement quand aucun placement valide n'est trouvé.
- INFO au chargement pour compter les cellules/interactifs chargés.
