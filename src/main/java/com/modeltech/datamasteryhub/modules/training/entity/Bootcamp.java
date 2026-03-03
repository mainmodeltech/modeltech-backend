package com.modeltech.datamasteryhub.modules.training.entity;

import com.modeltech.datamasteryhub.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bootcamp — programme de formation.
 * Un bootcamp peut avoir plusieurs sessions (cohortes planifiées).
 */
@Entity
@Table(name = "bootcamps")
@Getter @Setter @NoArgsConstructor
public class Bootcamp extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Informations principales ────────────────────────────────────
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Durée totale du programme, ex: "10 semaines"
    private String duration;

    // Public cible, ex: "Professionnels reconversion, Étudiants bac+2"
    @Column(columnDefinition = "TEXT")
    private String audience;

    // Prérequis, ex: "Aucun prérequis technique"
    @Column(columnDefinition = "TEXT")
    private String prerequisites;

    // ── Tarification ────────────────────────────────────────────────
    // Prix affiché sur la card, ex: "450 000 FCFA"
    private String price;

    // ── Contenu pédagogique ────────────────────────────────────────
    // Liste des compétences acquises (bullet points sur la card)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> benefits = new ArrayList<>();

    // ── Catégorie / Tag ─────────────────────────────────────────────
    // Catégorie fonctionnelle : "bi", "python", "sql", "ai", "data"
    @Column(length = 50)
    private String category = "data";

    // Tag badge affiché sur la card : "Bestseller", "Nouveau", "Complet"
    private String tag;

    // Icône Lucide affichée dans le header de la card : "BarChart3", "Database"
    @Column(name = "icon_name")
    private String iconName;

    // ── Affichage ───────────────────────────────────────────────────
    // Mise en avant sur la liste (bandeau "Prochaine session", fond dégradé)
    private Boolean featured = false;

    private Boolean published = true;

    @Column(name = "display_order")
    private Integer displayOrder = 0;

    // ── DÉPRÉCIÉ — à supprimer après migration complète vers BootcampSession ──
    // Conservé pour compatibilité avec anciens enregistrements Supabase
    @Column(name = "next_session")
    @Deprecated
    private String nextSession;

    // ── Relations ───────────────────────────────────────────────────
    @OneToMany(
            mappedBy = "bootcamp",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("startDate ASC")
    private List<BootcampSession> sessions = new ArrayList<>();
}