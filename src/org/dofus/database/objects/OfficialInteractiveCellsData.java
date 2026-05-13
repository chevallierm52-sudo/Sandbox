package org.dofus.database.objects;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cellules interactives officielles extraites des scripts maps StarLoco.
 * Ce n'est pas une table d'apprentissage : c'est un fallback statique pour les bases
 * qui ne stockent pas le cellsData complet et ne permettent donc pas de decoder object2.
 */
public final class OfficialInteractiveCellsData {

    private static final Logger logger = LoggerFactory.getLogger(OfficialInteractiveCellsData.class);
    private static final String RESOURCE_PATH = "ressources/starloco_interactive_cells.csv";
    private static final ConcurrentMap<Integer, Map<Short, OfficialInteractiveCell>> byMap =
            new ConcurrentHashMap<Integer, Map<Short, OfficialInteractiveCell>>();

    private OfficialInteractiveCellsData() {}

    public static void load() {
        byMap.clear();
        File file = new File(RESOURCE_PATH);
        if(!file.isFile()) {
            logger.warn("OfficialInteractiveCellsData : fichier absent {}", RESOURCE_PATH);
            return;
        }

        Map<Integer, Map<Short, OfficialInteractiveCell>> loaded =
                new HashMap<Integer, Map<Short, OfficialInteractiveCell>>();
        int count = 0;
        try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            boolean header = true;
            while((line = reader.readLine()) != null) {
                if(header) {
                    header = false;
                    continue;
                }
                if(line.trim().isEmpty()) continue;

                String[] parts = line.split(";");
                if(parts.length < 5) continue;

                int mapId = Integer.parseInt(parts[0]);
                short cellId = Short.parseShort(parts[1]);
                int gfxId = Integer.parseInt(parts[2]);
                int movement = Integer.parseInt(parts[3]);
                boolean active = "1".equals(parts[4]);

                Map<Short, OfficialInteractiveCell> cells = loaded.get(mapId);
                if(cells == null) {
                    cells = new HashMap<Short, OfficialInteractiveCell>();
                    loaded.put(mapId, cells);
                }
                cells.put(cellId, new OfficialInteractiveCell(cellId, gfxId, movement, active));
                count++;
            }
            for(Map.Entry<Integer, Map<Short, OfficialInteractiveCell>> entry : loaded.entrySet()) {
                byMap.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
            }
            logger.info("OfficialInteractiveCellsData : {} cellule(s) interactive(s) StarLoco chargees", count);
        } catch(Exception e) {
            byMap.clear();
            logger.warn("OfficialInteractiveCellsData : chargement impossible : {}", e.getMessage());
        }
    }

    public static OfficialInteractiveCell get(int mapId, short cellId) {
        Map<Short, OfficialInteractiveCell> cells = byMap.get(mapId);
        return cells != null ? cells.get(cellId) : null;
    }

    public static boolean isInteractiveCell(int mapId, short cellId) {
        return get(mapId, cellId) != null;
    }

    public static boolean isBlocking(int mapId, short cellId) {
        OfficialInteractiveCell cell = get(mapId, cellId);
        return cell != null && InteractiveObjectsData.isBlocking(cell.getGfxId());
    }

    public static final class OfficialInteractiveCell {
        private final short cellId;
        private final int gfxId;
        private final int movement;
        private final boolean active;

        private OfficialInteractiveCell(short cellId, int gfxId, int movement, boolean active) {
            this.cellId = cellId;
            this.gfxId = gfxId;
            this.movement = movement;
            this.active = active;
        }

        public short getCellId() { return cellId; }
        public int getGfxId() { return gfxId; }
        public int getMovement() { return movement; }
        public boolean isActive() { return active; }
    }
}
