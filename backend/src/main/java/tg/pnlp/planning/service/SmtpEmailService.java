package tg.pnlp.planning.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

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
            MimeMessage message = mailSender.createMimeMessage();
            boolean multipart = attachments != null && !attachments.isEmpty();
            MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(fromEmail);

            if (htmlBody != null && !htmlBody.isBlank()) {
                helper.setText(textBody != null ? textBody : "", htmlBody);
            } else {
                helper.setText(textBody != null ? textBody : "", false);
            }

            if (multipart) {
                for (Attachment att : attachments) {
                    helper.addAttachment(att.filename(), new ByteArrayResource(att.content()));
                }
            }

            mailSender.send(message);
            log.info("✅ Email envoyé via SMTP (local) à {} ({} pièce(s) jointe(s))",
                    to, attachments != null ? attachments.size() : 0);
        } catch (Exception e) {
            log.error("❌ Échec envoi email SMTP à {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Impossible d'envoyer l'email: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendSimpleEmail(String to, String subject, String textBody) {
        sendEmail(to, subject, textBody, null, null, null);
    }
}