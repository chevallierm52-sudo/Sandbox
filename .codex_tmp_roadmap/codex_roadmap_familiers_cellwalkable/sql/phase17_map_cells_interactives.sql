-- Phase 17 : CellWalkable / interactifs / overrides
-- Objectif : bloquer proprement puits, ressources, ateliers, portes, décors selon contexte.

CREATE TABLE IF NOT EXISTS map_cell_overrides (
    map_id INT NOT NULL,
    cell_id SMALLINT NOT NULL,
    blocking_player_movement TINYINT NOT NULL DEFAULT 0,
    blocking_bot_movement TINYINT NOT NULL DEFAULT 0,
    blocking_monster_spawn TINYINT NOT NULL DEFAULT 1,
    blocking_npc_spawn TINYINT NOT NULL DEFAULT 0,
    reason VARCHAR(64) NOT NULL DEFAULT 'manual',
    PRIMARY KEY (map_id, cell_id)
);

CREATE TABLE IF NOT EXISTS map_interactive_object_rules (
    gfx_id INT NOT NULL PRIMARY KEY,
    object_type VARCHAR(64) NOT NULL DEFAULT 'unknown',
    blocking_player_movement TINYINT NOT NULL DEFAULT 0,
    blocking_bot_movement TINYINT NOT NULL DEFAULT 0,
    blocking_monster_spawn TINYINT NOT NULL DEFAULT 1,
    blocking_npc_spawn TINYINT NOT NULL DEFAULT 0,
    harvestable TINYINT NOT NULL DEFAULT 0,
    usable TINYINT NOT NULL DEFAULT 0
);

-- Table de cache optionnelle si on veut persister le décodage complet des cellules.
CREATE TABLE IF NOT EXISTS map_decoded_cells (
    map_id INT NOT NULL,
    cell_id SMALLINT NOT NULL,
    active TINYINT NOT NULL DEFAULT 0,
    line_of_sight TINYINT NOT NULL DEFAULT 0,
    movement TINYINT NOT NULL DEFAULT 0,
    ground_level TINYINT NOT NULL DEFAULT 0,
    ground_slope TINYINT NOT NULL DEFAULT 0,
    layer_object_1_num INT NOT NULL DEFAULT 0,
    layer_object_2_num INT NOT NULL DEFAULT 0,
    layer_object_2_interactive TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (map_id, cell_id),
    KEY idx_map_decoded_interactive (map_id, layer_object_2_interactive)
);

-- Exemple manuel : interdire une cellule de puits au déplacement et au spawn.
-- INSERT INTO map_cell_overrides (map_id, cell_id, blocking_player_movement, blocking_bot_movement, blocking_monster_spawn, reason)
-- VALUES (1395, 221, 1, 1, 1, 'well')
-- ON DUPLICATE KEY UPDATE
--   blocking_player_movement=VALUES(blocking_player_movement),
--   blocking_bot_movement=VALUES(blocking_bot_movement),
--   blocking_monster_spawn=VALUES(blocking_monster_spawn),
--   reason=VALUES(reason);
