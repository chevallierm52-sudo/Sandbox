# Dofus 1.29 Sandbox Emulator

Emulateur Java prive pour le client Dofus 1.29.1. Le projet fournit un serveur login et un serveur game, charge les donnees depuis MySQL/MariaDB, et reconstruit progressivement les mecanismes officiels 1.29 avec des references d'emulateurs historiques quand c'est utile.

Le projet est en alpha technique. Beaucoup de systemes sont utilisables pour tester, mais le comportement n'est pas encore completement fidele a l'officiel.

## Etat actuel

- Connexion compte, handshake 1.29.1, selection de personnage et entree en jeu.
- Chargement des cartes, cellules interactives, PNJ, monstres, objets, familiers, sorts, guildes et experiences.
- Monde roleplay: affichage des acteurs, deplacements, changements de carte, triggers, PNJ, dialogues, zaaps/zaapis, banques, echanges, metiers et craft.
- Inventaire: equipement, pods, accessoires visibles, boucliers, effets d'objets et actions d'utilisation.
- Sorts: templates charges depuis la base, barre de sorts, effets principaux et debut d'integration en combat.
- Combat PvM: entree en combat par groupe de monstres, phase placement, choix de cellule, pret, initiative, timeline, tours, PA/PM, deplacement, lancer de sort, degats, morts, IA monstre simple, fin de combat, xp/kamas/drops, spectateur et reconnexion en combat.
- Systeme bot optionnel: IA conversationnelle, memoire locale, path memory et hooks de spawn desactives par defaut dans le code.
- Logs reseau detailles pour comparer les paquets client/serveur pendant le debug.

Voir aussi:

- [CHANGELOG.md](CHANGELOG.md) pour l'historique des changements.
- [TODO.md](TODO.md) pour la liste de debug globale et les priorites.

## Prerequis

- Java 8 ou plus. Le code reste compile en cible Java 8.
- MySQL ou MariaDB.
- Client Dofus 1.29.1 configure pour pointer vers le serveur local.
- Les donnees SQL du projet importees dans la base `Dofus`.

Les dependances Java sont dans `ressources/libs/`.

## Configuration

Le fichier principal est `config.properties`.

```properties
db.host=127.0.0.1
db.username=root
db.password=
db.name=Dofus

server.login.port=499
server.game.port=5555

bot.ai.enabled=false
bot.ai.key=
bot.ai.model=gpt-3.5-turbo
```

Ne versionne pas de vraie cle API dans ce fichier. Garde `bot.ai.enabled=false` si le systeme bot n'est pas teste.

## Lancement

Depuis Eclipse:

1. Importer le projet Java.
2. Verifier que `ressources/libs/*.jar` est dans le classpath.
3. Lancer `org.dofus.Main`.

Depuis une console, compile les sources vers `bin` avec le classpath des jars de `ressources/libs`, puis lance:

```powershell
java -cp "bin;ressources/libs/*" org.dofus.Main
```

Au demarrage, les logs attendus ressemblent a:

```text
Dofus 1.29.1 sandbox 3.x
Database pool initialized
Server listening on port 499
Game listening on port 5555
Initialisation terminee
```

## Structure

```text
src/org/dofus/
  constants/      Constantes applicatives et protocolaires
  database/       Connexion SQL, loaders et objets de donnees
  game/           Logique metiers, combats, drops, actions
  network/        Serveurs login/game, sessions, parsers paquets
  objects/        Comptes, personnages, cartes, items, acteurs
  utils/          Encodage, helpers, logs

ressources/
  libs/           Dependances Java
  data/           Donnees statiques selon installation locale

config.properties Configuration locale
CHANGELOG.md      Historique
TODO.md           Debug global et roadmap technique
```

## Flux reseau principal

Login:

```text
HC -> version -> credentials -> account data -> server list -> game token
```

Game:

```text
AT -> AL -> AS -> GC/GCK -> GDM -> GI -> GM/GDK
```

Combat PvM:

```text
GA movement to monster cell
GJK placement
GP placement cells
GM fighters
GIC fighter cells
GR ready
GS fight start
GTL/GTM timeline
GTS/GTF turns
GA actions
GE fight end
```

Quand un affichage est casse, comparer d'abord les paquets `GDM`, `GI`, `GM`, `GIC`, `GTM`, `GTS`, `GTF` et `GE`.

## Debug courant

- Personnage ou monstres invisibles: verifier le format `GM|+`, les cellules `GIC`, le `GTM` et redemarrer completement le serveur apres compilation.
- Deplacement bloque apres creation: verifier la cellule sauvegardee du personnage, l'occupation de cellule et la carte envoyee au client.
- Deplacement infini en combat: verifier le fighter actif, les PM restants, le path valide et l'envoi de `GTM` apres action.
- PA/PM qui ne reviennent pas: verifier `startTurn`, `finishTurn`, `resetTurnPoints` et le `GTM` diffuse au debut du tour.
- Sort qui affiche `undefined`: eviter les paquets d'effet inventes et synchroniser l'etat via les actions officielles plus `GTM`.
- IA qui ne passe pas son tour: verifier les timers `FightTurn`, l'etat du combat et l'absence d'anciennes classes compilees dans `bin`.
- Reconnexion en combat: verifier que la session est rattachee au fighter existant avant l'envoi de la carte roleplay.
- Spectateur bloque: utiliser la sortie spectateur et nettoyer la session via le parser combat.

Les details plus fins et les priorites sont dans [TODO.md](TODO.md).

## Discipline de changement

- Garder les paquets proches du comportement officiel 1.29.
- Preferer des changements petits, testables et lies au bug observe.
- Mettre a jour `CHANGELOG.md` pour les changements de comportement.
- Ajouter les points de debug restants dans `TODO.md` au lieu d'allonger ce README.
- Ne pas masquer un probleme client par un paquet invente si un paquet officiel existe.

## Notes

Ce depot est un sandbox de recherche et de developpement. Il n'est pas destine a une exploitation publique.
