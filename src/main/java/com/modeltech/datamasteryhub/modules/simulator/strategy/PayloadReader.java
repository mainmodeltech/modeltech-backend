package com.modeltech.datamasteryhub.modules.simulator.strategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lecture défensive d'un payload JSON (types inattendus → message clair, jamais d'exception de conversion). */
final class PayloadReader {

    static final int MAX_TEXT = 1000;

    private PayloadReader() {}

    /** La liste d'objets sous {@code key} ; refuse l'absence, un mauvais type et une taille hors bornes. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> objects(Map<String, Object> payload, String key, int min, int max) {
        Object raw = payload == null ? null : payload.get(key);
        if (!(raw instanceof List<?> list)) {
            throw new InvalidItemException("Le champ « " + key + " » est obligatoire (liste).");
        }
        if (list.size() < min || list.size() > max) {
            throw new InvalidItemException("« " + key + " » doit contenir entre " + min + " et " + max + " éléments (reçu : " + list.size() + ").");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                throw new InvalidItemException("Chaque élément de « " + key + " » doit être un objet.");
            }
            out.add((Map<String, Object>) m);
        }
        return out;
    }

    static String text(Map<String, Object> o, String key, String what) {
        Object v = o.get(key);
        String s = v instanceof String str ? str.trim() : "";
        if (s.isEmpty()) throw new InvalidItemException(what + " : le texte est obligatoire.");
        if (s.length() > MAX_TEXT) throw new InvalidItemException(what + " : " + MAX_TEXT + " caractères au plus.");
        return s;
    }

    static boolean flag(Map<String, Object> o, String key, String what) {
        if (!(o.get(key) instanceof Boolean b)) {
            throw new InvalidItemException(what + " : « " + key + " » doit être vrai ou faux.");
        }
        return b;
    }

    static String id(Map<String, Object> o, String pattern, String what) {
        Object v = o.get("id");
        String s = v instanceof String str ? str.trim() : "";
        if (!s.matches(pattern)) throw new InvalidItemException(what + " : identifiant invalide.");
        return s;
    }

    static Map<String, Object> item(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) m.put((String) keyValues[i], keyValues[i + 1]);
        return m;
    }
}
