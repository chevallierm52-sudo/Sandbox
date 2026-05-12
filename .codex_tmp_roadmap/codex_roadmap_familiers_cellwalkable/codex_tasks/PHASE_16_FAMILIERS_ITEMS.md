# PHASE 16 — Familiers dans Item

## But

Implémenter une première version Anka'like des familiers sans casser l'inventaire actuel.

## Fichiers à créer

```text
src/org/dofus/objects/items/PetState.java
src/org/dofus/objects/items/PetTemplate.java
src/org/dofus/objects/items/PetFoodRule.java
src/org/dofus/objects/items/PetService.java
src/org/dofus/database/objects/PetsData.java
sql/phase16_pets.sql
```

## Fichiers à modifier

```text
src/org/dofus/objects/items/Item.java
src/org/dofus/objects/items/Inventory.java
src/org/dofus/objects/items/UseItemService.java
src/org/dofus/database/Initialisation.java
src/org/dofus/database/objects/ItemsData.java
src/org/dofus/objects/characters/Statistic.java
CHANGELOG.md
```

## Étapes

1. Ajouter `PetState` comme état optionnel dans `Item`.
2. Charger les états depuis `pet_states` après le chargement des `character_items`.
3. Ajouter `PetsData.load()` au démarrage.
4. Ajouter `PetService.feed(character, session, pet, food)`.
5. Brancher le nourrissage depuis `UseItemService` ou depuis le parser inventaire si le packet client utilisé est différent.
6. Lors d'un bonus gagné, modifier les `rolledEffects` du familier.
7. Sauvegarder `character_items.rolled_effects` + `pet_states`.
8. Recalculer les stats du personnage.
9. Renvoyer les packets inventaire/stats/pods.
10. Ajouter logs et changelog.

## Politique v1 conseillée

Pour ne pas perdre d'objets pendant les tests :

- nourriture trop tôt : refuser sans consommer ;
- nourriture correcte : consommer ;
- nourriture trop tard : consommer, +0 bonus, perte 1 PV si retard fort ;
- familier à 0 PV : état `DEAD`, bonus ignorés.

Cette politique est légèrement plus prudente que l'officiel, mais plus sûre pour un sandbox en développement.

## Points de vigilance

- Les familiers doivent être non stackables.
- Ne jamais fusionner deux familiers dans `Inventory.addItem`.
- Un familier a des effets propres par UID, pas seulement par template.
- Les effets de familier doivent être ignorés si le familier est mort.
- Le slot 8 est déjà utilisé par le visuel, ne pas le changer.
