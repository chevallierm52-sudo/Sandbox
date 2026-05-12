/*
 Navicat Premium Dump SQL

 Source Server         : Dofus
 Source Server Type    : MySQL
 Source Server Version : 100432 (10.4.32-MariaDB)
 Source Host           : localhost:3306
 Source Schema         : ancestra_game

 Target Server Type    : MySQL
 Target Server Version : 100432 (10.4.32-MariaDB)
 File Encoding         : 65001

 Date: 12/05/2026 14:06:13
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for interactive_objects_data
-- ----------------------------
DROP TABLE IF EXISTS `interactive_objects_data`;
CREATE TABLE `interactive_objects_data`  (
  `id` int NOT NULL,
  `respawn` int NOT NULL DEFAULT 10000,
  `duration` int NOT NULL DEFAULT 1500,
  `unknow` int NOT NULL DEFAULT 4,
  `walkable` int NOT NULL DEFAULT 1,
  `Name IO` text CHARACTER SET latin1 COLLATE latin1_swedish_ci NOT NULL,
  UNIQUE INDEX `id`(`id`) USING BTREE
) ENGINE = MyISAM CHARACTER SET = latin1 COLLATE = latin1_swedish_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of interactive_objects_data
-- ----------------------------
INSERT INTO `interactive_objects_data` VALUES (7519, 120000, 1500, 4, 0, 'Puits');
INSERT INTO `interactive_objects_data` VALUES (7515, 180000, -1, 4, 0, 'Orge');
INSERT INTO `interactive_objects_data` VALUES (7511, 180000, -1, 4, 0, 'Bl');
INSERT INTO `interactive_objects_data` VALUES (7517, 180000, -1, 4, 0, 'Avoine');
INSERT INTO `interactive_objects_data` VALUES (7512, 180000, -1, 4, 0, 'Houblon');
INSERT INTO `interactive_objects_data` VALUES (7513, 180000, -1, 4, 0, 'Lin');
INSERT INTO `interactive_objects_data` VALUES (7516, 180000, -1, 4, 0, 'Seigle');
INSERT INTO `interactive_objects_data` VALUES (7550, 180000, -1, 4, 0, 'Riz');
INSERT INTO `interactive_objects_data` VALUES (7518, 180000, -1, 4, 0, 'Malt');
INSERT INTO `interactive_objects_data` VALUES (7500, 180000, -1, 4, 0, 'Fr?ne');
INSERT INTO `interactive_objects_data` VALUES (7536, 180000, -1, 4, 0, 'Edelweiss');
INSERT INTO `interactive_objects_data` VALUES (7501, 180000, -1, 4, 0, 'Ch?taignier');
INSERT INTO `interactive_objects_data` VALUES (7502, 180000, -1, 4, 0, 'Noyer');
INSERT INTO `interactive_objects_data` VALUES (7503, 180000, -1, 4, 0, 'Ch?ne');
INSERT INTO `interactive_objects_data` VALUES (7542, 180000, -1, 4, 0, 'Oliviolet');
INSERT INTO `interactive_objects_data` VALUES (7541, 180000, -1, 4, 0, 'Bombu');
INSERT INTO `interactive_objects_data` VALUES (7504, 180000, -1, 4, 0, 'Erable');
INSERT INTO `interactive_objects_data` VALUES (7553, 180000, -1, 4, 0, 'Bambou');
INSERT INTO `interactive_objects_data` VALUES (7505, 180000, -1, 4, 0, 'If');
INSERT INTO `interactive_objects_data` VALUES (7506, 180000, -1, 4, 0, 'Merisier');
INSERT INTO `interactive_objects_data` VALUES (7507, 180000, -1, 4, 0, 'Eb?ne');
INSERT INTO `interactive_objects_data` VALUES (7557, 180000, -1, 4, 0, 'Kalyptus');
INSERT INTO `interactive_objects_data` VALUES (7554, 180000, -1, 4, 0, 'Bambou Sombre');
INSERT INTO `interactive_objects_data` VALUES (7508, 180000, -1, 4, 0, 'Charme');
INSERT INTO `interactive_objects_data` VALUES (7509, 180000, -1, 4, 0, 'Orme');
INSERT INTO `interactive_objects_data` VALUES (7552, 180000, -1, 4, 0, 'Bambou Sacr');
INSERT INTO `interactive_objects_data` VALUES (7534, 180000, -1, 4, 0, 'Menthe');
INSERT INTO `interactive_objects_data` VALUES (7535, 180000, -1, 4, 0, 'Orchid?e Freyesque');
INSERT INTO `interactive_objects_data` VALUES (7533, 180000, -1, 4, 0, 'Tr?fle a 5');
INSERT INTO `interactive_objects_data` VALUES (7551, 180000, -1, 4, 0, 'Graine de Pandouille');
INSERT INTO `interactive_objects_data` VALUES (7530, 180000, -1, 4, 0, 'Petits poissons mer');
INSERT INTO `interactive_objects_data` VALUES (7529, 180000, -1, 4, 0, 'Petits possons rivi');
INSERT INTO `interactive_objects_data` VALUES (7531, 180000, -1, 4, 0, 'Poisson mer');
INSERT INTO `interactive_objects_data` VALUES (7532, 180000, -1, 4, 0, 'Poissons rivi');
INSERT INTO `interactive_objects_data` VALUES (7539, 180000, -1, 4, 0, 'G?ant poissons rivi');
INSERT INTO `interactive_objects_data` VALUES (7538, 180000, -1, 4, 0, 'gros poisson mer');
INSERT INTO `interactive_objects_data` VALUES (7540, 180000, -1, 4, 0, 'G?ant poissons mer');
INSERT INTO `interactive_objects_data` VALUES (7537, 180000, -1, 4, 0, 'Gros poisson rivi?re');
INSERT INTO `interactive_objects_data` VALUES (7555, 180000, -1, 4, 0, 'Dolomite');
INSERT INTO `interactive_objects_data` VALUES (7544, 10000, -1, 4, 0, 'Pichon');
INSERT INTO `interactive_objects_data` VALUES (7524, 180000, -1, 4, 0, 'Mangan?se');
INSERT INTO `interactive_objects_data` VALUES (7522, 180000, -1, 4, 0, 'Pierre Cuivr');
INSERT INTO `interactive_objects_data` VALUES (7526, 180000, -1, 4, 0, 'Argent');
INSERT INTO `interactive_objects_data` VALUES (7523, 180000, -1, 4, 0, 'Bronze');
INSERT INTO `interactive_objects_data` VALUES (7528, 180000, -1, 4, 0, 'Pierre de Bauxite');
INSERT INTO `interactive_objects_data` VALUES (7556, 180000, -1, 4, 0, 'Silicate');
INSERT INTO `interactive_objects_data` VALUES (7520, 180000, -1, 4, 0, 'Fer');
INSERT INTO `interactive_objects_data` VALUES (7543, 180000, -1, 4, 0, 'Ombre ?trange');
INSERT INTO `interactive_objects_data` VALUES (7521, 180000, -1, 4, 0, 'Etain');
INSERT INTO `interactive_objects_data` VALUES (6766, -1, 1500, 4, 0, 'Enclos');
INSERT INTO `interactive_objects_data` VALUES (6767, -1, 1500, 4, 0, 'Enclos');
INSERT INTO `interactive_objects_data` VALUES (6763, -1, 1500, 4, 0, 'Enclos');
INSERT INTO `interactive_objects_data` VALUES (6772, -1, 1500, 4, 0, 'Enclos');
INSERT INTO `interactive_objects_data` VALUES (7000, -1, 1500, 4, 0, 'Zaap');
INSERT INTO `interactive_objects_data` VALUES (7007, -1, 1500, 4, 1, 'Machine : Paysan');
INSERT INTO `interactive_objects_data` VALUES (7026, -1, 1500, 4, 0, 'Zaap');
INSERT INTO `interactive_objects_data` VALUES (7029, -1, 1500, 4, 0, 'Zaap');
INSERT INTO `interactive_objects_data` VALUES (4287, -1, 1500, 4, 0, 'Zaap');
INSERT INTO `interactive_objects_data` VALUES (7024, -1, 1500, 4, 1, 'Machine : P?cheur');
INSERT INTO `interactive_objects_data` VALUES (7025, -1, 1500, 4, 1, 'Machine : Boucher');
INSERT INTO `interactive_objects_data` VALUES (7022, -1, 1500, 4, 1, 'Machine : Poissonier');
INSERT INTO `interactive_objects_data` VALUES (7023, -1, 1500, 4, 1, 'Machine : Chasseur');
INSERT INTO `interactive_objects_data` VALUES (7352, -1, 1500, 4, 0, 'Poubelle');
INSERT INTO `interactive_objects_data` VALUES (7002, -1, 1500, 4, 1, 'Moule');
INSERT INTO `interactive_objects_data` VALUES (7003, -1, 1500, 4, 1, 'Machine : Bucheron');
INSERT INTO `interactive_objects_data` VALUES (7019, -1, 1500, 4, 1, 'Machine : Alchi');
INSERT INTO `interactive_objects_data` VALUES (7012, -1, 1500, 4, 1, 'Machine : Forgeron');
INSERT INTO `interactive_objects_data` VALUES (7039, -1, 1500, 4, 1, 'Machine : Bricoleur');
INSERT INTO `interactive_objects_data` VALUES (7001, -1, 1500, 4, 1, 'Machine : Boulanger');
INSERT INTO `interactive_objects_data` VALUES (7013, 10000, 1500, 4, 1, 'Machine : Sculteur');
INSERT INTO `interactive_objects_data` VALUES (7009, -1, 1500, 4, 1, 'Atelier');
INSERT INTO `interactive_objects_data` VALUES (7008, -1, 1500, 4, 1, 'Atelier');
INSERT INTO `interactive_objects_data` VALUES (7010, -1, 1500, 4, 1, 'Atelier');
INSERT INTO `interactive_objects_data` VALUES (7015, -1, 1500, 4, 1, 'Machine : Tailleur');
INSERT INTO `interactive_objects_data` VALUES (7014, -1, 1500, 4, 1, 'Machine : Tailleur');
INSERT INTO `interactive_objects_data` VALUES (7016, -1, 1500, 4, 1, 'Machine : Tailleur');
INSERT INTO `interactive_objects_data` VALUES (7011, -1, 1500, 4, 1, 'Machine : Cordonier');
INSERT INTO `interactive_objects_data` VALUES (7020, -1, 1500, 4, 1, 'FM : CaC');
INSERT INTO `interactive_objects_data` VALUES (7006, -1, 1500, 4, 1, 'Table a patate');
INSERT INTO `interactive_objects_data` VALUES (7549, 10000, -1, 4, 0, 'P?cher Canard (Foire)');
INSERT INTO `interactive_objects_data` VALUES (7037, -1, 1500, 4, 1, 'Table Magique');
INSERT INTO `interactive_objects_data` VALUES (7027, -1, 1500, 4, 1, 'Machine : Bouclier ');
INSERT INTO `interactive_objects_data` VALUES (7031, 10000, 1500, 4, 0, 'Zaapi');
INSERT INTO `interactive_objects_data` VALUES (7030, -1, 1500, 4, 0, 'Zaapi');
INSERT INTO `interactive_objects_data` VALUES (6700, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6701, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6702, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6703, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6704, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6705, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6706, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6707, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6708, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6709, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6710, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6711, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6712, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6713, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6714, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6715, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6716, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6717, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6718, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6719, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6720, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6721, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6722, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6723, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6724, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6725, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6726, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6729, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6730, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6731, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6732, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6733, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6734, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6735, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6736, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6737, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6738, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6739, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6740, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6741, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6742, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6743, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6744, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6745, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6746, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6747, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6748, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6754, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6756, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6757, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6758, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6759, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6760, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6761, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6762, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6764, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6765, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6768, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6769, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6770, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6771, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6773, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6774, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6775, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (6776, -1, 1500, 4, 0, 'Porte');
INSERT INTO `interactive_objects_data` VALUES (7350, -1, 1500, 4, 1, 'Coffre');
INSERT INTO `interactive_objects_data` VALUES (7351, -1, 1500, 4, 1, 'Coffre');
INSERT INTO `interactive_objects_data` VALUES (7353, -1, 1500, 4, 1, 'Coffre');
INSERT INTO `interactive_objects_data` VALUES (7038, -1, 1500, 4, 1, 'Atelier Magique');
INSERT INTO `interactive_objects_data` VALUES (7017, -1, 1500, 4, 1, 'Marmite');
INSERT INTO `interactive_objects_data` VALUES (7035, -1, 1500, 4, 1, 'Liste artisans');
INSERT INTO `interactive_objects_data` VALUES (7036, -1, 1500, 4, 1, 'Machine ? Coudre Magique');
INSERT INTO `interactive_objects_data` VALUES (7021, -1, 1500, 4, 1, 'Concasseur Munster');
INSERT INTO `interactive_objects_data` VALUES (7041, -1, 1500, 4, 1, 'Levier');
INSERT INTO `interactive_objects_data` VALUES (7042, -1, 1500, 4, 1, 'Levier');
INSERT INTO `interactive_objects_data` VALUES (7043, -1, 1500, 4, 1, 'Levier');
INSERT INTO `interactive_objects_data` VALUES (7044, -1, 1500, 4, 1, 'Levier');
INSERT INTO `interactive_objects_data` VALUES (7045, -1, 1500, 4, 1, 'Levier');
INSERT INTO `interactive_objects_data` VALUES (1853, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1854, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1855, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1856, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1857, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1858, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1859, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1860, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1861, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1862, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (1845, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (2319, -1, 1500, 4, 1, 'Statue de classe');
INSERT INTO `interactive_objects_data` VALUES (0, 10000, 1500, 4, 1, 'Source de jouvence');
INSERT INTO `interactive_objects_data` VALUES (7510, -1, 1500, 4, 1, 'Tas de patates');
INSERT INTO `interactive_objects_data` VALUES (7005, -1, 1500, 4, 1, 'Meule');
INSERT INTO `interactive_objects_data` VALUES (7028, -1, 1500, 4, 1, 'Etabli Pyrotechnique');
INSERT INTO `interactive_objects_data` VALUES (7546, 1000, -1, 4, 1, 'Machine de force (Foire)');
INSERT INTO `interactive_objects_data` VALUES (7547, 1000, -1, 4, 1, 'Machine de force (Foire)');

SET FOREIGN_KEY_CHECKS = 1;
