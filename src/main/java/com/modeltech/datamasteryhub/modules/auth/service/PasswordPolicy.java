package com.modeltech.datamasteryhub.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Règles des mots de passe : longueur (12 caractères pour le personnel, 10 pour les apprenants), pas de mot de passe
 * courant ni de variante évidente (« Password123! », « P@ssw0rd »), ni de suite de touches ou de chiffres, ni l'adresse
 * e-mail du compte. Pas d'obligation de majuscule/symbole : la longueur protège mieux (recommandation NIST SP 800-63B).
 * Le maximum est de 72 caractères (limite de BCrypt : au-delà, la fin du mot de passe serait ignorée sans le dire).
 */
@Component
@Slf4j
public class PasswordPolicy {

    private static final int MAX_LENGTH = 72;

    private final Set<String> common = new HashSet<>();

    @Value("${app.auth.password.min-length-staff:12}")
    private int minLengthStaff;

    @Value("${app.auth.password.min-length-learner:10}")
    private int minLengthLearner;

    public PasswordPolicy() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("security/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().map(String::trim).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(common::add);
        } catch (IOException e) {
            log.error("Liste des mots de passe courants illisible : seules les règles de longueur s'appliquent ({})", e.getMessage());
        }
    }

    public int minLength(boolean staff) {
        return staff ? minLengthStaff : minLengthLearner;
    }

    /** @throws ResponseStatusException 400 avec la raison, en français, affichée telle quelle à l'utilisateur */
    public void validate(String password, String email, boolean staff) {
        String reason = violation(password, email, staff);
        if (reason != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    /** Raison du refus, ou null si le mot de passe est acceptable. */
    String violation(String password, String email, boolean staff) {
        if (password == null) return "Le mot de passe est obligatoire.";
        int min = minLength(staff);
        if (password.length() < min) return "Le mot de passe doit contenir au moins " + min + " caractères.";
        if (password.length() > MAX_LENGTH) return "Le mot de passe ne doit pas dépasser " + MAX_LENGTH + " caractères.";

        String lower = password.toLowerCase(Locale.ROOT);
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (local.length() >= 4 && lower.contains(local)) return "Le mot de passe ne doit pas contenir votre adresse e-mail.";
        }
        if (isRepeated(lower) || isSequence(lower) || isCommon(lower)) {
            return "Ce mot de passe est trop courant ou trop prévisible. Choisissez une phrase plus longue et personnelle.";
        }
        return null;
    }

    private boolean isRepeated(String s) {
        return s.chars().distinct().count() <= 2;
    }

    /** Suite continue (123456789012, abcdefghijkl, 987654321098) dans un sens ou dans l'autre, la fin d'une suite reprenant au début. */
    private boolean isSequence(String s) {
        if (s.length() < 6) return false;
        boolean up = true, down = true;
        for (int i = 1; i < s.length(); i++) {
            char prev = s.charAt(i - 1), cur = s.charAt(i);
            int diff = cur - prev;
            if (diff != 1 && !(prev == '9' && cur == '0') && !(prev == 'z' && cur == 'a')) up = false;
            if (diff != -1 && !(prev == '0' && cur == '9') && !(prev == 'a' && cur == 'z')) down = false;
        }
        return up || down;
    }

    private boolean isCommon(String lower) {
        // Sans les chiffres et symboles de fin (« Password123! » → « password »), avant et après remplacement des lettres déguisées
        String stripped = lower.replaceAll("[^a-z]+$", "").replaceAll("^[^a-z]+", "");
        String strippedRaw = lower.replaceAll("[0-9!@#$%^&*._+=?-]+$", "");
        for (String base : new String[]{lower, stripped, strippedRaw}) {
            for (String candidate : new String[]{base, leet(base)}) {
                String compact = candidate.replaceAll("[^a-z0-9]", "");
                for (String variant : new String[]{candidate, compact, compact.replaceAll("[0-9]+$", "")}) {
                    if (variant.length() >= 3 && common.contains(variant)) return true;
                }
            }
        }
        for (String candidate : new String[]{stripped, leet(strippedRaw), leet(stripped)}) {
            String letters = candidate.replaceAll("[^a-z]", "");
            for (String word : common) {
                if (word.length() >= 6 && letters.length() <= word.length() + 4 && letters.contains(word)) return true;
            }
        }
        return false;
    }

    /** « p@ssw0rd » → « password ». */
    private static String leet(String s) {
        return s.replace('@', 'a').replace('0', 'o').replace('1', 'i').replace('3', 'e')
                .replace('$', 's').replace('5', 's').replace('4', 'a').replace('7', 't').replace('!', 'i');
    }
}
