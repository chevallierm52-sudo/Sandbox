-- =============================================================================
-- MIGRATION COMPATIBLE MySQL 5.x / 8.x
-- Ajoute les colonnes manquantes via information_schema (pas de IF NOT EXISTS)
-- =============================================================================
-- Usage : exécute ce fichier dans ton client MySQL/phpMyAdmin.
-- Les colonnes qui existent déjà sont ignorées sans erreur.
-- =============================================================================

SET @db = DATABASE();

-- ─────────────────────────────────────────────────────────────────────────────
-- Procédure utilitaire : ajoute une colonne si elle n'existe pas
-- ─────────────────────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS add_col;
DELIMITER $$
CREATE PROCEDURE add_col(
    IN tbl  VARCHAR(64),
    IN col  VARCHAR(64),
    IN def  TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = tbl
          AND COLUMN_NAME  = col
    ) THEN
        SET @sql = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('[OK] Colonne ajoutée : ', tbl, '.', col) AS info;
    ELSE
        SELECT CONCAT('[OK] Déjà présente : ', tbl, '.', col) AS info;
    END IF;
END$$
DELIMITER ;

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. item_templates
-- ─────────────────────────────────────────────────────────────────────────────
CALL add_col('item_templates', 'type_id',  'INT NOT NULL DEFAULT 0 COMMENT ''Type objet''');
CALL add_col('item_templates', 'level',    'INT NOT NULL DEFAULT 1 COMMENT ''Niveau requis''');
CALL add_col('item_templates', 'pods',     'INT NOT NULL DEFAULT 1 COMMENT ''Poids (g)''');
CALL add_col('item_templates', 'price',    'BIGINT NOT NULL DEFAULT 0 COMMENT ''Prix PNJ''');
CALL add_col('item_templates', 'gfx_id',   'INT NOT NULL DEFAULT 0 COMMENT ''Sprite Flash''');
CALL add_col('item_templates', 'effects',  'TEXT DEFAULT NULL COMMENT ''Effets CSV''');

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. spell_templates  (le Java utilisait "sprit_id" — corrigé en "sprite_id")
-- ─────────────────────────────────────────────────────────────────────────────
CALL add_col('spell_templates', 'sprite_id', 'INT NOT NULL DEFAULT 0 COMMENT ''Sprite Flash du sort''');

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. monster_templates
-- ─────────────────────────────────────────────────────────────────────────────
CALL add_col('monster_templates', 'gfx_id',    'INT NOT NULL DEFAULT 0 COMMENT ''Sprite Flash''');
CALL add_col('monster_templates', 'race',      'INT NOT NULL DEFAULT 0 COMMENT ''Famille monstre''');
CALL add_col('monster_templates', 'alignment', 'INT NOT NULL DEFAULT 0 COMMENT ''0=neutre 1=Bonta 2=Brak''');

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. monster_grades  (XP + kamas drops)
-- ─────────────────────────────────────────────────────────────────────────────
CALL add_col('monster_grades', 'xp',        'BIGINT NOT NULL DEFAULT 0 COMMENT ''XP accordée''');
CALL add_col('monster_grades', 'kamas_min', 'INT NOT NULL DEFAULT 0 COMMENT ''Kamas min''');
CALL add_col('monster_grades', 'kamas_max', 'INT NOT NULL DEFAULT 0 COMMENT ''Kamas max''');

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. guilds
-- ─────────────────────────────────────────────────────────────────────────────
CALL add_col('guilds', 'emblem',     'VARCHAR(128) DEFAULT '''' COMMENT ''Emblème sérialisé''');
CALL add_col('guilds', 'experience', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''XP guilde''');

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. guild_members
-- ─────────────────────────────────────────────────────────────────────────────
CALL add_col('guild_members', 'character_name', 'VARCHAR(64) NOT NULL DEFAULT '''' COMMENT ''Nom (cache)''');
CALL add_col('guild_members', 'rank',           'INT NOT NULL DEFAULT 0');
CALL add_col('guild_members', 'rights',         'INT NOT NULL DEFAULT 0');
CALL add_col('guild_members', 'experience',     'BIGINT NOT NULL DEFAULT 0');
CALL add_col('guild_members', 'level',          'INT NOT NULL DEFAULT 1');
CALL add_col('guild_members', 'breed',          'INT NOT NULL DEFAULT 1');
CALL add_col('guild_members', 'gender',         'TINYINT NOT NULL DEFAULT 0');

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. accounts — colonne banque
-- ─────────────────────────────────────────────────────────────────────────────
CALL add_col('accounts', 'bank_kamas', 'BIGINT NOT NULL DEFAULT 0 COMMENT ''Kamas en banque''');

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Tables nécessaires si absentes
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS `character_items` (
    `uid`            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `owner_id`       INT         NOT NULL,
    `template_id`    INT         NOT NULL,
    `quantity`       INT         NOT NULL DEFAULT 1,
    `position`       INT         NOT NULL DEFAULT -1 COMMENT '-1=sac, 1-10=slot équipement',
    `rolled_effects` TEXT                 DEFAULT NULL,
    INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `bank_items` (
    `id`             INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `account_id`     INT         NOT NULL,
    `template_id`    INT         NOT NULL,
    `quantity`       INT         NOT NULL DEFAULT 1,
    INDEX `idx_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `spell_levels` (
    `spell_id`       INT         NOT NULL,
    `level`          TINYINT     NOT NULL,
    `ap_cost`        INT         NOT NULL DEFAULT 3,
    `min_range`      INT         NOT NULL DEFAULT 1,
    `max_range`      INT         NOT NULL DEFAULT 1,
    `line_only`      TINYINT(1)  NOT NULL DEFAULT 0,
    `los`            TINYINT(1)  NOT NULL DEFAULT 1,
    `free_cell`      TINYINT(1)  NOT NULL DEFAULT 0,
    `crit_chance`    INT         NOT NULL DEFAULT 0,
    `fail_chance`    INT         NOT NULL DEFAULT 0,
    `cooldown`       INT         NOT NULL DEFAULT 0,
    `max_per_turn`   INT         NOT NULL DEFAULT 0,
    `max_per_target` INT         NOT NULL DEFAULT 0,
    `effects`        TEXT                 DEFAULT NULL,
    `crit_effects`   TEXT                 DEFAULT NULL,
    PRIMARY KEY (`spell_id`, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `monster_spawns` (
    `id`          INT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `map_id`      INT      NOT NULL,
    `monster_id`  INT      NOT NULL,
    `grade`       INT      NOT NULL DEFAULT 1,
    `cell_id`     SMALLINT NOT NULL DEFAULT 200,
    `orientation` INT      NOT NULL DEFAULT 2,
    `qty`         INT      NOT NULL DEFAULT 1,
    INDEX `idx_map` (`map_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `monster_drops` (
    `id`          INT  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `monster_id`  INT  NOT NULL,
    `template_id` INT  NOT NULL,
    `rate`        INT  NOT NULL DEFAULT 5000 COMMENT 'Sur 10000',
    `qty_min`     INT  NOT NULL DEFAULT 1,
    `qty_max`     INT  NOT NULL DEFAULT 1,
    INDEX `idx_monster` (`monster_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `craft_recipes` (
    `id`                 INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `job_id`             INT NOT NULL,
    `level_required`     INT NOT NULL DEFAULT 1,
    `result_template_id` INT NOT NULL,
    `result_qty`         INT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `craft_ingredients` (
    `id`                     INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `recipe_id`              INT NOT NULL,
    `ingredient_template_id` INT NOT NULL,
    `quantity`               INT NOT NULL DEFAULT 1,
    INDEX `idx_recipe` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `character_jobs` (
    `character_id` INT    NOT NULL,
    `job_id`       INT    NOT NULL,
    `level`        INT    NOT NULL DEFAULT 1,
    `experience`   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`character_id`, `job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────────────────────────────────────────────────────────────────
-- Nettoyage
-- ─────────────────────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS add_col;

SELECT 'Migration terminée avec succès !' AS statut;
