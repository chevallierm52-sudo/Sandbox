package org.dofus.database;

import org.dofus.database.objects.BreedsData;
import org.dofus.database.objects.ExperiencesData;
import org.dofus.database.objects.GuildsData;
import org.dofus.database.objects.InteractiveObjectsData;
import org.dofus.database.objects.ItemsData;
import org.dofus.database.objects.MonstersData;
import org.dofus.database.objects.NpcData;
import org.dofus.database.objects.OfficialInteractiveCellsData;
import org.dofus.database.objects.PetsData;
import org.dofus.database.objects.SpellsData;
import org.dofus.database.objects.UseItemActionsData;
import org.dofus.network.game.handlers.parsers.CraftParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chargement séquentiel de toutes les données serveur au démarrage.
 *
 * Ordre important :
 *   1. ExperiencesData  — requis par BreedsData et CharactersData
 *   2. BreedsData       — requis par Characters (life, stats de base)
 *   3. NpcData          — templates + questions + spawns
 *   4. ItemsData        — templates d'objets (requis par Inventory)
 *   5. SpellsData       — templates de sorts (requis par combat)
 *   6. MonstersData     — templates + grades (pas de spawn carte ici)
 *   7. GuildsData       — guildes + membres
 *
 * Les loaders sont tous protégés par try/catch en interne :
 * un échec de chargement d'une table optionnelle ne bloque pas le reste.
 */
public class Initialisation {

    private static final Logger logger = LoggerFactory.getLogger(Initialisation.class);

    public static void init() {
        long start = System.currentTimeMillis();

        ExperiencesData.load();
        BreedsData.load();
        InteractiveObjectsData.load();
        OfficialInteractiveCellsData.load();
        NpcData.load();
        ItemsData.load();
        PetsData.load();
        UseItemActionsData.load();
        SpellsData.load();
        MonstersData.load();
        GuildsData.load();
        CraftParser.load();   // recettes d'artisanat (table optionnelle)

        logger.info("Initialisation terminée en {} ms",
            System.currentTimeMillis() - start);
    }
}
