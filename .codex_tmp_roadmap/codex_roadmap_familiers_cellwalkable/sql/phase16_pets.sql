-- Phase 16 : Familiers Anka-like
-- Compatible MySQL/MariaDB. À appliquer après sauvegarde.

CREATE TABLE IF NOT EXISTS pet_templates (
    item_template_id INT NOT NULL PRIMARY KEY,
    base_life TINYINT NOT NULL DEFAULT 10,
    min_feed_interval_minutes INT NOT NULL DEFAULT 300,
    max_feed_interval_minutes INT NOT NULL DEFAULT 1080,
    meals_per_bonus TINYINT NOT NULL DEFAULT 3,
    max_bonus INT NOT NULL DEFAULT 80,
    hormone_max_bonus INT NOT NULL DEFAULT 90,
    can_die TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS pet_food_rules (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    pet_template_id INT NOT NULL,
    food_template_id INT NOT NULL,
    effect_id INT NOT NULL,
    stat_gain INT NOT NULL DEFAULT 1,
    UNIQUE KEY uq_pet_food (pet_template_id, food_template_id, effect_id),
    KEY idx_pet_food_pet (pet_template_id),
    KEY idx_pet_food_food (food_template_id)
);

CREATE TABLE IF NOT EXISTS pet_states (
    item_uid BIGINT NOT NULL PRIMARY KEY,
    life TINYINT NOT NULL DEFAULT 10,
    state TINYINT NOT NULL DEFAULT 0,
    last_feed_at BIGINT NOT NULL DEFAULT 0,
    last_bonus_food_effect INT NOT NULL DEFAULT 0,
    meal_counter TINYINT NOT NULL DEFAULT 0,
    hormoned TINYINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL DEFAULT 0
);

-- Exemples à compléter avec les vrais template_id de la base.
-- INSERT IGNORE INTO pet_templates (item_template_id, base_life, min_feed_interval_minutes, max_feed_interval_minutes, meals_per_bonus, max_bonus, hormone_max_bonus)
-- VALUES (1711, 10, 300, 1080, 3, 80, 90);
