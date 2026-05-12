# Familiers — mécanique Anka'like Dofus 1.29

## Ce qu'on veut reproduire

Un familier est un `Item` équipé sur le slot officiel `8`. Il doit rester un objet d'inventaire normal, mais avec un état vivant persistant.

Mécaniques principales à prévoir :

- type item familier : `type_id = 18` ;
- slot équipement : `position = 8` ;
- points de vie du familier, souvent 10 PV de base ;
- intervalle de repas minimum / maximum selon le familier ;
- nourriture autorisée selon le familier ;
- 3 repas corrects donnent 1 gain de bonus ;
- la nourriture du dernier repas peut déterminer le bonus gagné ;
- repas trop tôt : pas de gain, éventuellement message d'erreur ;
- repas trop tard : perte de PV / état maigrichon selon politique serveur ;
- familier mort : ne donne plus ses bonus, doit être ressuscitable plus tard ;
- hormone : plafond de bonus augmenté pour certains familiers.

## Important pour le code actuel

Le code a déjà une bonne base :

- `ItemTemplate.ItemType.FAMILIER(18)` existe déjà ;
- le slot familier `8` est déjà documenté dans `Inventory` ;
- `Inventory.buildAccessories()` envoie déjà le familier dans les accessoires visuels GM/Oa ;
- `Item` stocke déjà des `rolledEffects` persistables dans `character_items.rolled_effects` ;
- `UseItemService` permet déjà de brancher des actions d'utilisation.

Donc il ne faut pas créer un système à part. Il faut enrichir `Item` avec une donnée optionnelle `PetState`.

## Modèle serveur conseillé

Créer :

```java
org.dofus.objects.items.PetState
org.dofus.objects.items.PetTemplate
org.dofus.objects.items.PetFoodRule
org.dofus.objects.items.PetService
org.dofus.database.objects.PetsData
```

### PetTemplate

Décrit la règle du familier par template d'objet.

Champs utiles :

```text
item_template_id
base_life
min_feed_interval_minutes
max_feed_interval_minutes
meals_per_bonus
max_bonus
hormone_max_bonus
can_die
```

### PetFoodRule

Décrit quelle nourriture donne quel bonus.

```text
pet_template_id
food_template_id
effect_id
stat_gain
food_weight
```

Exemple : Chacha mange plusieurs ressources différentes. Selon la ressource, il peut monter force, intelligence, chance, agilité, vitalité, etc.

### PetState

État vivant de l'instance d'objet.

```text
item_uid
life
state
last_feed_at
last_bonus_food_effect
meal_counter
hormoned
corpulent_counter
skin_override_optionnel
```

`state` conseillé :

```text
0 = normal
1 = maigrichon
2 = obèse
3 = fantôme / mort
```

## Règles de nourriture

Pseudo-code :

```java
boolean feed(Characters owner, Item pet, Item food) {
    if(!pet.isPet()) return false;
    if(pet.getPosition() != 8 && policyRequiresEquipped) return false;
    if(petState.isDead()) return false;

    PetTemplate tpl = PetsData.getTemplate(pet.getTemplate().getId());
    PetFoodRule rule = PetsData.getFoodRule(tpl, food.getTemplate().getId());
    if(rule == null) return false;

    long now = System.currentTimeMillis();
    long elapsed = now - petState.getLastFeedAt();

    if(petState.getLastFeedAt() > 0 && elapsed < tpl.minInterval()) {
        // Trop tôt : consommer ou non selon choix serveur.
        // Anka-like strict : nourriture donnée mais pas de bonus, état peut tendre vers obèse.
        return tooEarly(owner, pet, food);
    }

    if(petState.getLastFeedAt() > 0 && elapsed > tpl.maxInterval()) {
        petState.loseLife(1);
        petState.setState(MAIGRICHON);
    }

    consumeFood(owner, food, 1);
    petState.setLastFeedAt(now);
    petState.setLastBonusFoodEffect(rule.getEffectId());
    petState.incrementMealCounter();

    if(petState.getMealCounter() >= tpl.mealsPerBonus()) {
        petState.resetMealCounter();
        addPetBonus(pet, rule.getEffectId(), rule.getStatGain(), tpl.maxOrHormoneMax(petState));
    }

    savePetState(petState);
    savePetItemEffects(pet);
    refreshInventoryAndStats(owner);
    return true;
}
```

## Bonus dans les effets de l'item

Le plus compatible avec le client : le bonus du familier doit rester dans `Item.rolledEffects`, comme les autres objets.

Exemple : un familier +10 sagesse aura un effet normal dans son entrée `OQ/OL`.

Quand on nourrit :

1. trouver l'effet correspondant dans `rolledEffects` ;
2. augmenter la valeur sans dépasser le plafond ;
3. sauvegarder `character_items.rolled_effects` ;
4. renvoyer inventaire + stats.

## Messages client à prévoir

À affiner selon packets déjà présents, mais prévoir au minimum :

```text
Im11 : action impossible générique
OQ / ObjectQuantity : nourriture consommée
Ow : pods mis à jour
As / stats : statistiques mises à jour
Oa : accessoire familier si changement visuel
```

## Cas particuliers à prévoir plus tard

- Poudre d'Eniripsa : rend 1 PV au familier.
- Résurrection : familier fantôme → familier vivant.
- Certificat de mise en chenil / transformation banque ou PNJ.
- Familier dévoreur d'âmes : progression par kills de monstres plutôt que par nourriture classique.
- Montiliers : à ne pas mélanger tout de suite avec les familiers 1.29.
