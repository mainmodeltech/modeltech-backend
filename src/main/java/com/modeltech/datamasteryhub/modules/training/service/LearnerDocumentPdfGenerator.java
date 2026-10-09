package com.modeltech.datamasteryhub.modules.training.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.modeltech.datamasteryhub.config.InvoiceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Documents PDF remis à l'apprenant : reçu de paiement et attestation d'inscription (A4 portrait).
 * Les mentions légales viennent de la configuration de facturation ; rien n'est inventé (ligne vide = non imprimée).
 */
@Component
@RequiredArgsConstructor
public class LearnerDocumentPdfGenerator {

    private static final Color NAVY = new Color(0x1A, 0x3A, 0x5B);
    private static final Color GRAY = new Color(0x5A, 0x65, 0x78);
    private static final Color LINE = new Color(0xE4, 0xDD, 0xD0);
    private static final Color BG = new Color(0xF3, 0xEF, 0xE8);

    private static final Font TITLE = new Font(Font.HELVETICA, 22, Font.BOLD, NAVY);
    private static final Font H = new Font(Font.HELVETICA, 10, Font.BOLD, NAVY);
    private static final Font LABEL = new Font(Font.HELVETICA, 8, Font.BOLD, GRAY);
    private static final Font N = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(0x2D, 0x37, 0x48));
    private static final Font B = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(0x1F, 0x29, 0x37));
    private static final Font BIG = new Font(Font.HELVETICA, 16, Font.BOLD, NAVY);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, GRAY);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final String LOGO = "certificates/logo.png";

    private final InvoiceProperties properties;

    @Value("${app.certificate.signatory-name:Patrick Lionnel DOOKO}")
    private String signatoryName;

    @Value("${app.certificate.signatory-title:Gérant}")
    private String signatoryTitle;

    public record Receipt(String number, LocalDate date, String payerName, String payerEmail, String formationTitle,
                          String sessionName, int installmentNumber, int installmentCount, long amount, String currency,
                          String methodLabel, String reference, long totalAmount, long paidSoFar) {}

    public record Attestation(String learnerName, String formationTitle, String duration, String sessionName,
                              LocalDate startDate, LocalDate endDate, String trainerName, LocalDate issuedOn) {}

    // ── Reçu de paiement ─────────────────────────────────────────────

    public byte[] receipt(Receipt r) {
        return render("Reçu de paiement " + r.number(), doc -> {
            doc.add(header("REÇU DE PAIEMENT", "N° " + r.number()));

            PdfPTable box = table(1);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(BG);
            cell.setBorderColor(LINE);
            cell.setPadding(14);
            cell.addElement(new Paragraph("Montant reçu", LABEL));
            cell.addElement(new Paragraph(money(r.amount(), r.currency()), BIG));
            cell.addElement(new Paragraph("Le " + DAY.format(r.date()), N));
            box.addCell(cell);
            box.setSpacingBefore(14);
            doc.add(box);

            PdfPTable details = table(2);
            details.setSpacingBefore(18);
            row(details, "Reçu de", r.payerName() + (r.payerEmail() != null ? "  (" + r.payerEmail() + ")" : ""));
            row(details, "Formation", r.formationTitle());
            if (r.sessionName() != null && !r.sessionName().isBlank()) row(details, "Session", r.sessionName());
            row(details, "Échéance", r.installmentCount() > 1
                    ? r.installmentNumber() + " sur " + r.installmentCount() : "Paiement unique");
            if (r.methodLabel() != null) row(details, "Moyen de paiement", r.methodLabel());
            if (r.reference() != null && !r.reference().isBlank()) row(details, "Référence", r.reference());
            row(details, "Total de l'inscription", money(r.totalAmount(), r.currency()));
            row(details, "Déjà réglé (ce reçu inclus)", money(r.paidSoFar(), r.currency()));
            long left = Math.max(0, r.totalAmount() - r.paidSoFar());
            row(details, "Reste à régler", left == 0 ? "Soldé" : money(left, r.currency()));
            doc.add(details);

            doc.add(signature());
            doc.add(note("Ce reçu atteste de l'encaissement du paiement ci-dessus. Il ne remplace pas une facture."));
        });
    }

    // ── Attestation d'inscription ────────────────────────────────────

    public byte[] attestation(Attestation a) {
        return render("Attestation d'inscription", doc -> {
            doc.add(header("ATTESTATION D'INSCRIPTION", "Délivrée le " + DAY.format(a.issuedOn())));

            Paragraph intro = new Paragraph("Nous soussignés, " + sellerName() + ", attestons que :", N);
            intro.setSpacingBefore(30);
            doc.add(intro);

            Paragraph name = new Paragraph(a.learnerName(), BIG);
            name.setAlignment(Element.ALIGN_CENTER);
            name.setSpacingBefore(18);
            name.setSpacingAfter(18);
            doc.add(name);

            doc.add(new Paragraph("est régulièrement inscrit(e) à la formation :", N));
            Paragraph formation = new Paragraph(a.formationTitle(), H);
            formation.setSpacingBefore(8);
            doc.add(formation);

            PdfPTable details = table(2);
            details.setSpacingBefore(16);
            if (a.sessionName() != null && !a.sessionName().isBlank()) row(details, "Session", a.sessionName());
            if (a.startDate() != null) {
                row(details, "Période", a.endDate() != null
                        ? "du " + DAY.format(a.startDate()) + " au " + DAY.format(a.endDate())
                        : "à partir du " + DAY.format(a.startDate()));
            }
            if (a.duration() != null && !a.duration().isBlank()) row(details, "Durée", a.duration());
            if (a.trainerName() != null) row(details, "Formateur", a.trainerName());
            doc.add(details);

            Paragraph purpose = new Paragraph("La présente attestation est délivrée pour servir et valoir ce que de droit.", N);
            purpose.setSpacingBefore(26);
            doc.add(purpose);
            doc.add(signature());
        });
    }

    // ── Blocs ────────────────────────────────────────────────────────

    private interface Body { void write(Document doc) throws DocumentException; }

    private byte[] render(String title, Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 40, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle(title);
            document.addAuthor(sellerName());
            document.open();
            body.write(document);
            footer(writer);
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Impossible de générer le document « " + title + " »", e);
        }
    }

    private PdfPTable header(String title, String subtitle) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1f, 2.2f});
        t.setWidthPercentage(100);
        PdfPCell logo = new PdfPCell();
        logo.setBorder(Rectangle.NO_BORDER);
        try (InputStream in = new ClassPathResource(LOGO).getInputStream()) {
            Image image = Image.getInstance(in.readAllBytes());
            image.scaleToFit(92, 60);
            logo.addElement(image);
        } catch (IOException e) {
            logo.addElement(new Paragraph(sellerName(), H));
        }
        t.addCell(logo);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph p = new Paragraph(title, TITLE);
        p.setAlignment(Element.ALIGN_RIGHT);
        Paragraph s = new Paragraph(subtitle, N);
        s.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(p);
        right.addElement(s);
        t.addCell(right);
        return t;
    }

    private Paragraph signature() {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(40);
        p.setAlignment(Element.ALIGN_RIGHT);
        p.add(new Phrase("Pour " + sellerName() + "\n", N));
        p.add(new Phrase(signatoryName + "\n", B));
        p.add(new Phrase(signatoryTitle, SMALL));
        return p;
    }

    private Paragraph note(String text) {
        Paragraph p = new Paragraph(text, SMALL);
        p.setSpacingBefore(18);
        return p;
    }

    private void footer(PdfWriter writer) {
        InvoiceProperties.Seller s = properties.getSeller();
        StringBuilder line = new StringBuilder(sellerName());
        append(line, s.getAddress());
        append(line, s.getPhone());
        append(line, s.getTaxId().isBlank() ? "" : "NINEA " + s.getTaxId());
        append(line, s.getRegisterNumber().isBlank() ? "" : "RCCM " + s.getRegisterNumber());
        com.lowagie.text.pdf.ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER,
                new Phrase(line.toString(), SMALL), PageSize.A4.getWidth() / 2, 28, 0);
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) sb.append("  ·  ").append(part.trim());
    }

    private PdfPTable table(int columns) throws DocumentException {
        PdfPTable t = columns == 2 ? new PdfPTable(new float[]{1f, 2f}) : new PdfPTable(columns);
        t.setWidthPercentage(100);
        return t;
    }

    private void row(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label.toUpperCase(Locale.FRENCH), LABEL));
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "" : value, B));
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(LINE);
            c.setPaddingTop(7);
            c.setPaddingBottom(7);
        }
        t.addCell(l);
        t.addCell(v);
    }

    private String sellerName() {
        return properties.getSeller().getName();
    }

    private static String money(long amount, String currency) {
        String unit = currency == null || currency.equals("XOF") ? "FCFA" : currency;
        return String.format(Locale.FRENCH, "%,d", amount).replace(' ', ' ').replace(' ', ' ') + " " + unit;
    }
}
