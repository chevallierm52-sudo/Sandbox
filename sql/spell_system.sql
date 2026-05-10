-- =============================================================================
-- spell_system.sql — Système de sorts Dofus 1.29
-- Tables : spell_templates, spell_levels
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Templates de sorts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `spell_templates` (
    `id`           INT          NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(100) NOT NULL DEFAULT '',
    `sprite`       VARCHAR(50)  NOT NULL DEFAULT '' COMMENT 'Nom du sprite Flash',
    `sprite_arg`   VARCHAR(50)  NOT NULL DEFAULT '' COMMENT 'Argument du sprite (frame, couleur…)',
    `description`  TEXT,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Niveaux de sorts (1 ligne par niveau 1-6)
--
-- effects_csv format par effet : effectId#diceNum#diceSide#bonus#zone;effectId#...
--   effectId : 91=dégâts feu, 92=neutre, 93=terre, 94=eau, 95=air, 108=soin, 135=boost PA…
--   zone     : C=cellule unique, X=croix, L=ligne, O=zone, A=anneau, Z=zigzag
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `spell_levels` (
    `spell_id`         INT          NOT NULL,
    `level`            TINYINT      NOT NULL DEFAULT 1,
    `ap_cost`          TINYINT      NOT NULL DEFAULT 3,
    `min_range`        TINYINT      NOT NULL DEFAULT 1,
    `max_range`        TINYINT      NOT NULL DEFAULT 1,
    `critical_hit`     TINYINT      NOT NULL DEFAULT 0  COMMENT 'Probabilité coup critique (1/N, 0=pas de CC)',
    `critical_fail`    TINYINT      NOT NULL DEFAULT 0  COMMENT 'Probabilité échec critique (1/N)',
    `line_only`        TINYINT(1)   NOT NULL DEFAULT 0,
    `line_of_sight`    TINYINT(1)   NOT NULL DEFAULT 1,
    `free_cell`        TINYINT(1)   NOT NULL DEFAULT 0,
    `modifiable_range` TINYINT(1)   NOT NULL DEFAULT 0,
    `max_per_turn`     TINYINT      NOT NULL DEFAULT 0  COMMENT '0 = illimité',
    `max_per_target`   TINYINT      NOT NULL DEFAULT 0,
    `cooldown`         TINYINT      NOT NULL DEFAULT 0  COMMENT 'Tours de recharge',
    `effects_csv`      TEXT                             COMMENT 'Effets normaux CSV',
    `cc_effects_csv`   TEXT                             COMMENT 'Effets coup critique CSV',
    PRIMARY KEY (`spell_id`, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Données démo — sorts de base par classe
-- -----------------------------------------------------------------------------

-- ── Iop ──────────────────────────────────────────────────────────────────────
INSERT INTO `spell_templates` (`id`, `name`, `sprite`, `sprite_arg`) VALUES
(1, 'Épée Céleste',    'sortceleste',   'iop1'),
(2, 'Torgnole',        'torgnole',      'iop2'),
(3, 'Déstabilisation', 'destabilise',   'iop3');

INSERT INTO `spell_levels` (`spell_id`, `level`, `ap_cost`, `min_range`, `max_range`, `critical_hit`, `effects_csv`, `cc_effects_csv`) VALUES
(1, 1, 4, 1, 1, 0,  '92#8#12#0#C',   '92#14#18#0#C'),
(1, 2, 4, 1, 1, 0,  '92#10#15#0#C',  '92#17#22#0#C'),
(1, 3, 4, 1, 1, 20, '92#13#19#0#C',  '92#20#28#0#C'),
(1, 4, 3, 1, 1, 20, '92#16#23#0#C',  '92#24#33#0#C'),
(1, 5, 3, 1, 1, 15, '92#20#28#0#C',  '92#30#40#0#C'),
(1, 6, 3, 1, 1, 10, '92#25#35#0#C',  '92#38#52#0#C'),

(2, 1, 3, 1, 1, 0,  '92#5#9#0#C',    '92#9#14#0#C'),
(2, 2, 3, 1, 1, 0,  '92#6#11#0#C',   '92#11#16#0#C'),
(2, 3, 3, 1, 1, 25, '92#8#13#0#C',   '92#13#20#0#C'),
(2, 4, 3, 1, 1, 25, '92#10#16#0#C',  '92#16#24#0#C'),
(2, 5, 2, 1, 1, 20, '92#12#19#0#C',  '92#20#29#0#C'),
(2, 6, 2, 1, 1, 15, '92#15#24#0#C',  '92#24#35#0#C');

-- ── Feu de vie (soin Eniripsa style) ─────────────────────────────────────────
INSERT INTO `spell_templates` (`id`, `name`, `sprite`, `sprite_arg`) VALUES
(4, 'Mot Stimulant',  'motstimulant',  'eni1'),
(5, 'Mot Ravivant',   'motravivant',   'eni2');

INSERT INTO `spell_levels` (`spell_id`, `level`, `ap_cost`, `min_range`, `max_range`, `modifiable_range`, `effects_csv`, `cc_effects_csv`) VALUES
(4, 1, 3, 1, 3, 0, '108#8#12#0#C',  '108#14#18#0#C'),
(4, 2, 3, 1, 3, 0, '108#10#15#0#C', '108#17#22#0#C'),
(4, 3, 3, 1, 4, 1, '108#13#19#0#C', '108#20#28#0#C'),
(4, 4, 3, 1, 4, 1, '108#16#23#0#C', '108#24#33#0#C'),
(4, 5, 2, 1, 5, 1, '108#20#28#0#C', '108#30#40#0#C'),
(4, 6, 2, 1, 6, 1, '108#25#35#0#C', '108#38#52#0#C'),

(5, 1, 4, 1, 4, 1, '108#15#22#0#C', '108#24#34#0#C'),
(5, 2, 4, 1, 4, 1, '108#18#26#0#C', '108#28#40#0#C'),
(5, 3, 4, 1, 5, 1, '108#22#32#0#C', '108#34#48#0#C'),
(5, 4, 3, 1, 5, 1, '108#27#38#0#C', '108#40#57#0#C'),
(5, 5, 3, 1, 6, 1, '108#33#46#0#C', '108#48#68#0#C'),
(5, 6, 3, 1, 6, 1, '108#40#55#0#C', '108#58#82#0#C');

-- ── Fléche percante (Cra) ─────────────────────────────────────────────────────
INSERT INTO `spell_templates` (`id`, `name`, `sprite`, `sprite_arg`) VALUES
(6, 'Flèche Percante', 'flechepercante', 'cra1');

INSERT INTO `spell_levels` (`spell_id`, `level`, `ap_cost`, `min_range`, `max_range`, `modifiable_range`, `line_only`, `effects_csv`, `cc_effects_csv`) VALUES
(6, 1, 3, 2, 6, 0, 1, '94#7#11#0#L',  '94#12#17#0#L'),
(6, 2, 3, 2, 7, 0, 1, '94#9#13#0#L',  '94#14#21#0#L'),
(6, 3, 3, 2, 7, 0, 1, '94#11#16#0#L', '94#18#25#0#L'),
(6, 4, 3, 2, 8, 1, 1, '94#14#20#0#L', '94#22#31#0#L'),
(6, 5, 3, 2, 8, 1, 1, '94#17#24#0#L', '94#27#38#0#L'),
(6, 6, 3, 2, 9, 1, 1, '94#21#29#0#L', '94#33#46#0#L');
