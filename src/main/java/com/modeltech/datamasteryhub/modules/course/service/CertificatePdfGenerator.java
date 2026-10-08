package com.modeltech.datamasteryhub.modules.course.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dessine le certificat (A4 paysage) : logo, numéro, nom du lauréat, formation, signataire, QR de vérification.
 * Mise en page calquée sur la maquette (fond crème, cadre bleu nuit, libellé orange).
 */
@Component
public class CertificatePdfGenerator {

    /** Ce qui est imprimé : une photographie figée à la délivrance. */
    public record CertificateDocument(
            String publicId,
            String recipientName,
            String formationTitle,
            String durationLabel,
            List<String> skills,
            boolean includesProject,
            String signatoryName,
            String signatoryTitle,
            String trainerName,
            LocalDateTime issuedAt,
            String verifyUrl) {}

    private static final Color CREAM = new Color(0xFA, 0xF7, 0xF2);
    private static final Color NAVY = new Color(0x1A, 0x3A, 0x5B);
    private static final Color ORANGE = new Color(0xD4, 0x38, 0x0D);
    private static final Color GRAY = new Color(0x5A, 0x65, 0x78);
    private static final Color LIGHT_GRAY = new Color(0x8A, 0x93, 0xA3);
    private static final Color BORDER_LIGHT = new Color(0xE4, 0xDD, 0xD0);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH);
    private static final String LOGO = "certificates/logo.png";

    /** Les coordonnées de la maquette (730 × 513 px) sont mises à l'échelle de la page A4 paysage. */
    private static final float W = PageSize.A4.getHeight();   // 842
    private static final float H = PageSize.A4.getWidth();    // 595
    private static final float SX = W / 730f;
    private static final float SY = H / 513f;

    public byte[] generate(CertificateDocument doc) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(new Rectangle(W, H), 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.addTitle("Certificat de réussite — " + doc.recipientName());
            document.addAuthor("Model Technologie");
            document.addSubject(doc.publicId());
            document.open();
            PdfContentByte cb = writer.getDirectContent();

            drawFrame(cb);
            drawLogo(cb);
            drawHeader(cb, doc);
            drawRecipient(cb, doc);
            drawDescription(cb, doc);
            drawSkills(cb, doc);
            drawSignatures(cb, doc);
            drawVerification(cb, doc);

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Impossible de générer le certificat " + doc.publicId(), e);
        }
    }

    // ── Cadre et logo ────────────────────────────────────────────────

    private void drawFrame(PdfContentByte cb) {
        cb.setColorFill(CREAM);
        cb.rectangle(0, 0, W, H);
        cb.fill();

        cb.setColorStroke(NAVY);
        cb.setLineWidth(1.6f);
        float margin = 17 * SX;
        cb.roundRectangle(margin, margin, W - 2 * margin, H - 2 * margin, 12);
        cb.stroke();
    }

    private void drawLogo(PdfContentByte cb) throws IOException, DocumentException {
        try (InputStream in = new ClassPathResource(LOGO).getInputStream()) {
            Image logo = Image.getInstance(in.readAllBytes());
            logo.scaleToFit(104, 88);
            logo.setAbsolutePosition(48, H - 34 - logo.getScaledHeight());
            cb.addImage(logo);
        }
    }

    // ── Texte ────────────────────────────────────────────────────────

    private void drawHeader(PdfContentByte cb, CertificateDocument doc) throws IOException, DocumentException {
        BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
        text(cb, bold, 8.5f, GRAY, 1f, Element.ALIGN_RIGHT, W - 60 * SX, H - 68 * SY, "N° " + doc.publicId());
        text(cb, bold, 10.5f, ORANGE, 2.4f, Element.ALIGN_LEFT, 60 * SX, H - 117 * SY, "CERTIFICAT DE RÉUSSITE");
    }

    private void drawRecipient(PdfContentByte cb, CertificateDocument doc) throws IOException, DocumentException {
        BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
        BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);
        text(cb, regular, 11, GRAY, 0, Element.ALIGN_LEFT, 60 * SX, H - 148 * SY, "Décerné à");

        // Le nom est réduit s'il ne tient pas sur une ligne
        float maxWidth = W - 2 * 60 * SX;
        float size = 54;
        while (size > 24 && bold.getWidthPoint(doc.recipientName(), size) > maxWidth) size -= 2;
        text(cb, bold, size, NAVY, 0, Element.ALIGN_LEFT, 58 * SX, H - 200 * SY, doc.recipientName());
    }

    private void drawDescription(PdfContentByte cb, CertificateDocument doc) throws DocumentException {
        Font normal = new Font(Font.HELVETICA, 12, Font.NORMAL, new Color(0x2D, 0x37, 0x48));
        Font strong = new Font(Font.HELVETICA, 12, Font.BOLD, new Color(0x1F, 0x29, 0x37));
        Phrase phrase = new Phrase();
        phrase.setLeading(17);
        phrase.add(new Chunk("pour avoir suivi et validé la formation ", normal));
        phrase.add(new Chunk(doc.formationTitle(), strong));
        if (doc.durationLabel() != null && !doc.durationLabel().isBlank()) {
            phrase.add(new Chunk(" (" + doc.durationLabel() + ")", normal));
        }
        phrase.add(new Chunk(doc.includesProject() ? ", incluant un projet final évalué." : ".", normal));

        ColumnText column = new ColumnText(cb);
        column.setSimpleColumn(60 * SX, H - 272 * SY, 60 * SX + 460 * SX, H - 214 * SY);
        column.setLeading(17);
        column.addText(phrase);
        column.go();
    }

    private void drawSkills(PdfContentByte cb, CertificateDocument doc) throws IOException, DocumentException {
        if (doc.skills() == null || doc.skills().isEmpty()) return;
        BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
        String line = "Compétences : " + String.join(" · ", doc.skills().stream().limit(6).toList());
        float maxWidth = 460 * SX;
        while (line.length() > 20 && regular.getWidthPoint(line, 8.5f) > maxWidth) {
            line = line.substring(0, line.length() - 4).trim() + "…";
        }
        text(cb, regular, 8.5f, GRAY, 0, Element.ALIGN_LEFT, 60 * SX, H - 288 * SY, line);
    }

    private void drawSignatures(PdfContentByte cb, CertificateDocument doc) throws IOException, DocumentException {
        BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
        BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, false);

        float x = 60 * SX;
        text(cb, bold, 12, NAVY, 0, Element.ALIGN_LEFT, x, H - 432 * SY, doc.signatoryName());
        text(cb, regular, 8, GRAY, 0, Element.ALIGN_LEFT, x, H - 449 * SY, doc.signatoryTitle());

        if (doc.trainerName() != null && !doc.trainerName().isBlank()) {
            float second = x + Math.max(bold.getWidthPoint(doc.signatoryName(), 12), regular.getWidthPoint(doc.signatoryTitle(), 8)) + 28;
            text(cb, bold, 12, NAVY, 0, Element.ALIGN_LEFT, second, H - 432 * SY, doc.trainerName());
            text(cb, regular, 8, GRAY, 0, Element.ALIGN_LEFT, second, H - 449 * SY, "Formateur");
        }
    }

    private void drawVerification(PdfContentByte cb, CertificateDocument doc) throws IOException, DocumentException {
        BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);

        // Boîte du QR code, en bas à droite
        float box = 66 * SX;
        float boxX = W - 60 * SX - box;
        float boxY = H - 453 * SY;
        cb.setColorFill(Color.WHITE);
        cb.setColorStroke(BORDER_LIGHT);
        cb.setLineWidth(0.8f);
        cb.roundRectangle(boxX, boxY, box, box, 8);
        cb.fillStroke();
        drawQr(cb, doc.verifyUrl(), boxX + 7, boxY + 7, box - 14);

        float right = boxX - 14;
        text(cb, regular, 8, GRAY, 0, Element.ALIGN_RIGHT, right, H - 434 * SY, "Délivré le " + DAY.format(doc.issuedAt()));
        text(cb, regular, 7.5f, LIGHT_GRAY, 0, Element.ALIGN_RIGHT, right, H - 449 * SY, "Vérifier : " + withoutScheme(doc.verifyUrl()));
    }

    private void drawQr(PdfContentByte cb, String content, float x, float y, float size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 0));
            int n = matrix.getWidth();
            float cell = size / n;
            cb.setColorFill(NAVY);
            for (int row = 0; row < n; row++) {
                for (int col = 0; col < n; col++) {
                    if (matrix.get(col, row)) {
                        cb.rectangle(x + col * cell, y + size - (row + 1) * cell, cell + 0.15f, cell + 0.15f);
                    }
                }
            }
            cb.fill();
        } catch (com.google.zxing.WriterException e) {
            throw new IllegalStateException("QR code impossible pour " + content, e);
        }
    }

    // ── Outils ───────────────────────────────────────────────────────

    private void text(PdfContentByte cb, BaseFont font, float size, Color color, float charSpacing,
                      int align, float x, float y, String value) {
        cb.beginText();
        cb.setFontAndSize(font, size);
        cb.setColorFill(color);
        cb.setCharacterSpacing(charSpacing);
        cb.showTextAligned(align, value, x, y, 0);
        cb.endText();
    }

    private String withoutScheme(String url) {
        return url == null ? "" : url.replaceFirst("^https?://", "");
    }
}
