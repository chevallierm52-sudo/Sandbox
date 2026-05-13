package org.dofus.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dofus.objects.maps.MapTemplate;
import org.dofus.objects.maps.MapTemplate.Cell;
import org.dofus.objects.maps.MapTemplate.Cell.MovementType;
import org.dofus.objects.maps.MapTemplate.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodeur de cellules Dofus 1.29.
 *
 * Le client 1.29 decompresse une map en blocs de 10 caracteres par cellule.
 * Chaque caractere est un entier base64 selon StringUtils.HASH. Les champs utiles
 * cote serveur sont principalement : active, lineOfSight, movement, groundLevel,
 * groundSlope et les objets interactifs portes par la couche Object2.
 *
 * Certaines bases stockent directement les blocs encodes, d'autres stockent une
 * chaine hex chiffree avec la clef/date de map. Ce decodeur tente les formats connus
 * et renvoie une map vide si aucune variante n'est exploitable, afin de ne jamais
 * casser le chargement des maps existantes.
 */
public final class MapCellDecoder {

    private static final Logger logger = LoggerFactory.getLogger(MapCellDecoder.class);
    private static final int MAX_CELL_COUNT = 560;
    private static final int MIN_CELL_COUNT = 100;
    private static final int CELL_SIZE = 10;

    private MapCellDecoder() {}


    /** Decode les cellules de placement combat stockees dans map_templates.places.
     * Format Dofus 1.29 : deux listes separees par |, chaque cellule encodee sur 2 caracteres base64.
     * On renvoie les deux equipes, car ces cellules sont souvent les meilleures zones naturelles
     * pour placer des groupes visibles sur la map hors combat.
     */
    public static List<Short> decodePlacementCells(String places) {
        Set<Short> unique = new LinkedHashSet<Short>();
        if(places == null || places.isEmpty()) return new ArrayList<Short>();

        String compact = places.replace("|", "");
        for(int i = 0; i + 1 < compact.length(); i += 2) {
            int high = StringUtils.HASH.indexOf(compact.charAt(i));
            int low = StringUtils.HASH.indexOf(compact.charAt(i + 1));
            if(high < 0 || low < 0) continue;

            int cell = high * 64 + low;
            if(cell >= 0 && cell <= 559) unique.add((short) cell);
        }
        return new ArrayList<Short>(unique);
    }

    public static Map<Short, Cell> decode(String encodedOrEncryptedData, String mapKey) {
        Map<Short, Cell> empty = new LinkedHashMap<Short, Cell>();
        if(encodedOrEncryptedData == null || encodedOrEncryptedData.isEmpty()) return empty;

        String plain = findPlainCellData(encodedOrEncryptedData, mapKey);
        if(plain == null) return empty;

        try {
            return decodePlainCells(plain);
        } catch(Exception e) {
            logger.debug("MapCellDecoder : donnees cellules ignorees ({})", e.getMessage());
            return empty;
        }
    }

    private static String findPlainCellData(String data, String mapKey) {
        if(looksLikePlainCells(data)) return data;

        if(mapKey != null && !mapKey.isEmpty() && looksLikeHex(data)) {
            String officialKey = prepareKey(mapKey);
            if(officialKey != null && !officialKey.isEmpty()) {
                String decoded = decypherData(data, officialKey, checksumOffset(officialKey), 0);
                if(looksLikePlainCells(decoded)) return decoded;
            }

            String checksum = checksumLegacy(mapKey);
            int checksumOffset = 0;
            try {
                checksumOffset = Integer.parseInt(checksum, 16) * 2;
            } catch(NumberFormatException ignored) {}

            String decoded = decypherData(data, mapKey, checksumOffset, 0);
            if(looksLikePlainCells(decoded)) return decoded;

            decoded = decypherData(data, mapKey, checksumOffset, 2);
            if(looksLikePlainCells(decoded)) return decoded;
        }

        return null;
    }

    private static int checksumOffset(String key) {
        int sum = 0;
        for(int i = 0; i < key.length(); i++) {
            sum += key.charAt(i) % 16;
        }
        return (sum % 16) * 2;
    }

    private static String prepareKey(String key) {
        if(key == null || key.isEmpty()) return "";
        if(!looksLikeHex(key)) return key;

        StringBuilder prepared = new StringBuilder(key.length() / 2);
        for(int i = 0; i + 1 < key.length(); i += 2) {
            try {
                prepared.append((char) Integer.parseInt(key.substring(i, i + 2), 16));
            } catch(NumberFormatException e) {
                return key;
            }
        }

        try {
            return URLDecoder.decode(prepared.toString(), "UTF-8");
        } catch(UnsupportedEncodingException e) {
            return prepared.toString();
        } catch(IllegalArgumentException e) {
            return prepared.toString();
        }
    }

    private static Map<Short, Cell> decodePlainCells(String data) {
        Map<Short, Cell> cells = new LinkedHashMap<Short, Cell>();
        int max = Math.min(MAX_CELL_COUNT, data.length() / CELL_SIZE);

        for(short id = 0; id < max; id++) {
            int offset = id * CELL_SIZE;
            int[] values = new int[CELL_SIZE];
            for(int i = 0; i < CELL_SIZE; i++) {
                values[i] = StringUtils.HASH.indexOf(data.charAt(offset + i));
                if(values[i] < 0) throw new IllegalArgumentException("caractere cellule invalide");
            }

            boolean active = ((values[0] & 32) >> 5) == 1;
            boolean lineOfSight = (values[0] & 1) == 1;
            int movement = (values[2] & 56) >> 3;
            int groundLevel = values[1] & 15;
            int groundSlope = (values[4] & 60) >> 2;
            int layerObject1Num = ((values[0] & 4) << 11)
                    + ((values[4] & 1) << 12)
                    + (values[5] << 6)
                    + values[6];
            boolean layerObject2Interactive = ((values[7] & 2) >> 1) == 1;
            int layerObject2Num = ((values[0] & 2) << 12)
                    + ((values[7] & 1) << 12)
                    + (values[8] << 6)
                    + values[9];

            MovementType type = MovementType.valueOf(movement);
            if(type == null) type = MovementType.Unwalkable;

            cells.put(id, new Cell(id, active, lineOfSight, type, groundLevel, groundSlope,
                    layerObject1Num, layerObject2Num, layerObject2Interactive, toPoint(id)));
        }

        return cells;
    }

    /** Conversion cellule -> coordonnees isometriques approximatives, suffisante pour trier des distances. */
    public static Point toPoint(short cellId) {
        int row = cellId / 14;
        int col = cellId % 14;
        int x = col - row;
        int y = col + row;
        return new Point(x, y);
    }

    public static int distance(short a, short b) {
        Point pa = toPoint(a);
        Point pb = toPoint(b);
        return Math.abs(pa.getX() - pb.getX()) + Math.abs(pa.getY() - pb.getY());
    }

    private static boolean looksLikePlainCells(String data) {
        if(data == null || data.length() < MIN_CELL_COUNT * CELL_SIZE || (data.length() % CELL_SIZE) != 0)
            return false;
        for(int i = 0; i < data.length(); i++) {
            if(StringUtils.HASH.indexOf(data.charAt(i)) < 0) return false;
        }
        return true;
    }

    private static boolean looksLikeHex(String data) {
        if(data == null || (data.length() % 2) != 0) return false;
        for(int i = 0; i < data.length(); i++) {
            char c = data.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if(!ok) return false;
        }
        return true;
    }

    private static String decypherData(String data, String key, int checksumOffset, int hexStart) {
        if(key == null || key.isEmpty()) return null;
        StringBuilder out = new StringBuilder(data.length() / 2);
        int keyLen = key.length();
        int keyIndex = 0;
        for(int i = hexStart; i + 1 < data.length(); i += 2) {
            int value;
            try {
                value = Integer.parseInt(data.substring(i, i + 2), 16);
            } catch(NumberFormatException e) {
                return null;
            }
            int k = key.charAt((keyIndex++ + checksumOffset) % keyLen);
            out.append((char)(value ^ k));
        }
        return out.toString();
    }

    private static String checksumLegacy(String s) {
        int sum = 0;
        for(int i = 1; i < s.length(); i++) {
            sum += s.charAt(i) % 16;
        }
        return Integer.toHexString(sum % 16);
    }
}
