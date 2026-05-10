-- =============================================================================
-- bank_system.sql — Système de banque Dofus 1.29
-- Modification de la table accounts + nouvelle table bank_items
-- =============================================================================

-- Ajout du champ kamas banque à la table accounts existante
ALTER TABLE `accounts`
    ADD COLUMN IF NOT EXISTS `bank_kamas` BIGINT NOT NULL DEFAULT 0
        COMMENT 'Kamas stockés en banque';

-- -----------------------------------------------------------------------------
-- Items en banque (coffre de compte — partagé entre tous les personnages)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `bank_items` (
    `uid`           INT          NOT NULL AUTO_INCREMENT,
    `account_id`    INT          NOT NULL COMMENT 'accounts.id',
    `template_id`   INT          NOT NULL,
    `quantity`      INT          NOT NULL DEFAULT 1,
    `effects`       TEXT                  COMMENT 'Effets rollés : effectId#value#0#0,...',
    PRIMARY KEY (`uid`),
    INDEX `idx_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Drops de monstres (optionnel — complète DropTable.loadDefaults())
-- rate : probabilité sur 10 000 (5000 = 50%)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `monster_drops` (
    `id`            INT          NOT NULL AUTO_INCREMENT,
    `monster_id`    INT          NOT NULL COMMENT 'monster_templates.id',
    `item_id`       INT          NOT NULL COMMENT 'item_templates.id',
    `rate`          INT          NOT NULL DEFAULT 1000 COMMENT 'Sur 10000 (1000=10%)',
    `qty_min`       INT          NOT NULL DEFAULT 1,
    `qty_max`       INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    INDEX `idx_monster` (`monster_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Drops démo
INSERT INTO `monster_drops` (`monster_id`, `item_id`, `rate`, `qty_min`, `qty_max`) VALUES
(1, 7, 5000, 1, 2),   -- Tofu → Oreille de Tofu 50%
(2, 6, 3000, 1, 1),   -- Larve Blanche → Bois de Frêne 30%
(5, 6, 2000, 1, 3),   -- Bouftou → Bois de Frêne 20%
(5, 9, 1000, 1, 1);   -- Bouftou → Ceinture Féca 10%
