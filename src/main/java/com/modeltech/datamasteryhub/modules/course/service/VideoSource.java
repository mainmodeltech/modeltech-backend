package com.modeltech.datamasteryhub.modules.course.service;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vidéo hébergée chez Vimeo ou YouTube, reconnue à partir du lien saisi par l'équipe.
 * Le lecteur de l'apprenant n'intègre jamais une adresse libre : l'adresse d'intégration est
 * <strong>construite ici</strong> à partir de l'identifiant (hôtes fixes, pas d'injection possible).
 * <p>
 * Le téléchargement et les restrictions de domaine se règlent chez l'hébergeur (Vimeo : « Qui peut
 * intégrer » + « Désactiver le téléchargement » ; YouTube ne permet pas d'empêcher le téléchargement).
 */
public record VideoSource(Provider provider, String id, String hash) {

    public enum Provider { VIMEO, YOUTUBE }

    private static final Pattern VIMEO_ID = Pattern.compile("^\\d{6,12}$");
    private static final Pattern VIMEO_HASH = Pattern.compile("^[0-9a-f]{8,20}$");
    private static final Pattern YOUTUBE_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
    private static final Pattern YOUTUBE_PATH = Pattern.compile("^/(?:embed|shorts|live|v)/([^/]+)");

    /** Reconnaît un lien Vimeo/YouTube ; vide pour tout autre lien (lien direct, HLS…), qui reste livré tel quel. */
    public static Optional<VideoSource> parse(String url) {
        if (url == null || url.isBlank()) return Optional.empty();
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (host == null || scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
            return Optional.empty();
        }
        host = host.toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();

        if (host.equals("vimeo.com") || host.equals("www.vimeo.com") || host.equals("player.vimeo.com")) {
            return parseVimeo(host, path, uri.getRawQuery());
        }
        if (host.equals("youtu.be")) {
            return youtube(firstSegment(path));
        }
        if (host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")
                || host.equals("youtube-nocookie.com") || host.equals("www.youtube-nocookie.com")) {
            if (path.equals("/watch")) return youtube(queryParam(uri.getRawQuery(), "v"));
            Matcher m = YOUTUBE_PATH.matcher(path);
            return m.find() ? youtube(m.group(1)) : Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<VideoSource> parseVimeo(String host, String path, String query) {
        // vimeo.com/123456789[/hash]  ·  player.vimeo.com/video/123456789?h=hash
        String[] parts = path.replaceFirst("^/", "").split("/");
        int i = host.equals("player.vimeo.com") ? (parts.length > 1 && parts[0].equals("video") ? 1 : -1) : 0;
        if (i < 0 || i >= parts.length || !VIMEO_ID.matcher(parts[i]).matches()) return Optional.empty();
        String hash = queryParam(query, "h");
        if (hash == null && i + 1 < parts.length) hash = parts[i + 1];
        if (hash != null && !VIMEO_HASH.matcher(hash).matches()) hash = null;
        return Optional.of(new VideoSource(Provider.VIMEO, parts[i], hash));
    }

    private static Optional<VideoSource> youtube(String id) {
        return id != null && YOUTUBE_ID.matcher(id).matches()
                ? Optional.of(new VideoSource(Provider.YOUTUBE, id, null))
                : Optional.empty();
    }

    private static String firstSegment(String path) {
        String p = path.replaceFirst("^/", "");
        int slash = p.indexOf('/');
        return slash < 0 ? p : p.substring(0, slash);
    }

    private static String queryParam(String query, String name) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
        }
        return null;
    }

    /** Adresse d'intégration (iframe) : sans pistage, sans vidéos suggérées d'autres chaînes. */
    public String embedUrl() {
        return switch (provider) {
            case VIMEO -> "https://player.vimeo.com/video/" + id + "?"
                    + (hash != null ? "h=" + hash + "&" : "") + "dnt=1&title=0&byline=0&portrait=0";
            case YOUTUBE -> "https://www.youtube-nocookie.com/embed/" + id + "?rel=0&modestbranding=1";
        };
    }
}
