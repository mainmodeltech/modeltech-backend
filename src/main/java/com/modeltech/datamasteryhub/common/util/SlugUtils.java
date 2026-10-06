package com.modeltech.datamasteryhub.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Génération de slugs d'URL (minuscules, sans accents, séparés par des tirets). */
public final class SlugUtils {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");

    private SlugUtils() {}

    public static String slugify(String input) {
        if (input == null) return "";
        String withoutAccents = DIACRITICS
                .matcher(Normalizer.normalize(input, Normalizer.Form.NFD))
                .replaceAll("");
        String dashed = NON_ALPHANUMERIC
                .matcher(withoutAccents.toLowerCase(Locale.ROOT))
                .replaceAll("-");
        return EDGE_DASHES.matcher(dashed).replaceAll("");
    }

    /**
     * Slug unique : {@code base}, puis {@code base-2}, {@code base-3}… tant que
     * {@code exists} répond vrai. {@code fallback} sert si la base est vide.
     */
    public static String unique(String source, String fallback, Predicate<String> exists) {
        String base = slugify(source);
        if (base.isEmpty()) base = fallback;
        String candidate = base;
        int suffix = 2;
        while (exists.test(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }
}
