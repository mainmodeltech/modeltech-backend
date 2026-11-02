package com.modeltech.datamasteryhub.modules.simulator.strategy;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Recherche de mots-clés dans une réponse libre. Le texte et les mots-clés sont ramenés à une forme commune : sans accents,
 * en minuscules, ponctuation et symboles remplacés par des espaces (« Sales[Amount] » devient « sales amount »).
 *
 * <p>Un mot-clé est reconnu <b>au début d'un mot</b> : « mesure » reconnaît « mesure » et « mesures », « calculate » reconnaît
 * « calculated ». Un mot-clé de plusieurs mots (« filter context ») doit apparaître tel quel, mots contigus. Aucune expression
 * régulière n'est construite à partir du texte de l'apprenant.
 */
final class KeywordMatcher {

    private KeywordMatcher() {}

    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^\\p{L}\\p{Nd}]+", " ").trim();
    }

    /** {@code normalizedAnswer} doit déjà être passé par {@link #normalize(String)}. */
    static boolean contains(String normalizedAnswer, String keyword) {
        String k = normalize(keyword);
        return !k.isEmpty() && (" " + normalizedAnswer).contains(" " + k);
    }
}
