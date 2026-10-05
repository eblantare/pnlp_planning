package tg.pnlp.planning.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tg.pnlp.planning.exception.BusinessException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * Sauvegarde un fichier dans un sous-dossier et retourne son chemin relatif.
     *
     * @param file        Le fichier uploadé
     * @param subDir      Sous-dossier (ex: "activites/{id}")
     * @param prefix      Préfixe du fichier (ex: "TDR", "ORDRE_MISSION", "LETTRE")
     * @return Le chemin relatif du fichier stocké
     */
    public String store(MultipartFile file, String subDir, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Le fichier est vide");
        }

        try {
            // Créer le dossier si nécessaire
            Path basePath = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
            Files.createDirectories(basePath);

            // Générer un nom de fichier unique
            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }
            String filename = prefix + "_" + UUID.randomUUID() + extension;

            // Copier le fichier
            Path targetPath = basePath.resolve(filename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Retourner le chemin relatif (pour stocker en base)
            String relativePath = subDir + "/" + filename;
            log.info("Fichier stocké: {}", relativePath);
            return relativePath;

        } catch (IOException e) {
            log.error("Erreur stockage fichier", e);
            throw new BusinessException("Impossible de stocker le fichier: " + e.getMessage());
        }
    }

    /**
     * Charge un fichier depuis son chemin relatif.
     */
    public byte[] load(String relativePath) {
        try {
            Path filePath = Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
            if (!Files.exists(filePath)) {
                throw new BusinessException("Fichier non trouvé: " + relativePath);
            }
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Erreur lecture fichier", e);
            throw new BusinessException("Impossible de lire le fichier: " + e.getMessage());
        }
    }

    /**
     * Supprime un fichier (silencieux si absent).
     */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path filePath = Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
            Files.deleteIfExists(filePath);
            log.info("Fichier supprimé: {}", relativePath);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier: {}", relativePath, e);
        }
    }

    /**
     * Récupère le chemin absolu d'un fichier.
     */
    public Path getAbsolutePath(String relativePath) {
        return Paths.get(uploadDir, relativePath).toAbsolutePath().normalize();
    }

    /**
     * Extrait l'extension d'un nom de fichier.
     */
    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * Valide le type de fichier (PDF, Word, image).
     */
    public void validerTypeFichier(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        if (!ext.matches("pdf|doc|docx|xls|xlsx|png|jpg|jpeg")) {
            throw new BusinessException("Type de fichier non autorisé: ." + ext +
                    " (autorisés: pdf, doc, docx, xls, xlsx, png, jpg, jpeg)");
        }
        // Taille max 20 Mo
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new BusinessException("Fichier trop volumineux (max 20 Mo)");
        }
    }
}