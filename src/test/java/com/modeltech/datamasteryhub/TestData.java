package com.modeltech.datamasteryhub;

import com.modeltech.datamasteryhub.modules.training.entity.Bootcamp;
import com.modeltech.datamasteryhub.modules.training.entity.BootcampSession;
import com.modeltech.datamasteryhub.modules.training.entity.Domain;
import com.modeltech.datamasteryhub.modules.training.entity.Partner;
import com.modeltech.datamasteryhub.modules.training.entity.Registration;
import com.modeltech.datamasteryhub.modules.training.enums.DeliveredBy;
import com.modeltech.datamasteryhub.modules.training.enums.SessionStatus;

import java.time.LocalDate;

/** Fabriques d'entités pour les tests (les entités n'ont pas de @Builder). */
public final class TestData {

    private TestData() {}

    public static Domain domain(String slug, String name, boolean visible) {
        Domain d = new Domain();
        d.setSlug(slug);
        d.setName(name);
        d.setVisible(visible);
        return d;
    }

    public static Partner partner(String slug, String name) {
        Partner p = new Partner();
        p.setSlug(slug);
        p.setName(name);
        p.setBio("Bio de " + name);
        p.setWebsite("https://example.com/" + slug);
        p.setContactEmail("secret-contact@example.com");
        p.setRevenueSharePercent(new java.math.BigDecimal("30.00"));
        return p;
    }

    public static Bootcamp bootcamp(String slug, String title, Domain domain) {
        Bootcamp b = new Bootcamp();
        b.setSlug(slug);
        b.setTitle(title);
        b.setDomain(domain);
        b.setDeliveredBy(DeliveredBy.INTERNAL);
        b.setPublished(true);
        return b;
    }

    public static BootcampSession session(Bootcamp bootcamp, String name, LocalDate start, SessionStatus status) {
        BootcampSession s = new BootcampSession();
        s.setBootcamp(bootcamp);
        s.setSessionName(name);
        s.setStartDate(start);
        s.setStatus(status);
        s.setPublished(true);
        return s;
    }

    public static Registration registration(String email, Bootcamp bootcamp, BootcampSession session, Integer discountPercent) {
        Registration r = new Registration();
        r.setFirstName("Awa");
        r.setLastName("Diop");
        r.setEmail(email);
        r.setPhone("771234567");
        r.setCountry("Sénégal");
        r.setBootcamp(bootcamp);
        r.setBootcampTitle(bootcamp.getTitle());
        r.setSession(session);
        r.setSessionName(session != null ? session.getSessionName() : null);
        r.setDiscountPercent(discountPercent);
        return r;
    }
}
