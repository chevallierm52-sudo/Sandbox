-- =============================================================================
-- guild_system.sql — Système de guildes Dofus 1.29
-- Tables : guilds, guild_members
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Guildes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `guilds` (
    `id`             INT          NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(50)  NOT NULL DEFAULT '',
    `emblem`         VARCHAR(100) NOT NULL DEFAULT '1;1;1;1;1' COMMENT 'bg_shape;bg_color;mg_shape;mg_color;fg_color',
    `level`          TINYINT      NOT NULL DEFAULT 1,
    `xp`             BIGINT       NOT NULL DEFAULT 0,
    `leader_id`      INT          NOT NULL DEFAULT 0  COMMENT 'characters.id du meneur',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Membres
-- Rights bitmask (exemples) :
--   1  = gérer les rangs       2  = inviter
--   4  = gérer le paddock      8  = gérer le HDV
--   16 = gérer le coffre       32 = enregistrer les hauts faits
--   255 = chef (tous droits)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `guild_members` (
    `character_id`   INT          NOT NULL,
    `guild_id`       INT          NOT NULL,
    `rank`           TINYINT      NOT NULL DEFAULT 0  COMMENT '0=novice 1=soldat 2=gardien 3=bras droit 4=chef',
    `rights`         SMALLINT     NOT NULL DEFAULT 0  COMMENT 'Bitmask des permissions',
    `xp_given`       BIGINT       NOT NULL DEFAULT 0  COMMENT 'XP totale donnée à la guilde',
    `xp_percent`     TINYINT      NOT NULL DEFAULT 0  COMMENT '% XP reversé à la guilde (0-90)',
    `joined_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_login`     DATETIME     DEFAULT NULL,
    PRIMARY KEY (`character_id`),
    INDEX `idx_guild` (`guild_id`),
    CONSTRAINT `fk_gm_guild` FOREIGN KEY (`guild_id`) REFERENCES `guilds`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Données démo
-- -----------------------------------------------------------------------------

-- Guilde de démonstration
INSERT INTO `guilds` (`id`, `name`, `emblem`, `level`, `xp`, `leader_id`) VALUES
(1, 'Les Aventuriers', '3;4;5;2;1', 3, 45000, 1);

-- Membres démo (personnage id=1 est chef, id=2 est bras droit)
INSERT INTO `guild_members` (`character_id`, `guild_id`, `rank`, `rights`, `xp_given`, `xp_percent`) VALUES
(1, 1, 4, 255, 30000, 10),
(2, 1, 3, 6,   15000, 5);
