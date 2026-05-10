-- =============================================================================
-- craft_system.sql — Système d'artisanat Dofus 1.29
-- Tables : craft_recipes, craft_ingredients, character_jobs
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Recettes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `craft_recipes` (
    `id`                 INT      NOT NULL AUTO_INCREMENT,
    `job_id`             TINYINT  NOT NULL          COMMENT 'CraftRecipe.JobType.id',
    `level_required`     TINYINT  NOT NULL DEFAULT 1,
    `result_template_id` INT      NOT NULL,
    `result_qty`         INT      NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    INDEX `idx_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Ingrédients (N-N avec recipes)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `craft_ingredients` (
    `id`                    INT NOT NULL AUTO_INCREMENT,
    `recipe_id`             INT NOT NULL,
    `ingredient_template_id` INT NOT NULL,
    `quantity`              INT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    INDEX `idx_recipe` (`recipe_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Niveaux de métier par personnage
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `character_jobs` (
    `character_id`  INT     NOT NULL,
    `job_id`        TINYINT NOT NULL,
    `level`         TINYINT NOT NULL DEFAULT 1,
    `xp`            BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (`character_id`, `job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Données démo
-- Boulanger (job_id=7) : 2 Oreilles de Tofu + 1 Bois de Frêne → 1 Pain d'orge
-- -----------------------------------------------------------------------------
INSERT INTO `craft_recipes` (`id`, `job_id`, `level_required`, `result_template_id`, `result_qty`) VALUES
(1, 7, 1, 5, 1),   -- Boulanger → Pain d'orge
(2, 3, 1, 6, 2);   -- Bûcheron → Bois de Frêne ×2 (recette fictive démo)

INSERT INTO `craft_ingredients` (`recipe_id`, `ingredient_template_id`, `quantity`) VALUES
(1, 7, 2),   -- 2 Oreilles de Tofu
(1, 6, 1),   -- 1 Bois de Frêne
(2, 7, 3);   -- 3 Oreilles de Tofu
