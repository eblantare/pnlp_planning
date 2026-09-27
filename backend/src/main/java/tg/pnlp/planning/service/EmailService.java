package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tg.pnlp.planning.entity.Utilisateur;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void envoyerEmailReinitialisation(Utilisateur user, String token) {
        try {
            String resetLink = frontendUrl + "/reset-password?token=" + token;

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(user.getEmail());
            helper.setSubject("PNLP Planning - Réinitialisation de votre mot de passe");

            String htmlContent = buildEmailHtml(user, resetLink);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Email de réinitialisation envoyé à: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Erreur envoi email à {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Impossible d'envoyer l'email de réinitialisation", e);
        }
    }

    private String buildEmailHtml(Utilisateur user, String resetLink) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'></head>" +
                "<body style='font-family: Arial, sans-serif; background: #F3F4F6; padding: 20px;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1);'>" +
                "<div style='background: linear-gradient(135deg, #1B5E20 0%, #2E7D32 100%); padding: 30px; text-align: center;'>" +
                "<h1 style='color: white; margin: 0; font-size: 24px;'>PNLP Planning</h1>" +
                "<p style='color: rgba(255,255,255,0.9); margin: 8px 0 0 0; font-size: 14px;'>Programme National de Lutte contre le Paludisme</p>" +
                "</div>" +
                "<div style='padding: 30px;'>" +
                "<h2 style='color: #1B5E20; margin-top: 0;'>Réinitialisation de mot de passe</h2>" +
                "<p style='color: #374151; line-height: 1.6;'>Bonjour <strong>" + user.getUsername() + "</strong>,</p>" +
                "<p style='color: #374151; line-height: 1.6;'>Vous avez demandé la réinitialisation de votre mot de passe. Cliquez sur le bouton ci-dessous pour en définir un nouveau :</p>" +
                "<div style='text-align: center; margin: 30px 0;'>" +
                "<a href='" + resetLink + "' style='display: inline-block; background: linear-gradient(135deg, #1B5E20 0%, #2E7D32 100%); color: white; padding: 14px 32px; text-decoration: none; border-radius: 8px; font-weight: 600;'>Réinitialiser mon mot de passe</a>" +
                "</div>" +
                "<p style='color: #6B7280; font-size: 13px; line-height: 1.6;'>Ce lien est valide pendant <strong>30 minutes</strong>. Si vous n'avez pas demandé cette réinitialisation, ignorez cet email.</p>" +
                "<p style='color: #6B7280; font-size: 13px;'>Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :</p>" +
                "<p style='color: #1B5E20; word-break: break-all; font-size: 12px; background: #F3F4F6; padding: 10px; border-radius: 6px;'>" + resetLink + "</p>" +
                "</div>" +
                "<div style='background: #F9FAFB; padding: 20px; text-align: center; border-top: 1px solid #E5E7EB;'>" +
                "<p style='color: #9CA3AF; font-size: 12px; margin: 0;'>© 2026 PNLP - Tous droits réservés</p>" +
                "</div>" +
                "</div></body></html>";
    }
}