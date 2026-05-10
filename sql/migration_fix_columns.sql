-- =============================================================================
-- MIGRATION : Colonnes manquantes dans les tables existantes
-- =============================================================================
-- À exécuter UNE SEULE FOIS sur ta base de données Dofus 1.29.
-- Ces ALTER TABLE ajoutent les colonnes attendues par le code Java
-- sans toucher aux données déjà présentes.
--
-- Erreurs corrigées :
--   ItemsData  : Unknown column 'type_id'   in item_templates
--   SpellsData : Unknown column 'sprite_id' in spell_templates  (typo sprit_id → sprite_id corrigé en Java)
--   MonstersData: Unknown column 'alignment' in monster_templates
--   GuildsData : Unknown column 'experience' in guilds
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. item_templates — colonnes manquantes
-- ─────────────────────────────────────────────────────────────────────────────
--
-- Le code Java attend :
--   id, name, type_id, level, pods, price, gfx_id, effects
--
-- Si ta table s'appelle différemment pour ces colonnes, modifie les noms ci-dessous.
--

ALTER TABLE `item_templates`
    ADD COLUMN IF NOT EXISTS `type_id`  INT          NOT NULL DEFAULT 0    COMMENT 'Type d''objet (1=Amulette, 2=Anneau, 3=Ceinture, 4=Bottes, 5=Chapeau, 6=Cape…)',
    ADD COLUMN IF NOT EXISTS `pods`     INT          NOT NULL DEFAULT 1    COMMENT 'Poids de l''objet en grammes',
    ADD COLUMN IF NOT EXISTS `price`    BIGINT       NOT NULL DEFAULT 0    COMMENT 'Prix de vente PNJ de base',
    ADD COLUMN IF NOT EXISTS `gfx_id`   INT          NOT NULL DEFAULT 0    COMMENT 'ID du sprite Flash',
    ADD COLUMN IF NOT EXISTS `effects`  TEXT                 DEFAULT NULL  COMMENT 'Effets CSV : effectId,dice,min,max,special#…';

-- Si MySQL < 8.0 (pas de IF NOT EXISTS pour colonnes), utilise les lignes séparées ci-dessous
-- et commente le bloc ci-dessus :
--
-- ALTER TABLE `item_templates` ADD COLUMN `type_id` INT NOT NULL DEFAULT 0;
-- ALTER TABLE `item_templates` ADD COLUMN `pods`    INT NOT NULL DEFAULT 1;
-- ALTER TABLE `item_templates` ADD COLUMN `price`   BIGINT NOT NULL DEFAULT 0;
-- ALTER TABLE `item_templates` ADD COLUMN `gfx_id`  INT NOT NULL DEFAULT 0;
-- ALTER TABLE `item_templates` ADD COLUMN `effects` TEXT DEFAULT NULL;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. spell_templates — colonne manquante
-- ─────────────────────────────────────────────────────────────────────────────
--
-- Le code Java attend : id, name, sprite_id
-- (la faute de frappe "sprit_id" a été corrigée dans SpellsData.java)
--

ALTER TABLE `spell_templates`
    ADD COLUMN IF NOT EXISTS `sprite_id` INT NOT NULL DEFAULT 0 COMMENT 'ID du sprite Flash du sort';

-- MySQL < 8.0 :
-- ALTER TABLE `spell_templates` ADD COLUMN `sprite_id` INT NOT NULL DEFAULT 0;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. monster_templates — colonnes manquantes
-- ─────────────────────────────────────────────────────────────────────────────
--
-- Le code Java attend : id, name, gfx_id, race, alignment
--

ALTER TABLE `monster_templates`
    ADD COLUMN IF NOT EXISTS `gfx_id`    INT          NOT NULL DEFAULT 0 COMMENT 'ID du sprite Flash',
    ADD COLUMN IF NOT EXISTS `race`      INT          NOT NULL DEFAULT 0 COMMENT 'Race du monstre (famille)',
    ADD COLUMN IF NOT EXISTS `alignment` INT          NOT NULL DEFAULT 0 COMMENT '0=neutre, 1=Bontarien, 2=Maléfique';

-- MySQL < 8.0 :
-- ALTER TABLE `monster_templates` ADD COLUMN `gfx_id`    INT NOT NULL DEFAULT 0;
-- ALTER TABLE `monster_templates` ADD COLUMN `race`      INT NOT NULL DEFAULT 0;
-- ALTER TABLE `monster_templates` ADD COLUMN `alignment` INT NOT NULL DEFAULT 0;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. monster_grades — colonnes manquantes pour les drops
-- ─────────────────────────────────────────────────────────────────────────────
--
-- Le code Java attend aussi : kamas_min, kamas_max (en plus de xp)
--

ALTER TABLE `monster_grades`
    ADD COLUMN IF NOT EXISTS `xp`        BIGINT NOT NULL DEFAULT 0  COMMENT 'XP de base accordée par grade',
    ADD COLUMN IF NOT EXISTS `kamas_min` INT    NOT NULL DEFAULT 0  COMMENT 'Kamas min droppés',
    ADD COLUMN IF NOT EXISTS `kamas_max` INT    NOT NULL DEFAULT 0  COMMENT 'Kamas max droppés';

-- MySQL < 8.0 :
-- ALTER TABLE `monster_grades` ADD COLUMN `xp`        BIGINT NOT NULL DEFAULT 0;
-- ALTER TABLE `monster_grades` ADD COLUMN `kamas_min` INT    NOT NULL DEFAULT 0;
-- ALTER TABLE `monster_grades` ADD COLUMN `kamas_max` INT    NOT NULL DEFAULT 0;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. guilds — colonne manquante
-- ─────────────────────────────────────────────────────────────────────────────
--
-- Le code Java attend : id, name, emblem, level, experience
--

ALTER TABLE `guilds`
    ADD COLUMN IF NOT EXISTS `emblem`     VARCHAR(128) DEFAULT '' COMMENT 'Emblème sérialisé (bg/mg/fg)',
    ADD COLUMN IF NOT EXISTS `experience` BIGINT       NOT NULL DEFAULT 0 COMMENT 'XP totale de la guilde';

-- MySQL < 8.0 :
-- ALTER TABLE `guilds` ADD COLUMN `emblem`     VARCHAR(128) DEFAULT '';
-- ALTER TABLE `guilds` ADD COLUMN `experience` BIGINT NOT NULL DEFAULT 0;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. guild_members — colonnes manquantes
-- ─────────────────────────────────────────────────────────────────────────────
--
-- Le code Java attend :
--   guild_id, character_id, character_name, rank, rights, experience, level, breed, gender
--

ALTER TABLE `guild_members`
    ADD COLUMN IF NOT EXISTS `character_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Nom du personnage (cache)',
    ADD COLUMN IF NOT EXISTS `rank`           INT         NOT NULL DEFAULT 0  COMMENT '0=Aspirant, 1=Maître de Guilde',
    ADD COLUMN IF NOT EXISTS `rights`         INT         NOT NULL DEFAULT 0  COMMENT 'Bitmask des droits',
    ADD COLUMN IF NOT EXISTS `experience`     BIGINT      NOT NULL DEFAULT 0  COMMENT 'XP donnée à la guilde',
    ADD COLUMN IF NOT EXISTS `level`          INT         NOT NULL DEFAULT 1  COMMENT 'Niveau du personnage (cache)',
    ADD COLUMN IF NOT EXISTS `breed`          INT         NOT NULL DEFAULT 1  COMMENT 'Race du personnage (cache)',
    ADD COLUMN IF NOT EXISTS `gender`         TINYINT     NOT NULL DEFAULT 0  COMMENT '0=masculin, 1=féminin';

-- MySQL < 8.0 :
-- ALTER TABLE `guild_members` ADD COLUMN `character_name` VARCHAR(64) NOT NULL DEFAULT '';
-- ALTER TABLE `guild_members` ADD COLUMN `rank`           INT         NOT NULL DEFAULT 0;
-- ALTER TABLE `guild_members` ADD COLUMN `rights`         INT         NOT NULL DEFAULT 0;
-- ALTER TABLE `guild_members` ADD COLUMN `experience`     BIGINT      NOT NULL DEFAULT 0;
-- ALTER TABLE `guild_members` ADD COLUMN `level`          INT         NOT NULL DEFAULT 1;
-- ALTER TABLE `guild_members` ADD COLUMN `breed`          INT         NOT NULL DEFAULT 1;
-- ALTER TABLE `guild_members` ADD COLUMN `gender`         TINYINT     NOT NULL DEFAULT 0;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. Tables nécessaires si elles n'existent pas encore
-- ─────────────────────────────────────────────────────────────────────────────

-- Items des personnages (inventaire)
CREATE TABLE IF NOT EXISTS `character_items` (
    `uid`            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `owner_id`       INT          NOT NULL,
    `template_id`    INT          NOT NULL,
    `quantity`       INT          NOT NULL DEFAULT 1,
    `position`       INT          NOT NULL DEFAULT -1 COMMENT '-1=sac, 1-10=équipé',
    `rolled_effects` TEXT                  DEFAULT NULL COMMENT 'Effets rollés : effectId,d,min,max,s#…',
    INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Items en banque
CREATE TABLE IF NOT EXISTS `bank_items` (
    `id`             INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `account_id`     INT          NOT NULL,
    `template_id`    INT          NOT NULL,
    `quantity`       INT          NOT NULL DEFAULT 1,
    INDEX `idx_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Colonne bank_kamas dans accounts (si manquante)
ALTER TABLE `accounts`
    ADD COLUMN IF NOT EXISTS `bank_kamas` BIGINT NOT NULL DEFAULT 0 COMMENT 'Kamas stockés en banque';

-- MySQL < 8.0 :
-- ALTER TABLE `accounts` ADD COLUMN `bank_kamas` BIGINT NOT NULL DEFAULT 0;

-- Tables de sorts (niveaux)
CREATE TABLE IF NOT EXISTS `spell_levels` (
    `spell_id`       INT          NOT NULL,
    `level`          TINYINT      NOT NULL,
    `ap_cost`        INT          NOT NULL DEFAULT 3,
    `min_range`      INT          NOT NULL DEFAULT 1,
    `max_range`      INT          NOT NULL DEFAULT 1,
    `line_only`      TINYINT(1)   NOT NULL DEFAULT 0,
    `los`            TINYINT(1)   NOT NULL DEFAULT 1  COMMENT 'Line of sight',
    `free_cell`      TINYINT(1)   NOT NULL DEFAULT 0,
    `crit_chance`    INT          NOT NULL DEFAULT 0  COMMENT '1/N probabilité critique (0=jamais)',
    `fail_chance`    INT          NOT NULL DEFAULT 0  COMMENT '1/N probabilité d''échec',
    `cooldown`       INT          NOT NULL DEFAULT 0,
    `max_per_turn`   INT          NOT NULL DEFAULT 0,
    `max_per_target` INT          NOT NULL DEFAULT 0,
    `effects`        TEXT                  DEFAULT NULL COMMENT 'Effets normaux CSV',
    `crit_effects`   TEXT                  DEFAULT NULL COMMENT 'Effets critique CSV',
    PRIMARY KEY (`spell_id`, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Spawns de monstres
CREATE TABLE IF NOT EXISTS `monster_spawns` (
    `id`             INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `map_id`         INT          NOT NULL,
    `monster_id`     INT          NOT NULL,
    `grade`          INT          NOT NULL DEFAULT 1,
    `cell_id`        SMALLINT     NOT NULL DEFAULT 200,
    `orientation`    INT          NOT NULL DEFAULT 2 COMMENT 'Ordinal EOrientation (2=SOUTH)',
    `qty`            INT          NOT NULL DEFAULT 1,
    INDEX `idx_map` (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Drops de monstres
CREATE TABLE IF NOT EXISTS `monster_drops` (
    `id`             INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `monster_id`     INT          NOT NULL,
    `template_id`    INT          NOT NULL COMMENT 'Item droppé',
    `rate`           INT          NOT NULL DEFAULT 5000 COMMENT 'Taux sur 10000 (5000=50%)',
    `qty_min`        INT          NOT NULL DEFAULT 1,
    `qty_max`        INT          NOT NULL DEFAULT 1,
    INDEX `idx_monster` (`monster_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Recettes d'artisanat
CREATE TABLE IF NOT EXISTS `craft_recipes` (
    `id`                 INT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `job_id`             INT      NOT NULL,
    `level_required`     INT      NOT NULL DEFAULT 1,
    `result_template_id` INT      NOT NULL,
    `result_qty`         INT      NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `craft_ingredients` (
    `id`                    INT  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `recipe_id`             INT  NOT NULL,
    `ingredient_template_id` INT NOT NULL,
    `quantity`              INT  NOT NULL DEFAULT 1,
    INDEX `idx_recipe` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `character_jobs` (
    `character_id` INT     NOT NULL,
    `job_id`       INT     NOT NULL,
    `level`        INT     NOT NULL DEFAULT 1,
    `experience`   BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (`character_id`, `job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
