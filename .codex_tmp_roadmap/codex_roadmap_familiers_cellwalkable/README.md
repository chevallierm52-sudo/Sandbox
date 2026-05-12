# Roadmap Codex — Familiers + CellWalkable

Objectif : préparer le travail Codex sur deux chantiers Anka'like pour le sandbox Dofus 1.29.1 :

1. **Familiers dans le système Item** : équipement, stats, nourriture, points de vie, état maigrichon/normal/obèse, dernier repas, compteur de repas, hormones, persistence.
2. **CellWalkable officiel-like** : empêcher les déplacements/spawns sur puits, ressources, ateliers, portes, décors bloquants et cellules non marchables.

Cette roadmap est volontairement découpée en phases courtes pour pouvoir patcher/tester sans casser ce qui marche déjà.

## Ordre recommandé

1. Lire `docs/01_familiers_mecanique_anka_like.md`.
2. Appliquer la structure SQL proposée dans `sql/phase16_pets.sql`.
3. Implémenter `codex_tasks/PHASE_16_FAMILIERS_ITEMS.md`.
4. Lire `docs/02_cellwalkable_mecanique_anka_like.md`.
5. Appliquer `sql/phase17_map_cells_interactives.sql`.
6. Implémenter `codex_tasks/PHASE_17_CELLWALKABLE.md`.
7. Valider avec `docs/03_tests_acceptation.md`.

## Principe global

Ne pas remplacer brutalement le système existant. Ajouter des couches optionnelles : si les nouvelles tables n'existent pas ou sont vides, le serveur doit continuer à fonctionner avec le fallback actuel.
