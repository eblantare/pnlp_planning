package tg.pnlp.planning.service;

import java.util.List;

public interface EmailSender {

    /**
     * Envoi avec UNE SEULE pièce jointe (signature historique).
     * Conservée pour compatibilité avec le code existant.
     */
    void sendEmail(String to, String subject, String textBody, String htmlBody,
                   String attachmentName, byte[] attachmentContent);

    /**
     * ✅ NOUVELLE signature : envoi avec PLUSIEURS pièces jointes.
     */
    void sendEmail(String to, String subject, String textBody, String htmlBody,
                   List<Attachment> attachments);

    void sendSimpleEmail(String to, String subject, String textBody);

    /**
     * Représente une pièce jointe (nom de fichier + contenu binaire).
     */
    record Attachment(String filename, byte[] content) {}
}