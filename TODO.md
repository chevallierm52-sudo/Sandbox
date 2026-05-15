# TODO debug global - Dofus 1.29 Sandbox

Derniere mise a jour : 2026-05-14

Objectif : garder une liste de debug exploitable pour stabiliser toutes les mecaniques du jeu. Chaque item doit idealement etre valide par un test en client 1.29, un log packet, et une comparaison avec les sources officielles client / Ancestra / StarLoco quand le protocole est ambigu.

Legende :
- `[P0]` bloquant : crash, ecran noir, perte de personnage, combat bloque.
- `[P1]` important : mecanique jouable mais incomplete ou incoherente.
- `[P2]` moyen : finition protocole, affichage, confort, edge cases.
- `[P3]` bas : contenu, polish, outils internes.

## Methode de debug a appliquer

- [ ] Capturer le flux client complet : login, choix perso, `GC/GDM/GI`, action, retour serveur.
- [ ] Garder pour chaque bug un scenario minimal reproductible : perso, map, cellule, action, packet recu/envoye.
- [ ] Comparer les packets avec le client 1.29 decompilie avant de deviner un format.
- [ ] Comparer les packets serveurs avec Ancestra/StarLoco pour les formats historiques.
- [ ] Ajouter un log court au point serveur qui decide, pas seulement au parser reseau.
- [ ] Tester apres recompilation complete et redemarrage JVM complet.
- [ ] Ne pas valider une correction combat sans tester : entree, placement, pret, tour joueur, tour monstre, mort, fin, reco.

## P0 - Stabilite client / serveur

### Connexion, choix perso, entree en jeu

- [ ] Verifier que `account.connected` repasse toujours a false apres fermeture client, crash, timeout MINA, retour selection perso.
- [ ] Tester deux connexions successives du meme compte apres crash.
- [ ] Tester mauvais mot de passe, compte banni, compte deja connecte.
- [ ] Tester creation personnage puis entree immediate en map : pas de cellule occupee par soi-meme, pas de perso bloque.
- [ ] Verifier packets de selection : `ALK`, `ASK`, `GCK`, `GDM`, `GDK`, `GI`.
- [ ] Verifier que la date `BD`, les canaux `cC`, les stats `As`, les sorts `SL`, pods `Ow` arrivent dans le bon ordre.

### Chargement carte et sprites

- [ ] Verifier toutes les maps Astrub de base : pas d'ecran noir, mapKey correcte, `cellsData` decode.
- [ ] Verifier que `GM|+` joueur, PNJ, bots, groupes de monstres et marchands respectent le discriminant client.
- [ ] Tester les accessoires visuels : coiffe, cape, familier, arme, bouclier, objivivant.
- [ ] Tester les couleurs `-1` et couleurs hex sur joueur, PNJ, monstres.
- [ ] Verifier suppression acteur : `GM|-id` sur deconnexion, changement map, entree combat.
- [ ] Verifier rechargement `GI` apres retour spectateur et fin combat.

### Persistence

- [ ] Sauvegarder/recharger : map, cell, orientation, vie, energie, kamas, inventaire, sorts, stats, alignement.
- [ ] Tester deco pendant mouvement, deco pendant dialogue, deco pendant banque, deco pendant combat.
- [ ] Tester que les timers regen/combat/bot ne continuent pas sur un perso deconnecte.
- [ ] Verifier que `CharactersData.update()` ne perd pas les champs non touches.

## P0 - Combat PvM

### Entree en combat

- [ ] Cliquer directement sur cellule du groupe monstre : le perso marche jusqu'a la cellule d'arret, puis combat apres 100 ms.
- [ ] Cliquer a cote du groupe : mouvement normal sans combat.
- [ ] Envoyer `GR{groupId}` pendant un deplacement : mise en attente puis entree apres `GKK`.
- [ ] Verifier que le groupe est retire de la map roleplay pour tous les joueurs : `GM|-groupId`.
- [ ] Verifier que le joueur est retire de la map roleplay pour les autres : `GM|-playerId`.
- [ ] Refuser entree si deja en combat, groupe disparu, cellule invalide, map differente.

### Placement

- [ ] Verifier `GJK2|...|30000|4`, `GP`, `GM|+fighter`, `GIC`.
- [ ] Verifier affichage des joueurs et monstres pendant placement, pas seulement cercles.
- [ ] Verifier choix cellule `Gp{cell}` uniquement sur cellules autorisees.
- [ ] Refuser cellule occupee, non autorisee, hors map.
- [ ] Verifier bouton Pret `GR1` et annulation `GR0`.
- [ ] Verifier lancement auto apres timer placement.

### Demarrage combat et tours

- [ ] Verifier `GS`, `GTL`, `GTM`, `GTS`.
- [ ] Verifier initiative : ordre joueur/monstres coherent avec stats.
- [ ] Verifier reset PA/PM a chaque debut de tour.
- [ ] Verifier fin de tour volontaire `Gt`.
- [ ] Verifier timeout de tour : `GTF` puis joueur suivant.
- [ ] Verifier qu'un combattant mort n'est jamais selectionne.
- [ ] Verifier qu'un combattant deconnecte ne bloque pas la timeline.

### Deplacement en combat

- [ ] Depenser les PM selon nombre de pas reel.
- [ ] Refuser deplacement si PM insuffisants.
- [ ] Refuser cellule occupee, invalide, bloquante.
- [ ] Envoyer `GA1;1;id;path`, puis retrait `GA;129`, puis `GIC/GTM`.
- [ ] Verifier que l'interface PM se met a jour immediatement.
- [ ] Tester deplacement autour des obstacles, ressources, objets interactifs, autres combattants.
- [ ] Ajouter un vrai pathfinding combat si le client envoie un chemin incoherent.

### Sorts et effets

- [ ] Verifier lancement officiel `GA300{spellId};{cell}` et parsing legacy.
- [ ] Depenser les PA avant effet et refuser si PA insuffisants.
- [ ] Verifier range, ligne de vue, cellule cible, cooldown, lancer par tour, lancer par cible.
- [ ] Gerer tous les effets courants : dommages elementaires, vol de vie, soin, retrait PA/PM, boost stats, poison, pieges, glyphes.
- [ ] Ne jamais utiliser `GA;306` pour synchroniser les PV : c'est un packet piege cote client.
- [ ] Synchroniser apres sort via `GTM`.
- [ ] Verifier mort : `GA;103`, retrait timeline/etat mort, cellule liberee.
- [ ] Verifier que l'interface PA se met a jour et reset au tour suivant.

### IA monstres

- [ ] Verifier qu'un monstre joue apres `GTSmonster`.
- [ ] Verifier qu'il attaque si adjacent.
- [ ] Verifier qu'il se deplace puis attaque si possible.
- [ ] Verifier qu'il passe son tour s'il ne peut rien faire.
- [ ] Verifier qu'il ne joue pas si mort avant son timer.
- [ ] Verifier qu'il ne joue pas si le combat est termine.
- [ ] Remplacer l'IA offset simple par A* combat.
- [ ] Ajouter sorts de monstres depuis templates au lieu du corps-a-corps fixe.

### Fin combat

- [ ] Detecter victoire equipe 0 ou equipe 1 sans attendre timeout.
- [ ] Envoyer `GE` avec gagnants/perdants, duree, xp, kamas, drops.
- [ ] Replacer les joueurs sur la bonne cellule apres combat.
- [ ] Redemarrer regen uniquement pour survivants connectes.
- [ ] Appliquer mort joueur : vie minimale, energie, familier, retour phoenix si necessaire.
- [ ] Respawn groupe monstre selon delai et map.
- [ ] Sauvegarder xp, kamas, drops, vie, map/cell.

### Spectateur et reconnexion

- [ ] Liste combats map `fL`, details `fD`, entree spectateur.
- [ ] Spectateur recoit `GJK` avec flag spectateur, sprites, `GIC`, `GS`, `GTL`, `GTM`, `GTS`.
- [ ] Quitter spectateur via `GQ` et `fV` : retirer session, envoyer `GV`, retour map normal.
- [ ] Deco spectateur : retirer session de la liste.
- [ ] Reco combattant pendant placement : revoir `GP`, sprites, `GIC`.
- [ ] Reco combattant pendant combat : revoir `GS/GTL/GTM/GTS`, pas quitter le combat.
- [ ] Deco joueur courant : passer le tour.

## P1 - Cartes, deplacement, interactifs

### Walkability et path

- [ ] Tester 20 maps avec `cellsData` officiel et fallback legacy.
- [ ] Verifier cellules interactives StarLoco : ressource traversable mais pas cellule d'arrivee.
- [ ] Verifier objets bloquants : puits, zaap, zaapi, portes, coffres.
- [ ] Verifier triggers changement map : pas de double teleport, pas de teleport apres interaction ressource.
- [ ] Verifier mini-map `BaM` : cellule de destination safe, map cible valide.
- [ ] Verifier `GKK` : libere action roleplay, execute action en attente.
- [ ] Tester chemin nul, chemin court, chemin invalide, deplacement pendant action.

### Zaaps / zaapis

- [ ] Verifier format panel `WC/Wc` : map courante, respawn, destinations, cout.
- [ ] Verifier cout dynamique selon distance.
- [ ] Verifier debits kamas et refus si kamas insuffisants.
- [ ] Implementer memorisation zaap par personnage.
- [ ] Verifier cellules arrivee safe autour du zaap/zaapi.
- [ ] Tester alignement pour zaapis Bonta/Brakmar.

### Ressources metiers

- [ ] Verifier `GA500/GA501/GDF` pour chaque type de ressource.
- [ ] Verifier outil requis, metier requis, niveau requis.
- [ ] Verifier respawn ressource et etat `GDF`.
- [ ] Verifier gain item, pods, sauvegarde SQL.
- [ ] Verifier interruption si inventaire plein.

## P1 - Inventaire, objets, stats

### Inventaire

- [ ] Verifier `OL`, `OAKO`, `OCKO`, `OM`, `OQ`, `OR`, `Ow`.
- [ ] Tester stack objets identiques avec jets identiques.
- [ ] Tester non-stack avec jets differents.
- [ ] Tester destruction, drop au sol, ramassage.
- [ ] Tester pods max et refus si surcharge.
- [ ] Tester sauvegarde/rechargement apres equipement.

### Equipement

- [ ] Verifier slots officiels : amulette, arme, anneaux, ceinture, bottes, coiffe, cape, familier, dofus, bouclier.
- [ ] Verifier conditions : niveau, classe, sexe, alignement, stats.
- [ ] Verifier arme deux mains contre bouclier.
- [ ] Verifier recalcul stats `As` apres equip/unequip.
- [ ] Verifier visuels `Oa` et `GM accessories`.
- [ ] Verifier bouclier avec et sans arme.
- [ ] Verifier armes/outils metiers visibles ou caches selon contexte.

### Stats

- [ ] Verifier base + equipement + dons + buffs dans `As`.
- [ ] Verifier initiative, prospection, pods, PA, PM.
- [ ] Verifier resistances fixes et pourcent.
- [ ] Verifier paliers de stats par classe.
- [ ] Verifier level-up : points stats/sorts, vie max, soins, packet client.

### Familiers

- [ ] Verifier affichage PV familier.
- [ ] Verifier nourriture, delais, refus nourriture invalide.
- [ ] Verifier perte PV a la mort du joueur.
- [ ] Verifier bonus stats familier dans `As`.
- [ ] Verifier sauvegarde/rechargement et equipement.

## P1 - Sorts hors combat et livres de sorts

- [ ] Verifier chargement `SpellsData` pour toutes les classes.
- [ ] Verifier `SL` au login : sortId, level, position.
- [ ] Verifier oubli/apprentissage sort si prevu.
- [ ] Verifier boost sort : cout points, max level, packet update.
- [ ] Verifier sorts speciaux de classe.
- [ ] Verifier animations/gfx de sorts en combat.

## P1 - Monstres, spawns, drops

- [ ] Charger tous les spawns utiles au demarrage ou lazy-load propre par zone, pas seulement map courante.
- [ ] Verifier composition groupes : taille, grade, niveau total, etoiles si prevu.
- [ ] Verifier format GM groupe roleplay : liste mobs, levels, couleurs, gfx.
- [ ] Verifier movement/respawn des groupes si implemente.
- [ ] Verifier drops par monstre : taux, seuil prospection, quantite.
- [ ] Verifier xp : niveau groupe, sagesse, challenge si futur.
- [ ] Verifier kamas : bornes min/max par grade.

## P1 - PNJ, dialogues, shops, banques

### PNJ et dialogues

- [ ] Verifier spawns PNJ sur toutes les maps chargees.
- [ ] Verifier GM PNJ : gfx, scale, couleurs, accessoires, custom art.
- [ ] Verifier ouverture `DC`, reponse `DR`, fermeture `DV`.
- [ ] Implementer conditions de questions/reponses.
- [ ] Implementer actions de dialogue : teleport, item, kamas, quete, shop.
- [ ] Tester dialogue multi-niveaux complet.

### Shops PNJ

- [ ] Implementer ouverture boutique `EW/EL`.
- [ ] Achat : verifier kamas, pods, stock, ajout item.
- [ ] Vente : verifier item, prix, retrait/fusion, credit kamas.
- [ ] Verifier fermeture propre de l'echange.

### Banque

- [ ] Tester depot/retrait kamas.
- [ ] Tester depot/retrait item, quantite partielle.
- [ ] Tester cout banque si officiel.
- [ ] Tester persistence banque apres deco.
- [ ] Tester refus inventaire plein.

## P1 - Chat, social, groupe

### Chat

- [ ] Verifier canaux : general, commerce, recrutement, groupe, guilde, alignement, admin, information.
- [ ] Verifier activation/desactivation `cC`.
- [ ] Verifier anti-spam, away, invisible.
- [ ] Verifier insertion item en chat.
- [ ] Verifier messages `Im` avec bons arguments.

### Groupe

- [ ] Invitation, acceptation, refus, leave, kick.
- [ ] Synchroniser leader, membres, niveaux, map.
- [ ] Verifier canal groupe.
- [ ] Verifier combat en groupe : rejoindre, placement multi-joueurs, drops/xp partages.

### Amis / ennemis / ignore

- [ ] Implementer liste amis.
- [ ] Implementer notification connexion ami.
- [ ] Implementer ignore pour chat/MP.

## P1 - Guildes, alignement, PvP

### Guildes

- [ ] Creation guilde : nom, blason, conditions, cout.
- [ ] Invitation guilde, accept/refus.
- [ ] Droits complets : inviter, bannir, gerer xp, percepteur, enclos.
- [ ] Liste membres `gL`, infos `gI`, messages guilde.
- [ ] XP guilde et repartition xp.
- [ ] Percepteurs : pose, combat, collecte, mort, respawn.

### Alignement / PvP

- [ ] Verifier alignement dans `As`, `ZS`, GM wings.
- [ ] Verifier grade, honneur, deshonneur.
- [ ] Verifier agressions PvP.
- [ ] Verifier mode spectateur en PvP.
- [ ] Verifier prisons, zones, zaapis alignement.
- [ ] Prismes/conquete : a implementer plus tard.

## P2 - Metiers et craft

- [ ] Verifier liste metiers `JS/JX/JO`.
- [ ] Verifier outil metier equipe.
- [ ] Verifier recettes chargees.
- [ ] Verifier craft simple : ingredients, quantites, resultat.
- [ ] Verifier craft echec/reussite selon niveau.
- [ ] Verifier XP metier reelle selon recette.
- [ ] Verifier level-up metier et packets XP/niveau.
- [ ] Implementer craft cooperatif si necessaire.
- [ ] Implementer forgemagie separement.

## P2 - Quetes, maisons, montures, modes speciaux

### Quetes

- [ ] Schema SQL complet : quests, steps, objectives, rewards.
- [ ] Packets `QF`, `QL`, `QS`, `Qr` selon client.
- [ ] Objectifs : parler PNJ, tuer monstre, collecter item, aller cellule/map.
- [ ] Rewards : xp, kamas, items, emotes, sorts.

### Maisons / coffres

- [ ] Affichage portes/maisons.
- [ ] Achat/vente maison.
- [ ] Code maison/coffre.
- [ ] Coffres persistants.

### Montures / enclos

- [ ] Inventaire monture.
- [ ] Equipement/desequipement.
- [ ] Apparence sur personnage.
- [ ] Enclos et objets d'elevage.

### Marchands / modes offline

- [ ] Mode marchand joueur.
- [ ] Store items et prix.
- [ ] Achat par autre joueur.
- [ ] Taxe/conditions maps.

## P2 - Admin, logs, outils

- [ ] Nettoyer droits admin : moderateur, admin, owner.
- [ ] Implementer `.reload` chaud pour donnees non critiques.
- [ ] Implementer `.ban/.unban` en BDD + refus login.
- [ ] Implementer `.god` en combat.
- [ ] Implementer `.invisible` dans GM et interactions.
- [ ] Exposer metriques : joueurs, uptime, memoire, packets.
- [ ] Ajouter endpoint HTTP ou commande admin pour metriques.
- [ ] Ajouter tests unitaires pour parsing packets critiques.
- [ ] Ajouter tests de non-regression pour map walkability, stats, inventaire.

## TODO/FIXME/XXX trouves dans le code src

Cette section vient d'un `rg -n "TODO|FIXME|XXX|HACK" src`.

### Combat / monstres

- [ ] [src/org/dofus/game/fight/Fighter.java:202](src/org/dofus/game/fight/Fighter.java:202) - `toFLEntry()` : verifier format exact Dofus 1.29.
- [ ] [src/org/dofus/database/objects/MonstersData.java:32](src/org/dofus/database/objects/MonstersData.java:32) - creer `monster_system.sql` avec donnees de base.
- [ ] [src/org/dofus/objects/monsters/MonsterGroup.java:155](src/org/dofus/objects/monsters/MonsterGroup.java:155) - formule officielle taille gfx selon level.
- [ ] [src/org/dofus/network/game/handlers/parsers/GameParser.java:162](src/org/dofus/network/game/handlers/parsers/GameParser.java:162) - FIXME mouvement bot a reprendre.
- [ ] [src/org/dofus/network/game/handlers/parsers/GameParser.java:164](src/org/dofus/network/game/handlers/parsers/GameParser.java:164) - chargement/spawn groupes monstres par maps/zones, pas uniquement map visitee.

### Stats, personnage, protocole GM

- [ ] [src/org/dofus/objects/actors/Characters.java:63](src/org/dofus/objects/actors/Characters.java:63) - extraire Alignment/AlignmentExp dans une classe dediee.
- [ ] [src/org/dofus/objects/actors/Characters.java:100](src/org/dofus/objects/actors/Characters.java:100) - initialisation channels a formaliser.
- [ ] [src/org/dofus/objects/actors/Characters.java:450](src/org/dofus/objects/actors/Characters.java:450) - champ GM stuff a completer/verifier.
- [ ] [src/org/dofus/objects/actors/Characters.java:455](src/org/dofus/objects/actors/Characters.java:455) - champ inconnu `1` a documenter.
- [ ] [src/org/dofus/objects/characters/Statistic.java:368](src/org/dofus/objects/characters/Statistic.java:368) - grade alignement dans stats.
- [ ] [src/org/dofus/objects/characters/Statistic.java:380](src/org/dofus/objects/characters/Statistic.java:380) - FIXME Ancestra "make better".
- [ ] [src/org/dofus/network/game/protocols/GProtocol.java:32](src/org/dofus/network/game/protocols/GProtocol.java:32) - alignment dons `/100` a verifier.
- [ ] [src/org/dofus/network/game/protocols/GProtocol.java:34](src/org/dofus/network/game/protocols/GProtocol.java:34) - champ GM inconnu a documenter.
- [ ] [src/org/dofus/objects/characters/breeds/Breed.java:11](src/org/dofus/objects/characters/breeds/Breed.java:11) - map de depart classe a formaliser.

### Chat / canaux

- [ ] [src/org/dofus/objects/characters/Channel.java:51](src/org/dofus/objects/characters/Channel.java:51) - canal information `^`.
- [ ] [src/org/dofus/network/game/handlers/parsers/BasicParser.java:96](src/org/dofus/network/game/handlers/parsers/BasicParser.java:96) - canal fight `#`.
- [ ] [src/org/dofus/network/game/handlers/parsers/BasicParser.java:109](src/org/dofus/network/game/handlers/parsers/BasicParser.java:109) - canal admin/game master `@`.
- [ ] [src/org/dofus/network/game/handlers/parsers/BasicParser.java:127](src/org/dofus/network/game/handlers/parsers/BasicParser.java:127) - canal alignement `!`.
- [ ] [src/org/dofus/network/game/handlers/parsers/BasicParser.java:129](src/org/dofus/network/game/handlers/parsers/BasicParser.java:129) - canal information `i`.
- [ ] [src/org/dofus/network/game/handlers/parsers/BasicParser.java:153](src/org/dofus/network/game/handlers/parsers/BasicParser.java:153) - packet special chat en combat.
- [ ] [src/org/dofus/network/game/handlers/parsers/BasicParser.java:166](src/org/dofus/network/game/handlers/parsers/BasicParser.java:166) - anti-spam / away.
- [ ] [src/org/dofus/network/game/handlers/parsers/BasicParser.java:171](src/org/dofus/network/game/handlers/parsers/BasicParser.java:171) - invisible.
- [ ] [src/org/dofus/network/game/handlers/parsers/ChannelParser.java:16](src/org/dofus/network/game/handlers/parsers/ChannelParser.java:16) - update personnage + save differe apres changement canal.

### Echanges, PNJ, banques, shops

- [ ] [src/org/dofus/network/game/handlers/parsers/DialogParser.java:106](src/org/dofus/network/game/handlers/parsers/DialogParser.java:106) - ouvrir boutique PNJ via protocole echange.
- [ ] [src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:23](src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:23) - boutique PNJ priorite 2.
- [ ] [src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:192](src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:192) - `EW` + `EL` items boutique.
- [ ] [src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:197](src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:197) - achat : verifier kamas, debit, ajout item.
- [ ] [src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:202](src/org/dofus/network/game/handlers/parsers/ExchangeParser.java:202) - vente : verifier item, retrait, credit kamas.

### Craft / metiers

- [ ] [src/org/dofus/network/game/handlers/parsers/CraftParser.java:188](src/org/dofus/network/game/handlers/parsers/CraftParser.java:188) - XP metier reelle selon niveau recette.
- [ ] [src/org/dofus/network/game/handlers/parsers/CraftParser.java:198](src/org/dofus/network/game/handlers/parsers/CraftParser.java:198) - renvoyer niveau metier + XP metier.

### Guildes

- [ ] [src/org/dofus/database/objects/GuildsData.java:23](src/org/dofus/database/objects/GuildsData.java:23) - creer `guild_system.sql`.
- [ ] [src/org/dofus/objects/guilds/Guild.java:25](src/org/dofus/objects/guilds/Guild.java:25) - droits complets.
- [ ] [src/org/dofus/objects/guilds/Guild.java:31](src/org/dofus/objects/guilds/Guild.java:31) - format exact blason/embleme.
- [ ] [src/org/dofus/objects/guilds/Guild.java:83](src/org/dofus/objects/guilds/Guild.java:83) - format exact packet Dofus 1.29.
- [ ] [src/org/dofus/objects/guilds/Guild.java:91](src/org/dofus/objects/guilds/Guild.java:91) - format exact packet Dofus 1.29.
- [ ] [src/org/dofus/objects/guilds/GuildMember.java:50](src/org/dofus/objects/guilds/GuildMember.java:50) - format membre guilde.
- [ ] [src/org/dofus/network/game/handlers/parsers/GuildParser.java:28](src/org/dofus/network/game/handlers/parsers/GuildParser.java:28) - implementation complete apres inventaire.
- [ ] [src/org/dofus/network/game/handlers/parsers/GuildParser.java:63](src/org/dofus/network/game/handlers/parsers/GuildParser.java:63) - verifier nom guilde unique.
- [ ] [src/org/dofus/network/game/handlers/parsers/GuildParser.java:91](src/org/dofus/network/game/handlers/parsers/GuildParser.java:91) - suppression guilde BDD.
- [ ] [src/org/dofus/network/game/handlers/parsers/GuildParser.java:102](src/org/dofus/network/game/handlers/parsers/GuildParser.java:102) - invitation guilde.

### Quetes

- [ ] [src/org/dofus/objects/quests/QuestTemplate.java:20](src/org/dofus/objects/quests/QuestTemplate.java:20) - creer `quest_system.sql` + implementer parser.
- [ ] [src/org/dofus/network/game/handlers/parsers/QuestParser.java:27](src/org/dofus/network/game/handlers/parsers/QuestParser.java:27) - implementation apres inventaire/PNJ.
- [ ] [src/org/dofus/network/game/handlers/parsers/QuestParser.java:46](src/org/dofus/network/game/handlers/parsers/QuestParser.java:46) - TODO non detaille.
- [ ] [src/org/dofus/network/game/handlers/parsers/QuestParser.java:56](src/org/dofus/network/game/handlers/parsers/QuestParser.java:56) - TODO non detaille.
- [ ] [src/org/dofus/network/game/handlers/parsers/QuestParser.java:64](src/org/dofus/network/game/handlers/parsers/QuestParser.java:64) - serialiser quetes actives.
- [ ] [src/org/dofus/network/game/handlers/parsers/QuestParser.java:77](src/org/dofus/network/game/handlers/parsers/QuestParser.java:77) - objectif `KILL`.
- [ ] [src/org/dofus/network/game/handlers/parsers/QuestParser.java:88](src/org/dofus/network/game/handlers/parsers/QuestParser.java:88) - objectif `TALK_NPC`.
- [ ] [src/org/dofus/network/game/handlers/parsers/QuestParser.java:100](src/org/dofus/network/game/handlers/parsers/QuestParser.java:100) - objectif `COLLECT_ITEM`.

### Admin / serveur / metriques

- [ ] [src/org/dofus/network/server/handlers/VersionHandler.java:18](src/org/dofus/network/server/handlers/VersionHandler.java:18) - centraliser packets.
- [ ] [src/org/dofus/network/server/handlers/ServerChoiceHandler.java:39](src/org/dofus/network/server/handlers/ServerChoiceHandler.java:39) - recherche ami.
- [ ] [src/org/dofus/network/server/handlers/NicknameHandler.java:24](src/org/dofus/network/server/handlers/NicknameHandler.java:24) - restrictions pseudo.
- [ ] [src/org/dofus/utils/ServerMetrics.java:14](src/org/dofus/utils/ServerMetrics.java:14) - brancher packets recus/envoyes.
- [ ] [src/org/dofus/utils/ServerMetrics.java:20](src/org/dofus/utils/ServerMetrics.java:20) - endpoint HTTP metriques.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:36](src/org/dofus/network/game/handlers/parsers/AdminParser.java:36) - droits granulaires.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:56](src/org/dofus/network/game/handlers/parsers/AdminParser.java:56) - `.reload`.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:57](src/org/dofus/network/game/handlers/parsers/AdminParser.java:57) - `.speed`.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:207](src/org/dofus/network/game/handlers/parsers/AdminParser.java:207) - ban BDD + blocage login.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:213](src/org/dofus/network/game/handlers/parsers/AdminParser.java:213) - unban.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:505](src/org/dofus/network/game/handlers/parsers/AdminParser.java:505) - mode god en combat.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:510](src/org/dofus/network/game/handlers/parsers/AdminParser.java:510) - invisibilite GM.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:523](src/org/dofus/network/game/handlers/parsers/AdminParser.java:523) - reload chaud.
- [ ] [src/org/dofus/network/game/handlers/parsers/AdminParser.java:528](src/org/dofus/network/game/handlers/parsers/AdminParser.java:528) - facteur vitesse bots.

### Auth / ecran personnage

- [ ] [src/org/dofus/network/game/handlers/GameScreenHandler.java:75](src/org/dofus/network/game/handlers/GameScreenHandler.java:75) - gifts compte.
- [ ] [src/org/dofus/network/game/handlers/GameScreenHandler.java:79](src/org/dofus/network/game/handlers/GameScreenHandler.java:79) - apply gift.
- [ ] [src/org/dofus/network/game/handlers/GameScreenHandler.java:83](src/org/dofus/network/game/handlers/GameScreenHandler.java:83) - client id.
- [ ] [src/org/dofus/network/game/handlers/GameScreenHandler.java:96](src/org/dofus/network/game/handlers/GameScreenHandler.java:96) - client key.
- [ ] [src/org/dofus/network/game/handlers/GameScreenHandler.java:99](src/org/dofus/network/game/handlers/GameScreenHandler.java:99) - pseudo aleatoire.
- [ ] [src/org/dofus/network/game/handlers/GameScreenHandler.java:104](src/org/dofus/network/game/handlers/GameScreenHandler.java:104) - resurrection heroique.

### Regen / emotes / divers

- [ ] [src/org/dofus/utils/RegenService.java:27](src/org/dofus/utils/RegenService.java:27) - verifier paquet debut regen officiel.
- [ ] [src/org/dofus/objects/characters/Emote.java:5](src/org/dofus/objects/characters/Emote.java:5) - implementer `toString()`.
- [ ] [src/org/dofus/objects/WorldData.java:14](src/org/dofus/objects/WorldData.java:14) - nettoyer commentaire XXX / role de stockage connectes.
- [ ] [src/org/dofus/network/server/handlers/AuthentificationHandler.java:27](src/org/dofus/network/server/handlers/AuthentificationHandler.java:27) - commentaires XXX a convertir en code clair/logs utiles.

### Stubs d'acteurs / sessions a nettoyer

- [ ] [src/org/dofus/network/game/BotSession.java:132](src/org/dofus/network/game/BotSession.java:132) - stubs session bot.
- [ ] [src/org/dofus/objects/actors/Merchant.java:9](src/org/dofus/objects/actors/Merchant.java:9) - stubs acteur marchand.
- [ ] [src/org/dofus/objects/actors/Mutant.java:9](src/org/dofus/objects/actors/Mutant.java:9) - stubs acteur mutant.
- [ ] [src/org/dofus/objects/actors/MountPark.java:9](src/org/dofus/objects/actors/MountPark.java:9) - stubs parc monture.
- [ ] [src/org/dofus/objects/actors/Monster.java:9](src/org/dofus/objects/actors/Monster.java:9) - stubs acteur monstre.
- [ ] [src/org/dofus/objects/actors/NPC.java:37](src/org/dofus/objects/actors/NPC.java:37) - stub NPC.
- [ ] [src/org/dofus/objects/actors/Prism.java:9](src/org/dofus/objects/actors/Prism.java:9) - stubs prisme.
- [ ] [src/org/dofus/objects/actors/TaxCollector.java:9](src/org/dofus/objects/actors/TaxCollector.java:9) - stubs percepteur.

## Definition de "fait" pour chaque mecanique

- [ ] Le scenario marche en client 1.29 sans erreur visible.
- [ ] Les packets envoyes sont documentes dans le code ou dans le changelog.
- [ ] Le bug ne revient pas apres deco/reco.
- [ ] La sauvegarde BDD est verifiee.
- [ ] Les cas d'erreur renvoient un packet propre (`BN`, `Im`, refus officiel) au lieu de bloquer le client.
- [ ] Les logs serveur permettent de diagnostiquer l'echec sans spam excessif.
