package tg.pnlp.planning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * ✅ Service d'envoi d'emails via l'API HTTP Mailjet (port 443).
 *
 * Pourquoi ? Render Free BLOQUE tous les ports SMTP (25, 465, 587).
 * Seule une API HTTP sur port 443 fonctionne.
 */
@Service
@Profile("!local")
@Slf4j
public class MailjetApiService implements EmailSender {

    @Value("${mailjet.api-key}")
    private String apiKey;

    @Value("${mailjet.secret-key}")
    private String secretKey;

    @Value("${mailjet.from-email}")
    private String fromEmail;

    @Value("${mailjet.from-name:PNLP Planning}")
    private String fromName;

    private HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank() || "test-local".equals(apiKey)) {
            throw new IllegalStateException(
                    "❌ MAILJET_API_KEY manquante ou invalide. Configure la variable d'environnement.");
        }
        if (secretKey == null || secretKey.isBlank() || "test-local".equals(secretKey)) {
            throw new IllegalStateException(
                    "❌ MAILJET_SECRET_KEY manquante ou invalide. Configure la variable d'environnement.");
        }

        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("✅ MailjetApiService initialisé (from: {})", fromEmail);
    }

    // ============================================================
    // ✅ Signature historique : 1 seule pièce jointe
    // ============================================================
    @Override
    public void sendEmail(String to, String subject, String textBody, String htmlBody,
                          String attachmentName, byte[] attachmentContent) {

        List<Attachment> atts = (attachmentName != null && attachmentContent != null)
                ? List.of(new Attachment(attachmentName, attachmentContent))
                : List.of();

        sendEmail(to, subject, textBody, htmlBody, atts);
    }

    // ============================================================
    // ✅ NOUVELLE signature : plusieurs pièces jointes
    // ============================================================
    @Override
    public void sendEmail(String to, String subject, String textBody, String htmlBody,
                          List<Attachment> attachments) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            ArrayNode messages = payload.putArray("Messages");
            ObjectNode message = messages.addObject();

            ObjectNode from = message.putObject("From");
            from.put("Email", fromEmail);
            from.put("Name", fromName);

            ArrayNode toArray = message.putArray("To");
            ObjectNode toObj = toArray.addObject();
            toObj.put("Email", to);

            message.put("Subject", subject);
            if (textBody != null && !textBody.isBlank()) message.put("TextPart", textBody);
            if (htmlBody != null && !htmlBody.isBlank()) message.put("HTMLPart", htmlBody);

            if (attachments != null && !attachments.isEmpty()) {
                ArrayNode attsArray = message.putArray("Attachments");
                for (Attachment att : attachments) {
                    ObjectNode attNode = attsArray.addObject();
                    attNode.put("ContentType", "application/octet-stream");
                    attNode.put("Filename", att.filename());
                    attNode.put("Base64Content", Base64.getEncoder().encodeToString(att.content()));
                }
            }

            String json = objectMapper.writeValueAsString(payload);
            String auth = Base64.getEncoder().encodeToString(
                    (apiKey + ":" + secretKey).getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mailjet.com/v3.1/send"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("✅ Email envoyé via Mailjet API à {} ({} pièce(s) jointe(s))",
                        to, attachments != null ? attachments.size() : 0);
            } else {
                log.error("❌ Erreur Mailjet API ({}): {}", response.statusCode(), response.body());
                throw new RuntimeException("Erreur Mailjet: " + response.body());
            }
        } catch (Exception e) {
            log.error("❌ Échec envoi email à {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Impossible d'envoyer l'email: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendSimpleEmail(String to, String subject, String textBody) {
        sendEmail(to, subject, textBody, null, null, null);
    }
}