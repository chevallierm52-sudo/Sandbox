# Changelog roadmap

## 2026-05-12 — Roadmap Codex Familiers + CellWalkable

### Items / Familiers
- Ajout d'une roadmap Anka-like pour intégrer les familiers comme extension du système `Item` existant.
- Préparation des modèles `PetState`, `PetTemplate`, `PetFoodRule`, `PetService` et `PetsData`.
- Préparation SQL pour `pet_templates`, `pet_food_rules` et `pet_states`.
- Définition des règles de nourriture : intervalles, 3 repas pour 1 bonus, PV, état mort/maigrichon/obèse, hormone.

### Maps / CellWalkable
- Ajout d'une roadmap pour durcir la validation des cellules marchables.
- Préparation du décodage `layerObject1Num`, `layerObject2Num` et `layerObject2Interactive`.
- Préparation d'une séparation propre des validations : joueur, bot, monstre, PNJ, combat.
- Préparation SQL pour overrides manuels et règles par objet interactif.
- Cas prioritaire identifié : empêcher la marche sur les puits et le spawn sur ressources/interactifs.

### Codex
- Ajout de tâches découpées en phases 16 et 17 pour implémentation progressive.
- Ajout d'une checklist de tests d'acceptation.
