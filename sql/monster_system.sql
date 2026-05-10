-- =============================================================================
-- monster_system.sql — Système de monstres Dofus 1.29
-- Tables : monster_templates, monster_grades, monster_spawns
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Templates de monstres
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `monster_templates` (
    `id`           INT          NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(100) NOT NULL DEFAULT '',
    `gfx_id`       INT          NOT NULL DEFAULT 0   COMMENT 'Identifiant sprite Flash',
    `colors`       VARCHAR(50)  NOT NULL DEFAULT '-1,-1,-1' COMMENT 'color1,color2,color3 (-1 = défaut)',
    `align`        TINYINT      NOT NULL DEFAULT 0   COMMENT '0=neutre 1=bonta 2=brak',
    `race`         TINYINT      NOT NULL DEFAULT 0,
    `can_tackle`   TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Grades (niveaux d'un même monstre — 1 ligne par grade)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `monster_grades` (
    `monster_id`   INT          NOT NULL,
    `grade`        TINYINT      NOT NULL DEFAULT 1,
    `level`        SMALLINT     NOT NULL DEFAULT 1,
    `life`         INT          NOT NULL DEFAULT 50,
    `ap`           TINYINT      NOT NULL DEFAULT 6,
    `mp`           TINYINT      NOT NULL DEFAULT 3,
    `strength`     INT          NOT NULL DEFAULT 0,
    `intelligence` INT          NOT NULL DEFAULT 0,
    `chance`       INT          NOT NULL DEFAULT 0,
    `agility`      INT          NOT NULL DEFAULT 0,
    `wisdom`       INT          NOT NULL DEFAULT 0,
    `vitality`     INT          NOT NULL DEFAULT 0,
    `res_neutral`  INT          NOT NULL DEFAULT 0   COMMENT 'Résistance % neutre',
    `res_earth`    INT          NOT NULL DEFAULT 0,
    `res_fire`     INT          NOT NULL DEFAULT 0,
    `res_water`    INT          NOT NULL DEFAULT 0,
    `res_air`      INT          NOT NULL DEFAULT 0,
    `xp_base`      INT          NOT NULL DEFAULT 10  COMMENT 'XP de base accordée',
    `kamas_min`    INT          NOT NULL DEFAULT 0,
    `kamas_max`    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`monster_id`, `grade`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Spawns de groupes de monstres sur les cartes
-- Format multi-monstres : "monsterId~grade,monsterId~grade"
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `monster_spawns` (
    `id`           INT          NOT NULL AUTO_INCREMENT,
    `map_id`       INT          NOT NULL,
    `cell_id`      SMALLINT     NOT NULL DEFAULT 0,
    `orientation`  TINYINT      NOT NULL DEFAULT 1,
    `composition`  VARCHAR(255) NOT NULL             COMMENT 'Ex: 1~2,2~1 = Tofu grade2 + Larve grade1',
    PRIMARY KEY (`id`),
    INDEX `idx_map` (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Données démo
-- -----------------------------------------------------------------------------

-- Monstres
INSERT INTO `monster_templates` (`id`, `name`, `gfx_id`, `colors`) VALUES
(1, 'Tofu',           39,  '-1,-1,-1'),
(2, 'Larve Blanche',  60,  '-1,-1,-1'),
(3, 'Larve Bleue',    61,  '-1,-1,-1'),
(4, 'Larve Violette', 62,  '-1,-1,-1'),
(5, 'Bouftou',        17,  '-1,-1,-1'),
(6, 'Bouftou Royal',  17,  '16777215,-1,-1'),
(7, 'Crabe',          40,  '-1,-1,-1');

-- Grades — Tofu
INSERT INTO `monster_grades` (`monster_id`, `grade`, `level`, `life`, `ap`, `mp`, `agility`, `xp_base`, `kamas_min`, `kamas_max`) VALUES
(1, 1,  1,  30, 6, 3, 10, 5,  0, 5),
(1, 2,  3,  45, 6, 3, 20, 10, 0, 8),
(1, 3,  5,  65, 7, 3, 35, 18, 1, 12);

-- Grades — Larve Blanche
INSERT INTO `monster_grades` (`monster_id`, `grade`, `level`, `life`, `ap`, `mp`, `strength`, `xp_base`, `kamas_min`, `kamas_max`) VALUES
(2, 1,  2,  40, 6, 3, 5,  8,  0, 6),
(2, 2,  4,  60, 6, 4, 15, 15, 0, 10),
(2, 3,  7,  85, 7, 4, 30, 25, 1, 15);

-- Grades — Bouftou
INSERT INTO `monster_grades` (`monster_id`, `grade`, `level`, `life`, `ap`, `mp`, `strength`, `vitality`, `xp_base`, `kamas_min`, `kamas_max`) VALUES
(5, 1,  5,  80,  6, 4, 20, 0,  20, 1, 20),
(5, 2,  8,  120, 6, 4, 40, 0,  35, 2, 30),
(5, 3,  12, 170, 7, 4, 70, 30, 55, 5, 50);

-- Spawns démo (map 7411 — zone démo)
INSERT INTO `monster_spawns` (`map_id`, `cell_id`, `orientation`, `composition`) VALUES
(7411, 200, 1, '1~1,1~2'),
(7411, 350, 3, '2~1,2~1,2~2'),
(7411, 100, 5, '5~1');
