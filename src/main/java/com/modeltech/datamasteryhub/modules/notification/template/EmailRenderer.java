package com.modeltech.datamasteryhub.modules.notification.template;

import com.modeltech.datamasteryhub.config.InvoiceProperties;
import com.modeltech.datamasteryhub.modules.notification.template.EmailContent.Block;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Met en page un {@link EmailContent} : version HTML (gabarit {@code email/layout.html}, mise en page à une
 * colonne de 570 px, styles en ligne, compatible Gmail / Outlook / Apple Mail / mobile) et version texte brut.
 * Les deux versions partent ensemble ({@code multipart/alternative}) : un client sans HTML affiche le texte.
 *
 * <p>Charte : marine {@code #1A3A5B}, orange d'action {@code #C73A08}, fond sable {@code #F6F3EE} (mêmes
 * couleurs que le site). Toute valeur insérée est échappée.
 */
@Component
public class EmailRenderer {

    private static final String NAVY = "#1A3A5B";
    private static final String CTA = "#C73A08";
    private static final String INK = "#16202E";
    private static final String MUTED = "#4A5566";
    private static final String SAND = "#F6F3EE";
    private static final String BORDER = "#E7E0D4";
    private static final String FONT = "'DM Sans',-apple-system,'Segoe UI',Helvetica,Arial,sans-serif";

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*", Pattern.DOTALL);
    private static final Pattern URL = Pattern.compile("(https?://[^\\s<]*[^\\s<.,;:!?)\\]])");

    /** Identité affichée dans l'en-tête et le pied de page. */
    public record Brand(String name, String homeUrl, String logoUrl, String address, String phone, String email, String website) {}

    public record Rendered(String html, String text) {}

    private final Brand brand;
    private final String layout;

    @Autowired
    public EmailRenderer(InvoiceProperties invoice,
                         @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this(brandFrom(invoice, frontendUrl));
    }

    public EmailRenderer(Brand brand) {
        this.brand = brand;
        try {
            this.layout = new String(new ClassPathResource("email/layout.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Gabarit d'e-mail introuvable (email/layout.html)", e);
        }
    }

    private static Brand brandFrom(InvoiceProperties invoice, String frontendUrl) {
        InvoiceProperties.Seller s = invoice.getSeller();
        String base = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        return new Brand(blankTo(s.getName(), "Model Technologie"), base, base + "/logo.png",
                s.getAddress(), s.getPhone(), s.getEmail(), s.getWebsite());
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public Brand brand() {
        return brand;
    }

    // =========================================================================
    //  RENDU
    // =========================================================================

    public Rendered render(EmailContent content) {
        return new Rendered(html(content), text(content));
    }

    private String html(EmailContent c) {
        StringBuilder body = new StringBuilder();
        if (notBlank(c.greeting())) body.append(paragraph(c.greeting()));
        for (Block block : c.blocks()) body.append(block(block));
        if (notBlank(c.signature())) {
            body.append("<p style=\"margin:24px 0 0 0;color:").append(INK).append(";\">").append(inline(c.signature())).append("</p>");
        }
        if (notBlank(c.footnote())) {
            body.append("<div style=\"margin-top:28px;padding-top:18px;border-top:1px solid ").append(BORDER)
                    .append(";font-size:13px;line-height:1.6;color:").append(MUTED).append(";\">")
                    .append(inline(c.footnote())).append("</div>");
        }
        return layout
                .replace("{{preheader}}", esc(c.preheader()))
                .replace("{{homeUrl}}", esc(brand.homeUrl()))
                .replace("{{logoUrl}}", esc(brand.logoUrl()))
                .replace("{{brand}}", esc(brand.name()))
                .replace("{{footer}}", footerHtml())
                .replace("{{title}}", esc(c.title()))
                .replace("{{body}}", body.toString());   // en dernier : le texte saisi ne doit pas être réinterprété
    }

    private String block(Block block) {
        if (block instanceof EmailContent.Paragraph p) return paragraph(p.text());
        if (block instanceof EmailContent.Details d) return details(d);
        if (block instanceof EmailContent.Button b) return button(b);
        if (block instanceof EmailContent.Code c) return code(c);
        if (block instanceof EmailContent.Quote q) return quote(q);
        EmailContent.Note n = (EmailContent.Note) block;
        return "<p style=\"margin:0 0 16px 0;font-size:13px;line-height:1.6;color:" + MUTED + ";\">" + inline(n.text()) + "</p>";
    }

    private String paragraph(String text) {
        return "<p style=\"margin:0 0 16px 0;color:" + INK + ";\">" + inline(text) + "</p>";
    }

    private String details(EmailContent.Details d) {
        StringBuilder rows = new StringBuilder();
        for (EmailContent.Entry e : d.entries()) {
            rows.append("<tr><td valign=\"top\" style=\"padding:7px 16px 7px 0;width:38%;font-size:14px;line-height:1.5;color:").append(MUTED)
                    .append(";\">").append(esc(e.label())).append("</td><td valign=\"top\" style=\"padding:7px 0;font-size:15px;line-height:1.5;font-weight:700;color:")
                    .append(INK).append(";\">").append(inline(e.value())).append("</td></tr>");
        }
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:8px 0 22px 0;background-color:"
                + SAND + ";border:1px solid " + BORDER + ";border-radius:10px;\"><tr><td style=\"padding:12px 20px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">" + rows + "</table></td></tr></table>";
    }

    private String button(EmailContent.Button b) {
        String bg = b.primary() ? CTA : "#FFFFFF";
        String color = b.primary() ? "#FFFFFF" : NAVY;
        String border = b.primary() ? CTA : NAVY;
        StringBuilder out = new StringBuilder();
        out.append("<table role=\"presentation\" class=\"btn\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:10px auto 18px auto;\"><tr>")
                .append("<td align=\"center\" bgcolor=\"").append(bg).append("\" style=\"border-radius:999px;background-color:").append(bg).append(";\">")
                .append("<a href=\"").append(esc(b.url())).append("\" target=\"_blank\" style=\"display:inline-block;padding:14px 34px;border:2px solid ")
                .append(border).append(";border-radius:999px;font-family:").append(FONT).append(";font-size:16px;font-weight:700;line-height:1.2;color:")
                .append(color).append(";text-decoration:none;\">").append(esc(b.label())).append("</a></td></tr></table>");
        if (b.primary()) {
            out.append("<p style=\"margin:0 0 18px 0;font-size:13px;line-height:1.6;color:").append(MUTED)
                    .append(";\">Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :<br><a href=\"").append(esc(b.url()))
                    .append("\" style=\"color:").append(CTA).append(";word-break:break-all;\">").append(esc(b.url())).append("</a></p>");
        }
        return out.toString();
    }

    private String code(EmailContent.Code c) {
        return "<div style=\"margin:8px 0 22px 0;padding:18px 16px;text-align:center;background-color:" + SAND + ";border:1px dashed " + NAVY + ";border-radius:10px;\">"
                + (notBlank(c.label()) ? "<div style=\"margin:0 0 6px 0;font-size:13px;color:" + MUTED + ";\">" + esc(c.label()) + "</div>" : "")
                + "<div style=\"font-family:'SFMono-Regular',Consolas,'Liberation Mono',Menlo,monospace;font-size:30px;line-height:1.2;font-weight:700;letter-spacing:8px;color:" + NAVY + ";\">"
                + esc(c.value()) + "</div></div>";
    }

    private String quote(EmailContent.Quote q) {
        return "<div style=\"margin:4px 0 20px 0;padding:6px 0 6px 16px;border-left:3px solid " + BORDER + ";color:" + MUTED + ";\">"
                + inline(q.text())
                + (notBlank(q.author()) ? "<div style=\"margin-top:6px;font-size:13px;font-weight:700;color:" + INK + ";\">— " + esc(q.author()) + "</div>" : "")
                + "</div>";
    }

    private String footerHtml() {
        List<String> contact = new ArrayList<>();
        if (notBlank(brand.address())) contact.add(esc(brand.address()));
        if (notBlank(brand.phone())) contact.add(esc(brand.phone()));
        if (notBlank(brand.email())) contact.add("<a href=\"mailto:" + esc(brand.email()) + "\" style=\"color:#6B7585;\">" + esc(brand.email()) + "</a>");
        String site = notBlank(brand.website()) ? brand.website() : brand.homeUrl();
        if (notBlank(site)) contact.add("<a href=\"" + esc(site) + "\" style=\"color:#6B7585;\">" + esc(site.replaceFirst("^https?://", "")) + "</a>");
        return "<strong style=\"color:" + MUTED + ";\">" + esc(brand.name()) + "</strong>"
                + (contact.isEmpty() ? "" : "<br>" + String.join(" &nbsp;·&nbsp; ", contact));
    }

    // =========================================================================
    //  TEXTE BRUT
    // =========================================================================

    private String text(EmailContent c) {
        List<String> parts = new ArrayList<>();
        parts.add(c.title());
        if (notBlank(c.greeting())) parts.add(plain(c.greeting()));
        for (Block block : c.blocks()) parts.add(plainBlock(block));
        if (notBlank(c.signature())) parts.add(plain(c.signature()));
        if (notBlank(c.footnote())) parts.add("---\n" + plain(c.footnote()));
        StringBuilder footer = new StringBuilder("--\n").append(brand.name());
        if (notBlank(brand.address())) footer.append("\n").append(brand.address());
        if (notBlank(brand.phone())) footer.append("\n").append(brand.phone());
        if (notBlank(brand.email())) footer.append("\n").append(brand.email());
        parts.add(footer.toString());
        return String.join("\n\n", parts) + "\n";
    }

    private String plainBlock(Block block) {
        if (block instanceof EmailContent.Paragraph p) return plain(p.text());
        if (block instanceof EmailContent.Details d) {
            return d.entries().stream().map(e -> e.label() + " : " + plain(e.value())).collect(Collectors.joining("\n"));
        }
        if (block instanceof EmailContent.Button b) return b.label() + " :\n" + b.url();
        if (block instanceof EmailContent.Code c) return (notBlank(c.label()) ? c.label() + " : " : "") + c.value();
        if (block instanceof EmailContent.Quote q) {
            return plain(q.text()).lines().map(l -> "> " + l).collect(Collectors.joining("\n"))
                    + (notBlank(q.author()) ? "\n— " + q.author() : "");
        }
        return plain(((EmailContent.Note) block).text());
    }

    /** Texte sans la mise en forme {@code **gras**}. */
    private static String plain(String s) {
        return BOLD.matcher(s == null ? "" : s).replaceAll("$1");
    }

    // =========================================================================
    //  OUTILS
    // =========================================================================

    /** Échappe, met en gras {@code **…**}, rend les adresses cliquables et conserve les retours à la ligne. */
    static String inline(String raw) {
        if (raw == null) return "";
        String s = esc(raw);
        s = BOLD.matcher(s).replaceAll("<strong>$1</strong>");
        Matcher m = URL.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String url = m.group(1);
            m.appendReplacement(out, Matcher.quoteReplacement(
                    "<a href=\"" + url + "\" style=\"color:" + CTA + ";word-break:break-all;\">" + url + "</a>"));
        }
        m.appendTail(out);
        return out.toString().replace("\r\n", "\n").replace("\n", "<br>");
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
