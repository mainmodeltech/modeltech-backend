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
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.modeltech.datamasteryhub.modules.training.entity.Invoice;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Dessine la facture (A4 portrait) à partir d'une {@link Invoice} figée. */
@Component
public class InvoicePdfGenerator {

    private static final Color NAVY = new Color(0x1A, 0x3A, 0x5B);
    private static final Color ORANGE = new Color(0xD4, 0x38, 0x0D);
    private static final Color GRAY = new Color(0x5A, 0x65, 0x78);
    private static final Color LINE = new Color(0xE4, 0xDD, 0xD0);
    private static final Color HEAD_BG = new Color(0xF3, 0xEF, 0xE8);

    private static final Font TITLE = new Font(Font.HELVETICA, 22, Font.BOLD, NAVY);
    private static final Font H = new Font(Font.HELVETICA, 9, Font.BOLD, NAVY);
    private static final Font LABEL = new Font(Font.HELVETICA, 7.5f, Font.BOLD, GRAY);
    private static final Font N = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(0x2D, 0x37, 0x48));
    private static final Font B = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(0x1F, 0x29, 0x37));
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.NORMAL, GRAY);
    private static final Font TOTAL = new Font(Font.HELVETICA, 12, Font.BOLD, NAVY);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH);

    public byte[] generate(Invoice inv) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 36, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle("Facture " + inv.getNumber());
            document.addAuthor(seller(inv, "name"));
            document.open();

            document.add(header(inv));
            document.add(parties(inv));
            document.add(lines(inv));
            document.add(totals(inv));
            if (inv.getInstallments() != null && !inv.getInstallments().isEmpty()) document.add(installments(inv));
            document.add(paymentInfo(inv));
            if (inv.getNotes() != null && !inv.getNotes().isBlank()) {
                Paragraph notes = new Paragraph("Notes : " + inv.getNotes(), SMALL);
                notes.setSpacingBefore(10);
                document.add(notes);
            }
            footer(writer.getDirectContent(), inv);
            if (!inv.isIssued()) watermark(writer.getDirectContentUnder());
            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Impossible de générer la facture " + inv.getNumber(), e);
        }
    }

    // ── Blocs ────────────────────────────────────────────────────────

    private PdfPTable header(Invoice inv) throws IOException, DocumentException {
        PdfPTable t = new PdfPTable(new float[]{1.1f, 1f});
        t.setWidthPercentage(100);
        PdfPCell left = cell(Rectangle.NO_BORDER);
        try (InputStream in = new ClassPathResource("certificates/logo.png").getInputStream()) {
            Image logo = Image.getInstance(in.readAllBytes());
            logo.scaleToFit(96, 80);
            left.addElement(logo);
        }
        t.addCell(left);

        PdfPCell right = cell(Rectangle.NO_BORDER);
        Paragraph title = new Paragraph("FACTURE", TITLE);
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        right.addElement(rightLine("N° " + inv.getNumber(), B));
        right.addElement(rightLine("Émise le " + DAY.format(inv.getIssueDate()), N));
        if (inv.getDueDate() != null) right.addElement(rightLine("Échéance : " + DAY.format(inv.getDueDate()), N));
        t.addCell(right);
        return t;
    }

    private PdfPTable parties(Invoice inv) {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(14);

        PdfPCell from = cell(Rectangle.NO_BORDER);
        from.addElement(new Phrase("ÉMETTEUR", LABEL));
        from.addElement(new Paragraph(seller(inv, "name"), B));
        for (String key : new String[]{"address", "phone", "email", "website"}) addIfPresent(from, seller(inv, key));
        if (!seller(inv, "taxId").isBlank()) addIfPresent(from, "NINEA : " + seller(inv, "taxId"));
        if (!seller(inv, "registerNumber").isBlank()) addIfPresent(from, "RCCM : " + seller(inv, "registerNumber"));
        t.addCell(from);

        PdfPCell to = cell(Rectangle.NO_BORDER);
        to.addElement(new Phrase("FACTURÉ À", LABEL));
        to.addElement(new Paragraph(inv.getBuyerName(), B));
        addIfPresent(to, inv.getBuyerContact());
        addIfPresent(to, inv.getBuyerAddress());
        addIfPresent(to, inv.getBuyerEmail());
        if (inv.getPurchaseOrderRef() != null && !inv.getPurchaseOrderRef().isBlank()) {
            addIfPresent(to, "Bon de commande : " + inv.getPurchaseOrderRef());
        }
        t.addCell(to);
        return t;
    }

    private PdfPTable lines(Invoice inv) {
        PdfPTable t = new PdfPTable(new float[]{5f, 0.8f, 1.8f, 1.8f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(18);
        for (String h : new String[]{"Désignation", "Qté", "Prix unitaire", "Montant"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, H));
            c.setBackgroundColor(HEAD_BG);
            c.setBorderColor(LINE);
            c.setPadding(6);
            c.setHorizontalAlignment(h.equals("Désignation") ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            t.addCell(c);
        }
        row(t, inv.getDescription(), "1", money(inv.getUnitAmount(), inv), money(inv.getUnitAmount(), inv));
        if (inv.getDiscountAmount() != null && inv.getDiscountAmount() > 0) {
            row(t, inv.getDiscountLabel() != null ? inv.getDiscountLabel() : "Remise", "", "", "- " + money(inv.getDiscountAmount(), inv));
        }
        return t;
    }

    private PdfPTable totals(Invoice inv) {
        PdfPTable t = new PdfPTable(new float[]{3f, 2f});
        t.setWidthPercentage(52);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingBefore(8);
        boolean vat = inv.getVatPercent() != null && inv.getVatPercent().signum() > 0;
        if (vat) {
            totalRow(t, "Total HT", money(inv.getTotalExclVat(), inv), N);
            totalRow(t, "TVA " + inv.getVatPercent().stripTrailingZeros().toPlainString() + " %", money(inv.getVatAmount(), inv), N);
        }
        totalRow(t, vat ? "TOTAL TTC À PAYER" : "TOTAL À PAYER", money(inv.getTotal(), inv), TOTAL);
        return t;
    }

    private PdfPTable installments(Invoice inv) {
        PdfPTable t = new PdfPTable(new float[]{1f, 2f, 2f});
        t.setWidthPercentage(60);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.setSpacingBefore(16);
        PdfPCell title = new PdfPCell(new Phrase("ÉCHÉANCIER", LABEL));
        title.setColspan(3);
        title.setBorder(Rectangle.NO_BORDER);
        title.setPaddingBottom(4);
        t.addCell(title);
        for (Invoice.Installment i : inv.getInstallments()) {
            t.addCell(plain("Échéance " + i.number(), N));
            t.addCell(plain(LocalDate.parse(i.dueDate()).format(DAY), N));
            PdfPCell amount = plain(money(i.amount(), inv), B);
            amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            t.addCell(amount);
        }
        return t;
    }

    private PdfPTable paymentInfo(Invoice inv) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setSpacingBefore(16);
        String details = seller(inv, "paymentDetails");
        if (!details.isBlank()) {
            PdfPCell c = cell(Rectangle.NO_BORDER);
            c.addElement(new Phrase("MODALITÉS DE RÈGLEMENT", LABEL));
            c.addElement(new Paragraph(details, N));
            t.addCell(c);
        }
        return t;
    }

    private void footer(PdfContentByte cb, Invoice inv) throws IOException, DocumentException {
        String footer = seller(inv, "footer");
        BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
        cb.setLineWidth(0.6f);
        cb.setColorStroke(LINE);
        cb.moveTo(40, 52);
        cb.lineTo(PageSize.A4.getWidth() - 40, 52);
        cb.stroke();
        String line = (footer.isBlank() ? seller(inv, "name") : footer);
        cb.beginText();
        cb.setFontAndSize(font, 7.5f);
        cb.setColorFill(GRAY);
        cb.showTextAligned(Element.ALIGN_CENTER, line.length() > 160 ? line.substring(0, 160) : line, PageSize.A4.getWidth() / 2, 40, 0);
        cb.endText();
    }

    private void watermark(PdfContentByte under) throws IOException, DocumentException {
        BaseFont font = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
        under.beginText();
        under.setFontAndSize(font, 86);
        under.setColorFill(new Color(0xE9, 0xB9, 0xAE));
        under.showTextAligned(Element.ALIGN_CENTER, "ANNULÉE", PageSize.A4.getWidth() / 2, PageSize.A4.getHeight() / 2, 35);
        under.endText();
    }

    // ── Outils ───────────────────────────────────────────────────────

    private PdfPCell cell(int border) {
        PdfPCell c = new PdfPCell();
        c.setBorder(border);
        c.setPadding(0);
        return c;
    }

    private PdfPCell plain(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorderColor(LINE);
        c.setBorderWidth(0.6f);
        c.setPadding(5);
        return c;
    }

    private Paragraph rightLine(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private void addIfPresent(PdfPCell cell, String text) {
        if (text != null && !text.isBlank()) cell.addElement(new Paragraph(text, N));
    }

    private void row(PdfPTable t, String description, String qty, String unit, String amount) {
        t.addCell(plain(description, N));
        PdfPCell q = plain(qty, N);
        q.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(q);
        PdfPCell u = plain(unit, N);
        u.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(u);
        PdfPCell a = plain(amount, B);
        a.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(a);
    }

    private void totalRow(PdfPTable t, String label, String value, Font font) {
        PdfPCell l = new PdfPCell(new Phrase(label, font));
        l.setBorder(Rectangle.NO_BORDER);
        l.setPadding(4);
        PdfPCell v = new PdfPCell(new Phrase(value, font));
        v.setBorder(Rectangle.NO_BORDER);
        v.setPadding(4);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private String seller(Invoice inv, String key) {
        Map<String, String> seller = inv.getSeller();
        String value = seller == null ? null : seller.get(key);
        return value == null ? "" : value;
    }

    /** « 150 000 FCFA » : espace ordinaire (les espaces insécables fines ne sont pas dans la police WinAnsi). */
    static String money(long amount, Invoice inv) {
        String digits = String.format(Locale.FRANCE, "%,d", amount).replace(' ', ' ').replace(' ', ' ');
        String currency = "XOF".equals(inv.getCurrency()) ? "FCFA" : inv.getCurrency();
        return digits + " " + currency;
    }
}
