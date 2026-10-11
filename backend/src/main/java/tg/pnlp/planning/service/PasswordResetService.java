package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tg.pnlp.planning.dto.ForgotPasswordRequest;
import tg.pnlp.planning.dto.ResetPasswordRequest;
import tg.pnlp.planning.entity.PasswordResetToken;
import tg.pnlp.planning.entity.Utilisateur;
import tg.pnlp.planning.exception.BusinessException;
import tg.pnlp.planning.repository.PasswordResetTokenRepository;
import tg.pnlp.planning.repository.UtilisateurRepository;
import tg.pnlp.planning.util.PasswordValidator;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.password-reset.token-expiration-minutes:30}")
    private int expirationMinutes;

    /**
     * Étape 1 : Demande de réinitialisation (envoi de l'email).
     *
     * ✅ Le token est enregistré en DB dans la transaction,
     *    puis l'email est envoyé APRÈS le commit de la transaction.
     *    → Si l'envoi échoue, le token reste valide en DB.
     *    → L'utilisateur reçoit quand même 200 OK (pas d'énumération d'emails).
     */
    @Transactional
    public void demanderReinitialisation(ForgotPasswordRequest request) {

        // 1. Chercher l'utilisateur par email
        Utilisateur user = utilisateurRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(
                        "Aucun compte n'est associé à cet email"));

        // 2. Vérifier que le compte est actif
        if (!Boolean.TRUE.equals(user.getActif())) {
            throw new BusinessException(
                    "Votre compte est inactif. Contactez l'administrateur.");
        }

        // 3. Vérifier que l'utilisateur a bien un email
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(
                    "Aucun email n'est associé à ce compte.");
        }

        // 4. Supprimer les anciens tokens de cet utilisateur
        tokenRepository.deleteByUtilisateur(user);

        // 5. Générer un nouveau token
        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .utilisateur(user)
                .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        // 6. ✅ Envoyer l'email APRÈS le commit de la transaction
        final Utilisateur userRef = user;
        final String tokenRef = token;
        final String emailRef = user.getEmail();

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            emailService.envoyerEmailReinitialisation(userRef, tokenRef);
                        } catch (Exception e) {
                            log.error("❌ Échec envoi email de réinitialisation à {} : {}",
                                    emailRef, e.getMessage(), e);
                            // ⚠️ Pas de rollback ici (transaction déjà commitée).
                            // Le token reste en DB : l'utilisateur peut re-demander.
                        }
                    }
                }
        );

        log.info("Demande de réinitialisation enregistrée pour: {}", emailRef);
    }

    /**
     * Étape 2 : Vérifier qu'un token est valide (utilisé par le frontend).
     */
    public boolean verifierToken(String token) {
        return tokenRepository.findValidToken(token, LocalDateTime.now()).isPresent();
    }

    /**
     * Étape 3 : Réinitialiser le mot de passe avec le token.
     */
    @Transactional
    public void reinitialiserMotDePasse(ResetPasswordRequest request) {

        // 1. Valider le nouveau mot de passe
        PasswordValidator.valider(request.getNouveauMotDePasse());

        // 2. Chercher le token valide
        PasswordResetToken resetToken = tokenRepository
                .findValidToken(request.getToken(), LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(
                        "Lien de réinitialisation invalide ou expiré"));

        // 3. Récupérer l'utilisateur
        Utilisateur user = resetToken.getUtilisateur();

        // 4. Mettre à jour le mot de passe
        user.setPassword(passwordEncoder.encode(request.getNouveauMotDePasse()));
        utilisateurRepository.save(user);

        // 5. Marquer le token comme utilisé
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        // 6. Nettoyer les tokens expirés
        tokenRepository.deleteExpired(LocalDateTime.now());

        log.info("Mot de passe réinitialisé pour: {}", user.getUsername());
    }
}