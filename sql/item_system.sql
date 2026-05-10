-- =============================================================================
-- item_system.sql — Système d'inventaire Dofus 1.29
-- Tables : item_templates, character_items
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Templates d'objets
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `item_templates` (
    `id`          INT          NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(100) NOT NULL DEFAULT '',
    `type`        TINYINT      NOT NULL DEFAULT 0   COMMENT '0=divers 1=arme 2=armure 3=anneau 4=amulette 5=ceinture 6=bottes 7=chapeau 8=manteau 9=bouclier 10=ressource 11=consommable',
    `level`       SMALLINT     NOT NULL DEFAULT 1,
    `weight`      INT          NOT NULL DEFAULT 0   COMMENT 'Poids en pods',
    `price`       INT          NOT NULL DEFAULT 0   COMMENT 'Prix de vente marchand',
    `two_handed`  TINYINT(1)   NOT NULL DEFAULT 0,
    `eth`         TINYINT(1)   NOT NULL DEFAULT 0   COMMENT '1 = éthéré (disparaît à l\'équipement)',
    `max_per_acc` TINYINT      NOT NULL DEFAULT 0   COMMENT '0 = illimité',
    `effects`     TEXT                              COMMENT 'CSV : effectId#min#max#special,...',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- -----------------------------------------------------------------------------
-- Données démo
-- -----------------------------------------------------------------------------
INSERT INTO `item_templates` (`id`, `name`, `type`, `level`, `weight`, `price`, `effects`) VALUES
-- Épée de bois (arme niveau 1)
(1,  'Épée de bois',       1,  1,  20, 10,  '9#1#5#0'),
-- Baguette de néophyte (arme niveau 1, bâton de mage)
(2,  'Baguette de néophyte', 1, 1, 10, 8,   '9#1#3#0'),
-- Amulette de Pandala (amulette niveau 5)
(3,  'Amulette de Pandala', 4, 5,  2,  50,  '111#5#10#0,112#3#6#0'),
-- Chapeau de Tofu (chapeau niveau 1)
(4,  'Chapeau de Tofu',    7,  1,  5,  15,  '125#1#3#0'),
-- Pain d'orge (consommable — soin)
(5,  'Pain d\'orge',       11, 1,  1,  2,   '108#10#10#0'),
-- Bois de Frêne (ressource)
(6,  'Bois de Frêne',      10, 1,  2,  1,   ''),
-- Oreille de Tofu (ressource craft)
(7,  'Oreille de Tofu',    10, 1,  1,  2,   ''),
-- Anneau de Larve Blanche (anneau niveau 3)
(8,  'Anneau de Larve',    3,  3,  1,  30,  '119#1#2#0'),
-- Ceinture Féca (ceinture niveau 1)
(9,  'Ceinture Féca',      5,  1,  3,  12,  '125#2#5#0'),
-- Bouclier de bois (bouclier niveau 1)
(10, 'Bouclier de bois',   9,  1,  30, 20,  '105#0#10#0');

-- -----------------------------------------------------------------------------
-- Inventaires des personnages
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `character_items` (
    `uid`           INT          NOT NULL AUTO_INCREMENT,
    `owner`         INT          NOT NULL            COMMENT 'characters.id',
    `template_id`   INT          NOT NULL,
    `quantity`      INT          NOT NULL DEFAULT 1,
    `position`      TINYINT      NOT NULL DEFAULT 63 COMMENT '63=sac, 1-13=slots équipement',
    `effects`       TEXT                             COMMENT 'Effets rollés : effectId#value#0#0,...',
    PRIMARY KEY (`uid`),
    INDEX `idx_owner` (`owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exemple : personnage id=1 commence avec une épée de bois dans le sac
INSERT INTO `character_items` (`owner`, `template_id`, `quantity`, `position`, `effects`) VALUES
(1, 1, 1, 63, '9#3#0#0');
