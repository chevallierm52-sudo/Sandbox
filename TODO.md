# TODO — Dofus 1.29 Sandbox Roadmap

> Légende : 🔴 Critique · 🟠 Important · 🟡 Moyen · 🟢 Optionnel · ✅ Fait

---

## 🔴 Critique — À corriger en priorité

- [ ] **Écran noir au login** — Les fixes `fC0` + `extraClip` ont été appliqués. À valider en jeu. Si le problème persiste, vérifier que les GFX IDs des PNJ (`gfxID`) existent dans les fichiers SWF du client.
- [ ] **Affichage des PNJ** — Vérifier en jeu que les PNJ s'affichent avec les bonnes couleurs et accessoires (format GM 12 champs). Si invisible : `gfxID=0` dans la BDD → le sprite est vide, ce n'est pas un bug code.
- [ ] **Sauvegarde position personnage** — `CharactersData.update()` sauvegarde-t-il bien la `currentMap` et `currentCell` après un téléport ? Vérifier que la position est correcte à la reconnexion.
- [ ] **Déconnexion propre** — `account.connected` reste parfois à `true` après un crash client. Le fix AuthentificationHandler gère le cas, mais tester plusieurs scénarios (crash, déco brutale, timeout MINA).

---

## 🟠 Important — Fonctionnalités manquantes

### Objets & Inventaire
- [ ] **Système d'inventaire** — Table `items` (id, owner_id, template_id, quantité, position, stats). Paquet `OL` (liste d'objets) à envoyer au login.
- [ ] **Équipement** — Slots d'équipement (arme, chapeau, cape, etc.). Mise à jour du paquet GM (champ accessories) quand un objet est équipé.
- [ ] **Système de kamas** — La BDD stocke les kamas mais il n'y a pas d'interface pour les obtenir/dépenser en dehors des zaaps.
- [ ] **Drop de monstres** — Lié au système de combat.

### Combat
- [ ] **Système de combat complet** — Parsing du paquet `GA` pour les combats (action ID ≠ 1), placement initial (`fP`), tours (`fTH`, `fTN`), fin de combat (`fR`).
- [ ] **Monstres sur les cartes** — Table `monster_spawns`, chargement dans `MapTemplate`, inclusion dans le paquet GM (format différent : `cell;dir;0;groupId;monsterId~qty,monsterId~qty,...`).
- [ ] **Sorts** — Chargement depuis BDD, paquet `SL` actuellement vide (`SL` sans données = aucun sort). Lancement de sort en combat (`GA` avec action 300+).

### Dialogue PNJ
- [ ] **Boutique PNJ (`SHOP`)** — `DialogParser.reply()` envoie `BN` comme placeholder. Implémenter le protocole d'échange : `EW{npcId}` → liste d'objets → achat/vente via `EA`, `EB`.
- [ ] **Arbre de dialogue multi-niveaux** — Actuellement le mode simplifié n'a qu'une question. Si `npc_questions` et `npc_replies` sont présentes en BDD, l'arbre complet fonctionne. Importer les vraies données.
- [ ] **Conditions de dialogue (`question_cond`)** — La colonne `question_cond` existe en BDD mais n'est pas utilisée. Permettrait d'afficher des questions différentes selon le niveau, l'alignement, les quêtes, etc.

### Guildes
- [ ] **Création de guilde** — Paquet `gC`. Table `guilds` + `guild_members`.
- [ ] **Percepteur de guilde** — Spawn sur carte via `gP`, collecte kamas, paquet `GM` avec GFX Percepteur.
- [ ] **Interface guilde** — Packets `gI`, `gJ`, `gL`, `gM` (info, rejoindre, liste, message).

---

## 🟡 Moyen — Améliorations qualité

### Carte & Déplacements
- [ ] **Animation de téléport** — Actuellement `GA;2;id;` est envoyé mais sans véritable animation de fondu. Vérifier que le client gère bien.

### Personnage
- [ ] **Sorts du personnage** — `SL` envoie une chaîne vide. Charger les sorts depuis la BDD et envoyer la liste au login.
- [ ] **Émotes** — `BasicParser.emoticons()` existe mais n'envoie rien. Diffuser `GA0;1;id;emoteId` à tous les joueurs de la carte.
- [ ] **Alignement & Ailes** — `showWings` non utilisé dans le paquet GM. Ajouter la logique pour afficher les ailes selon le grade.
- [ ] **Énergie** — Diminue au combat (mort), remonte avec le repos/nuit. Actuellement statique à 10000.
- [ ] **Régénération de vie** — La vie ne se régénère pas. Timer de regen à ajouter (paquet `AS` re-envoyé).

### Interface
- [ ] **Annonce serveur** — Paquet `Im` pour envoyer des messages système à tous les joueurs connectés.
- [ ] **Commandes GM en chat** — Préfixe `/` ou `.` pour des commandes admin : téléport, donner kamas, donner objets, kick.
- [ ] **Canal général** — Le canal `|` (général) fonctionne en diffusion uniquement. Ajouter les canaux `#` (commerce), `@` (recrutement), `%` (groupe), `$` (guilde).

### Zaap / Zaapi
- [ ] **Mémorisation zaap** — Un joueur doit d'abord interagir avec un zaap pour le "débloquer". Actuellement tous les zaaps sont accessibles d'emblée. Table `character_zaaps(character_id, map_id)`.
- [ ] **Coût dynamique zaap** — Le coût varie selon la distance. Actuellement fixe à 200 kamas.

---

## 🟢 Optionnel — Finitions & Contenu

### Contenu
- [ ] **Quêtes** — Table `quests`, `quest_steps`, `quest_rewards`. Packets `QF`, `Qr`, etc.
- [ ] **Maisons** — Achat, décoration, coffre, porte verrouillée.
- [ ] **Métiers** — Crafting, récolte, forgemagie.
- [ ] **Incarnam** — Carte de départ pour les nouveaux personnages (niveau < 10 → spawn Incarnam).
- [ ] **Tutoriel** — Séquence initiale pour les nouveaux joueurs.

### Technique
- [ ] **Système de droits granulaire** — `Right.java` a plusieurs bits mais seul `canMoveAllDirections()` (8192) est vérifié. Implémenter `isModerator()`, `isAdmin()`, `canBan()`, etc.
- [ ] **Reload à chaud** — Commande pour recharger les données PNJ / cartes sans redémarrer le serveur.
- [ ] **Métriques** — Nombre de joueurs connectés, uptime, mémoire utilisée → paquet `Im` ou endpoint HTTP.
- [ ] **Tests unitaires** — Au minimum : `Statistic`, `BoostParser`, `DialogParser`, `WaypointParser`.
- [ ] **Configuration étendue** — `config.properties` : activer/désactiver les bots, nombre de bots, map de spawn, etc.
- [ ] **Persistance position bots** — Les bots repartent toujours du même point au démarrage. Varier les cellules initiales ou les sauvegarder.
- [ ] **Pathfinding bots** — Le déplacement actuel est purement offset. Utiliser un pathfinding A* pour éviter les cellules bloquées et naviguer proprement.

---

## ✅ Déjà implémenté


- ✅ Pool de connexions BDD (5 connexions, `Connector.java`)
- ✅ Logging packets console (`PacketLogger.java`)
- ✅ Boost de stats avec paliers officiels (`BoostParser.java`)
- ✅ Sauvegarde différée des stats (`DeferredSaveService.java`)
- ✅ Système de bots IA (déplacement + messages + réponses PM)
- ✅ Invitations de groupe bots (auto-acceptées)
- ✅ Téléport carte monde double-clic (`BaM`, droits GM)
- ✅ Changement de carte par triggers (joueurs) — `RolePlayMovement.end()` + `teleport()`
- ✅ Changement de carte des bots — `BotBehavior.botChangeMap()` déclenché sur cellule trigger
- ✅ Format GM PNJ corrigé — `templateId;-4;gfxId^scale` (discriminant NPC Flash)
- ✅ Dialogue PNJ — `DCK{actorId}` (sprite), `DR{qId}|{rId}` parsing, questions/réponses globales
- ✅ Système Zaap/Zaapi (`WaypointParser.java`)
- ✅ Système PNJ complet (templates, questions, réponses, spawns JSON)
- ✅ Protocole dialogue PNJ (`DC`, `DR`, `DV`)
- ✅ Paquet GM PNJ 12 champs (couleurs, accessoires, échelle)
- ✅ Fix double `fC0` (écran noir au login)
- ✅ Fix `extraClip` non-neutre (plantage rendu Flash)
- ✅ Fix NPE `AuthentificationHandler` (flag `connected` périmé)
- ✅ Fix NPE multiples (`PartyParser`, `BasicParser`, `GameParser`, etc.)
- ✅ SQL injection → `PreparedStatement` partout
- ✅ `ResultSet` fermés (`try-with-resources`)
- ✅ Thread-safety (`Statistic` et `Account` non-static)
- ✅ Parties / groupes de joueurs
- ✅ Alignement (Bonta/Brakcmar) + expérience
- ✅ Expérience personnage + montée de niveau
- ✅ Canaux de chat (général, commerce, recrutement, groupe)
- ✅ Émotes (envoi, pas de diffusion → TODO)
- ✅ Restrictions personnage
- ✅ `config.properties` (host, port, BDD)
