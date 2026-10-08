package com.modeltech.datamasteryhub.modules.course;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.modeltech.datamasteryhub.modules.course.service.CertificatePdfGenerator;
import com.modeltech.datamasteryhub.modules.course.service.CertificatePdfGenerator.CertificateDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Le PDF est un vrai document A4 paysage contenant les informations figées, rendu en PNG pour contrôle visuel. */
class CertificatePdfGeneratorTest {

    private final CertificatePdfGenerator generator = new CertificatePdfGenerator();

    private CertificateDocument sample(String name, String trainer) {
        return new CertificateDocument(
                "MT-2026-VBA-00042-K7QX", name,
                "Excel VBA — Automatiser ses tâches", "4 semaines, 32 h",
                List.of("macros", "procédures VBA", "formulaires", "automatisation de rapports"), true,
                "Patrick Lionnel DOOKO", "Gérant, Model Technologie", trainer,
                LocalDateTime.of(2026, 9, 30, 10, 0),
                "https://www.model-technologie.com/certificats/MT-2026-VBA-00042-K7QX");
    }

    @Test
    void generatesAReadablePdf_withEveryFigedField() throws Exception {
        byte[] pdf = generator.generate(sample("Awa Ndiaye", "Moussa Fall"));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        assertThat(reader.getPageSize(1).getWidth()).isGreaterThan(reader.getPageSize(1).getHeight());   // paysage
        String text = new PdfTextExtractor(reader).getTextFromPage(1);
        assertThat(text).contains("CERTIFICAT DE R", "Awa Ndiaye", "Excel VBA", "4 semaines, 32 h",
                "projet final", "Patrick Lionnel DOOKO", "Gérant, Model Technologie", "Moussa Fall", "MT-2026-VBA-00042-K7QX",
                "30 sept. 2026", "model-technologie.com/certificats/MT-2026-VBA-00042-K7QX");

        // Aperçu pour contrôle visuel de la mise en page
        try (PDDocument document = PDDocument.load(pdf)) {
            File png = Path.of("target", "certificate-preview.png").toFile();
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 110), "png", png);
            assertThat(png).exists();
        }
        Files.write(Path.of("target", "certificate-preview.pdf"), pdf);
    }

    @Test
    void longNamesAreShrunkToFit_andOptionalPartsAreOmitted() throws Exception {
        CertificateDocument doc = new CertificateDocument(
                "MT-2026-PBI-00001-AAAA", "Mamadou Abdoulaye Ibrahima Sékou Ndiaye-Diallo de la Fontaine",
                "Power BI", null, List.of(), false,
                "Patrick Lionnel DOOKO", "Gérant, Model Technologie", null,
                LocalDateTime.of(2026, 1, 5, 9, 0), "https://example.com/certificats/MT-2026-PBI-00001-AAAA");

        byte[] pdf = generator.generate(doc);

        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("Mamadou Abdoulaye Ibrahima S", "Power BI").doesNotContain("incluant un projet").doesNotContain("Formateur");
        try (PDDocument document = PDDocument.load(pdf)) {
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0, 80), "png", Path.of("target", "certificate-preview-long.png").toFile());
        }
    }
}
