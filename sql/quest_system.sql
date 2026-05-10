-- =============================================================================
-- quest_system.sql — Système de quêtes Dofus 1.29
-- Tables : quest_templates, quest_steps, quest_objectives, quest_rewards,
--          character_quests
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Templates de quêtes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quest_templates` (
    `id`             INT          NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(100) NOT NULL DEFAULT '',
    `description`    TEXT,
    `repeatable`     TINYINT(1)   NOT NULL DEFAULT 0,
    `level_required` TINYINT      NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Étapes d'une quête (ordonnées par step_order)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quest_steps` (
    `id`             INT          NOT NULL AUTO_INCREMENT,
    `quest_id`       INT          NOT NULL,
    `step_order`     TINYINT      NOT NULL DEFAULT 1,
    `name`           VARCHAR(100) NOT NULL DEFAULT '',
    `description`    TEXT,
    PRIMARY KEY (`id`),
    INDEX `idx_quest` (`quest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Objectifs d'une étape
-- type : 0=parler_pnj 1=tuer_monstre 2=collecter_item 3=atteindre_carte
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quest_objectives` (
    `id`             INT          NOT NULL AUTO_INCREMENT,
    `step_id`        INT          NOT NULL,
    `type`           TINYINT      NOT NULL DEFAULT 0,
    `target_id`      INT          NOT NULL DEFAULT 0  COMMENT 'npcId / monsterId / itemTemplateId / mapId selon type',
    `quantity`       INT          NOT NULL DEFAULT 1,
    `description`    VARCHAR(200) NOT NULL DEFAULT '',
    PRIMARY KEY (`id`),
    INDEX `idx_step` (`step_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Récompenses d'une étape (accordées à la complétion)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quest_rewards` (
    `id`             INT          NOT NULL AUTO_INCREMENT,
    `step_id`        INT          NOT NULL,
    `kamas`          INT          NOT NULL DEFAULT 0,
    `xp`             INT          NOT NULL DEFAULT 0,
    `item_id`        INT          NOT NULL DEFAULT 0  COMMENT '0 = pas d\'item',
    `item_qty`       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    INDEX `idx_step` (`step_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Progression des personnages dans les quêtes
-- status : 0=pas commencée 1=en cours 2=terminée
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `character_quests` (
    `character_id`   INT          NOT NULL,
    `quest_id`       INT          NOT NULL,
    `step_id`        INT          NOT NULL DEFAULT 0  COMMENT 'Étape courante (0 si non commencée)',
    `status`         TINYINT      NOT NULL DEFAULT 0,
    `started_at`     DATETIME     DEFAULT NULL,
    `completed_at`   DATETIME     DEFAULT NULL,
    PRIMARY KEY (`character_id`, `quest_id`),
    INDEX `idx_quest` (`quest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Données démo — Quête "Première mission"
-- -----------------------------------------------------------------------------

-- Quête
INSERT INTO `quest_templates` (`id`, `name`, `description`, `level_required`) VALUES
(1, 'Première Mission', 'Une quête pour les héros débutants d\'Incarnam.', 1);

-- Étape 1 : Parler au Garde
INSERT INTO `quest_steps` (`id`, `quest_id`, `step_order`, `name`, `description`) VALUES
(1, 1, 1, 'Rapport au garde',    'Parle au Garde de la cité pour recevoir tes instructions.'),
(2, 1, 2, 'Élimination de Tofus','Élimine 3 Tofus qui menacent les récoltes.'),
(3, 1, 3, 'Retour au garde',     'Retourne voir le Garde pour toucher ta récompense.');

-- Objectifs
INSERT INTO `quest_objectives` (`step_id`, `type`, `target_id`, `quantity`, `description`) VALUES
(1, 0, 1,    1, 'Parle au Garde (PNJ 1)'),
(2, 1, 1,    3, 'Tue 3 Tofus'),
(3, 0, 1,    1, 'Retourne voir le Garde');

-- Récompenses
INSERT INTO `quest_rewards` (`step_id`, `kamas`, `xp`, `item_id`, `item_qty`) VALUES
(1, 0,   50,  0, 0),
(2, 200, 200, 7, 2),   -- 2 oreilles de Tofu
(3, 500, 500, 5, 1);   -- 1 pain d'orge

-- Quête 2 simple (répétable — farm)
INSERT INTO `quest_templates` (`id`, `name`, `description`, `level_required`, `repeatable`) VALUES
(2, 'Chasse aux Larves', 'Élimine 5 Larves Blanches pour protéger la zone.', 3, 1);

INSERT INTO `quest_steps` (`id`, `quest_id`, `step_order`, `name`, `description`) VALUES
(4, 2, 1, 'Élimination', 'Tue 5 Larves Blanches.');

INSERT INTO `quest_objectives` (`step_id`, `type`, `target_id`, `quantity`, `description`) VALUES
(4, 1, 2, 5, 'Tue 5 Larves Blanches');

INSERT INTO `quest_rewards` (`step_id`, `kamas`, `xp`) VALUES
(4, 150, 150);
