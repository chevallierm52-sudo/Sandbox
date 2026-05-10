-- ============================================================
-- NPC System — Dofus 1.29 Sandbox
-- ============================================================

-- NPC types: appearance + which dialog tree to open first
CREATE TABLE IF NOT EXISTS `npc_templates` (
    `id`             INT         NOT NULL,
    `name`           VARCHAR(64) NOT NULL,
    `gfx`            INT         NOT NULL,   -- sprite/model ID from client files
    `first_question` INT         NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Dialog questions. Each NPC owns its own question IDs.
-- `replies` = semicolon-separated reply IDs, e.g. "101;102;103"
CREATE TABLE IF NOT EXISTS `npc_questions` (
    `id`      INT          NOT NULL,
    `npc_id`  INT          NOT NULL,
    `text`    VARCHAR(512) NOT NULL,
    `replies` VARCHAR(256) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_q_npc` (`npc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Dialog replies.
-- `action` : NEXT | CLOSE | SHOP
-- `params` : for NEXT → next question id (as string)
--            for SHOP → future item list
--            for CLOSE → empty
CREATE TABLE IF NOT EXISTS `npc_replies` (
    `id`      INT          NOT NULL,
    `npc_id`  INT          NOT NULL,
    `text`    VARCHAR(256) NOT NULL,
    `action`  VARCHAR(16)  NOT NULL DEFAULT 'CLOSE',
    `params`  VARCHAR(256)          DEFAULT '',
    PRIMARY KEY (`id`),
    KEY `fk_r_npc` (`npc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- NPC instances placed on maps.
-- Actor ID seen by the client = id + 100000 (see NPC.getActorId()).
-- `direction` : EOrientation ordinal (0=E, 1=SE, 2=S, 3=SW, 4=W, 5=NW, 6=N, 7=NE)
CREATE TABLE IF NOT EXISTS `npc_spawns` (
    `id`           INT      NOT NULL AUTO_INCREMENT,
    `npc_template` INT      NOT NULL,
    `map_id`       INT      NOT NULL,
    `cell`         SMALLINT NOT NULL,
    `direction`    TINYINT  NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    KEY `fk_s_tpl` (`npc_template`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

-- ============================================================
-- DEMO DATA — remove / adapt to match your client's GFX IDs
-- ============================================================

-- Template 1 : Marchand de ressources (GFX 100 = common merchant sprite)
INSERT IGNORE INTO `npc_templates` VALUES (1, 'Marchand', 100, 1001);

-- Questions for NPC 1
-- Question 1001 : greeting
INSERT IGNORE INTO `npc_questions` VALUES
  (1001, 1, 'Bienvenue, aventurier ! Que puis-je faire pour toi ?', '101;102;103');

-- Question 1002 : shop branch
INSERT IGNORE INTO `npc_questions` VALUES
  (1002, 1, 'Voici ce que j''ai en stock. (Boutique à venir...)', '104');

-- Question 1003 : info branch
INSERT IGNORE INTO `npc_questions` VALUES
  (1003, 1, 'Ce serveur est un sandbox Dofus 1.29. Amuse-toi bien !', '104');

-- Replies for NPC 1
INSERT IGNORE INTO `npc_replies` VALUES
  (101, 1, 'Je voudrais acheter quelque chose.',  'NEXT', '1002'),
  (102, 1, 'Donne-moi des informations.',          'NEXT', '1003'),
  (103, 1, 'Au revoir.',                           'CLOSE', ''),
  (104, 1, 'Merci, à bientôt !',                   'CLOSE', '');

-- Template 2 : Garde (GFX 80)
INSERT IGNORE INTO `npc_templates` VALUES (2, 'Garde', 80, 2001);

INSERT IGNORE INTO `npc_questions` VALUES
  (2001, 2, 'Halte ! Qui va là ? ... Ah, un aventurier. Passez.', '201');

INSERT IGNORE INTO `npc_replies` VALUES
  (201, 2, 'Merci, garde.', 'CLOSE', '');

-- ============================================================
-- SPAWNS — place NPCs on map 7411 (sandbox map with bots)
-- ============================================================
INSERT IGNORE INTO `npc_spawns` (`npc_template`, `map_id`, `cell`, `direction`) VALUES
  (1, 7411, 290, 1),   -- Marchand at cell 290, facing SE
  (2, 7411, 265, 5);   -- Garde at cell 265, facing NW
