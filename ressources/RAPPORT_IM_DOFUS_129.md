# Audit des packets `Im` Dofus 1.29

Source analysée : `lang_fr_1254_scripts_frame_1.txt` extrait de ton ZIP.

## Règle importante

Le packet serveur est toujours de la forme `Im` + code. Le premier chiffre du code choisit la famille de message :

- `0` = `INFOS_`
- `1` = `ERROR_`
- `2` = `PVP_`

Exemples :

- `Im020;1000` = `INFOS_20` avec paramètre `1000`
- `Im189` = `ERROR_89`
- `Im1170;3~4` = `ERROR_170` avec paramètres `3` et `4`
- `Im0152;date~ip` = `INFOS_152`

## Résultat du tri

- Messages de langue identifiés : 505
- Messages `Im` utilisés dans Ancestra : 98
- Usages Ancestra trouvés : 150

## Pack généré

- `im_lang_all.csv` : tous les messages `INFOS`, `ERROR`, `PVP` présents dans le fichier langue.
- `im_used_in_ancestra.csv` : uniquement les `Im` réellement envoyés par Ancestra.
- `im_usage_details_ancestra.csv` : détail fichier/ligne/contexte de chaque usage.
- `InfoMessageId.java` : enum Java prête à intégrer ou adapter dans Sandbox2.

## Packets prioritaires pour ton émulateur

| Packet | Famille | Clé langue | Texte FR |
|---|---|---:|---|
| Im020 | INFOS | INFOS_20 | Tu as dû donner %1 kamas pour pouvoir accéder à ce coffre. |
| Im021 | INFOS | INFOS_21 | Tu as obtenu %1 '<b>%2</b>'. |
| Im022 | INFOS | INFOS_22 | Tu as perdu %1 '%2'. |
| Im034 | INFOS | INFOS_34 | Tu as perdu <b>%1</b> points d'énergie. |
| Im076 | INFOS | INFOS_76 | Tu as perdu <b>%1</b> points d' honneur. |
| Im080 | INFOS | INFOS_80 | Vous gagnez %1 points d'honneur en récompense de votre bravoure. |
| Im081 | INFOS | INFOS_81 | Vous perdez %1 points d'honneur suite à cette défaite. |
| Im082 | INFOS | INFOS_82 | Votre bravoure vous a fait grimper au rang %1. |
| Im083 | INFOS | INFOS_83 | Vous avez été dégradé au rang %1. |
| Im189 | ERROR | ERROR_89 | Bienvenue sur DOFUS Retro, dans le Monde des Douze !\nRappel : prenez garde, il est interdit de transmettre votre identifiant de connexion ainsi que votre mot de passe. |
| Im0152 | INFOS | INFOS_152 | Précédente connexion sur votre compte effectuée le %3/%2/%1 à %4:%5 via l'adresse IP %6 |
| Im0153 | INFOS | INFOS_153 | Votre adresse IP actuelle est %1. |
| Im1170 | ERROR | ERROR_170 | Impossible de lancer ce sort : Vous avez %1 PA disponible(s) et il vous en faut %2 pour ce sort ! |
| Im1171 | ERROR | ERROR_171 | Impossible de lancer ce sort : Vous avez une portée de %1 à %2 et vous visez à %3 ! |
| Im1169 | ERROR | ERROR_169 | Impossible de lancer ce sort : vous ne le possédez pas ! |
| Im1172 | ERROR | ERROR_172 | Impossible de lancer ce sort : la cellule visée n'est pas disponible ! |
| Im1173 | ERROR | ERROR_173 | Impossible de lancer ce sort autrement qu'en ligne droite ! |
| Im1174 | ERROR | ERROR_174 | Impossible de lancer ce sort : un obstacle gène votre vue ! |
| Im1175 | ERROR | ERROR_175 | Impossible de lancer ce sort actuellement. |
| Im093 | INFOS | INFOS_93 | L'équipe n'accepte désormais que les membres du groupe du personnage principal. |
| Im094 | INFOS | INFOS_94 | L'équipe accepte les membres de tous les groupes. |
| Im095 | INFOS | INFOS_95 | L'équipe n'accepte plus de personnages supplémentaires. |
| Im096 | INFOS | INFOS_96 | L'équipe accepte de nouveau des personnages supplémentaires. |
| Im0103 | INFOS | INFOS_103 | Demande d'aide signalée... |
| Im0104 | INFOS | INFOS_104 | Demande d'aide annulée... |

## Conseils d'intégration Sandbox2

Ne mets pas ces textes en dur dans les handlers. Crée plutôt une petite classe serveur qui construit les packets, par exemple `InfoMessagePackets.kamasPaid(amount)` qui retourne `Im020;amount`.

Le client possède déjà les textes en langue française. Ton serveur doit seulement envoyer le bon code et les bons paramètres.
