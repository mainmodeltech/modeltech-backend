package com.modeltech.datamasteryhub.config;

import com.modeltech.datamasteryhub.modules.auth.entity.AdminUser;
import com.modeltech.datamasteryhub.modules.auth.entity.Role;
import com.modeltech.datamasteryhub.modules.auth.repository.AdminUserRepository;
import com.modeltech.datamasteryhub.modules.auth.repository.RoleRepository;
import com.modeltech.datamasteryhub.modules.cms.entity.Service;
import com.modeltech.datamasteryhub.modules.cms.repository.ServiceRepository;
import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.enums.SessionFormat;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;
import com.modeltech.datamasteryhub.modules.training.repository.BootcampRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Seed les donnees de base au lancement si la BDD est vide.
 * Ne fait rien si des donnees existent deja.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AdminUserRepository adminUserRepository;
    private final BootcampRepository bootcampRepository;
    private final ServiceRepository serviceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedRolesAndAdmin();
        seedBootcamps();
        seedServices();
    }

    // ─── Roles + Admin ──────────────────────────────────────────────────────────

    private void seedRolesAndAdmin() {
//        if (roleRepository.count() > 0) {
//            log.debug("Roles deja presents — skip seed roles");
//            return;
//        }

        log.info("Seeding roles et admin user...");

        Role roleAdmin = new Role();
        roleAdmin.setName("ROLE_ADMIN");
        roleAdmin.setDescription("Administrateur complet");
        roleAdmin = roleRepository.save(roleAdmin);

        Role roleSuperAdmin = new Role();
        roleSuperAdmin.setName("ROLE_SUPER_ADMIN");
        roleSuperAdmin.setDescription("Super administrateur");
        roleSuperAdmin = roleRepository.save(roleSuperAdmin);

        // Admin par defaut — mot de passe a changer apres premier login
        if (!adminUserRepository.existsByEmailAndIsDeletedFalse("admin@model-technologie.com")) {
            AdminUser admin = AdminUser.builder()
                    .email("admin@model-technologie.com")
                    .passwordHash(passwordEncoder.encode("ChangerCeMotDePasse!2026"))
                    .fullName("Admin Model Tech")
                    .roles(new HashSet<>(Set.of(roleAdmin, roleSuperAdmin)))
                    .active(true)
                    .build();
            adminUserRepository.save(admin);
            log.info("Admin cree: admin@model-technologie.com (mot de passe par defaut)");
        }
    }

    // ─── Bootcamps ──────────────────────────────────────────────────────────────

    private void seedBootcamps() {
        if (bootcampRepository.count() > 0) {
            log.debug("Bootcamps deja presents — skip seed bootcamps");
            return;
        }

        log.info("Seeding bootcamps...");

        // ── Bootcamp Data Analyst ──
        Bootcamp dataAnalyst = new Bootcamp();
        dataAnalyst.setTitle("Data Analyst — SQL, Python & Power BI");
        dataAnalyst.setDescription(
                "Formation intensive pour devenir Data Analyst. "
                + "Maitrisez SQL, Python et Power BI a travers des projets concrets "
                + "bases sur des donnees reelles d'entreprises africaines."
        );
        dataAnalyst.setDuration("12 semaines");
        dataAnalyst.setAudience("Etudiants, professionnels en reconversion, analystes juniors");
        dataAnalyst.setPrerequisites("Notions de base en informatique. Aucune experience en programmation requise.");
        dataAnalyst.setPrice("450 000 FCFA");
        dataAnalyst.setBenefits(List.of(
                "Maitrise de SQL pour l'extraction et l'analyse de donnees",
                "Automatisation avec Python (pandas, matplotlib)",
                "Dashboards professionnels avec Power BI",
                "Portfolio de 3+ projets concrets",
                "Certification Model Technologie",
                "Accompagnement post-formation (3 mois)"
        ));
        dataAnalyst.setCategory("data");
        dataAnalyst.setTag("Populaire");
        dataAnalyst.setIconName("BarChart3");
        dataAnalyst.setFeatured(true);
        dataAnalyst.setPublished(true);
        dataAnalyst.setDisplayOrder(1);

        // Session pour ce bootcamp
        BootcampSession session1 = new BootcampSession();
        session1.setBootcamp(dataAnalyst);
        session1.setSessionName("Cohorte 6 — Avril 2026");
        session1.setCohortNumber(6);
        session1.setYear(2026);
        session1.setStartDate(LocalDate.of(2026, 4, 14));
        session1.setEndDate(LocalDate.of(2026, 7, 6));
        session1.setRegistrationDeadline(LocalDate.of(2026, 4, 7));
        session1.setMaxParticipants(20);
        session1.setCurrentParticipants(0);
        session1.setStatus(SessionStatus.OPEN);
        session1.setFormat(SessionFormat.PRESENTIEL);
        session1.setLocation("Dakar, Senegal");
        session1.setIsFeatured(true);
        session1.setPublished(true);

        dataAnalyst.setSessions(new ArrayList<>(List.of(session1)));
        bootcampRepository.save(dataAnalyst);

        // ── Bootcamp Power BI Avance ──
        Bootcamp powerBi = new Bootcamp();
        powerBi.setTitle("Power BI Avance — DAX & Modelisation");
        powerBi.setDescription(
                "Perfectionnez vos competences Power BI. "
                + "DAX avance, modelisation relationnelle, row-level security, "
                + "et deploiement sur Power BI Service."
        );
        powerBi.setDuration("6 semaines");
        powerBi.setAudience("Analystes et BI developers avec experience Power BI de base");
        powerBi.setPrerequisites("Avoir suivi le bootcamp Data Analyst ou equivalent. Connaitre les bases de Power BI.");
        powerBi.setPrice("300 000 FCFA");
        powerBi.setBenefits(List.of(
                "DAX avance (CALCULATE, contextes, time intelligence)",
                "Modelisation en etoile et flocon",
                "Row-level security (RLS)",
                "Deploiement Power BI Service & Gateway",
                "Certification Model Technologie — Power BI"
        ));
        powerBi.setCategory("data");
        powerBi.setTag("Avance");
        powerBi.setIconName("LineChart");
        powerBi.setFeatured(false);
        powerBi.setPublished(true);
        powerBi.setDisplayOrder(2);

        BootcampSession session2 = new BootcampSession();
        session2.setBootcamp(powerBi);
        session2.setSessionName("Cohorte 3 — Mai 2026");
        session2.setCohortNumber(3);
        session2.setYear(2026);
        session2.setStartDate(LocalDate.of(2026, 5, 4));
        session2.setEndDate(LocalDate.of(2026, 6, 15));
        session2.setRegistrationDeadline(LocalDate.of(2026, 4, 28));
        session2.setMaxParticipants(15);
        session2.setCurrentParticipants(0);
        session2.setStatus(SessionStatus.UPCOMING);
        session2.setFormat(SessionFormat.HYBRID);
        session2.setLocation("Dakar / En ligne");
        session2.setPriceOverride("300 000 FCFA");
        session2.setIsFeatured(true);
        session2.setPublished(true);

        powerBi.setSessions(new ArrayList<>(List.of(session2)));
        bootcampRepository.save(powerBi);

        // ── Bootcamp SQL Fondamentaux ──
        Bootcamp sql = new Bootcamp();
        sql.setTitle("SQL pour l'Analyse de Donnees");
        sql.setDescription(
                "Apprenez a interroger, transformer et analyser des donnees "
                + "avec SQL. De SELECT aux window functions, en passant par les "
                + "jointures complexes et l'optimisation des requetes."
        );
        sql.setDuration("4 semaines");
        sql.setAudience("Debutants complets, etudiants, marketeurs, financiers");
        sql.setPrerequisites("Aucun prerequis technique.");
        sql.setPrice("200 000 FCFA");
        sql.setBenefits(List.of(
                "Interroger des bases de donnees relationnelles",
                "Jointures, sous-requetes, CTEs",
                "Window functions et agregations avancees",
                "Exercices sur donnees reelles",
                "Certificat de completion"
        ));
        sql.setCategory("data");
        sql.setTag("Debutant");
        sql.setIconName("Database");
        sql.setFeatured(false);
        sql.setPublished(true);
        sql.setDisplayOrder(3);
        bootcampRepository.save(sql);

        log.info("3 bootcamps seeded avec 2 sessions");
    }

    // ─── Services B2B ───────────────────────────────────────────────────────────

    private void seedServices() {
        if (serviceRepository.count() > 0) {
            log.debug("Services deja presents — skip seed services");
            return;
        }

        log.info("Seeding services B2B...");

        Service consulting = new Service();
        consulting.setTitle("Conseil en Data & BI");
        consulting.setDescription(
                "Accompagnement strategique pour structurer votre demarche data : "
                + "audit de l'existant, definition de KPIs, choix d'outils, "
                + "et mise en place de dashboards decisionnels."
        );
        consulting.setIconName("Lightbulb");
        consulting.setFeatures(List.of(
                "Audit de maturite data",
                "Definition de KPIs metier",
                "Choix d'architecture BI",
                "Formation des equipes internes",
                "Accompagnement sur 3 a 6 mois"
        ));
        consulting.setDuration("3 a 6 mois");
        consulting.setDisplayOrder(1);
        consulting.setPublished(true);
        serviceRepository.save(consulting);

        Service dashboards = new Service();
        dashboards.setTitle("Creation de Dashboards");
        dashboards.setDescription(
                "Conception et developpement de tableaux de bord Power BI "
                + "sur mesure, connectes a vos sources de donnees (ERP, CRM, "
                + "Excel, bases SQL)."
        );
        dashboards.setIconName("BarChart3");
        dashboards.setFeatures(List.of(
                "Dashboards Power BI sur mesure",
                "Connexion multi-sources",
                "Modelisation des donnees",
                "Formation a l'utilisation",
                "Maintenance et evolution"
        ));
        dashboards.setDuration("2 a 8 semaines");
        dashboards.setDisplayOrder(2);
        dashboards.setPublished(true);
        serviceRepository.save(dashboards);

        Service formation = new Service();
        formation.setTitle("Formation en Entreprise");
        formation.setDescription(
                "Formations sur mesure pour vos equipes : SQL, Python, "
                + "Power BI, Excel avance. Programmes adaptes a votre secteur "
                + "et vos donnees reelles."
        );
        formation.setIconName("GraduationCap");
        formation.setFeatures(List.of(
                "Programme personnalise",
                "Exercices sur vos donnees",
                "Sessions en presentiel ou en ligne",
                "Support post-formation",
                "Certification des participants"
        ));
        formation.setDuration("1 a 4 semaines");
        formation.setDisplayOrder(3);
        formation.setPublished(true);
        serviceRepository.save(formation);

        log.info("3 services B2B seeded");
    }
}
