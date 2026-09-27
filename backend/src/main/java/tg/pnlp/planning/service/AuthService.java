package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.dto.LoginRequest;
import tg.pnlp.planning.dto.LoginResponse;
import tg.pnlp.planning.dto.UtilisateurDTO;
import tg.pnlp.planning.entity.Utilisateur;
import tg.pnlp.planning.exception.BusinessException;
import tg.pnlp.planning.repository.UtilisateurRepository;
import tg.pnlp.planning.security.JwtService;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 1. Chercher l'utilisateur
        Utilisateur user = utilisateurRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("Nom d'utilisateur ou mot de passe incorrect"));

        // 2. Vérifier le mot de passe
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Nom d'utilisateur ou mot de passe incorrect");
        }

        // 3. Vérifier que le compte est actif
        if (!user.getActif()) {
            throw new BusinessException("Votre compte est inactif. Contactez l'administrateur.");
        }

        // 4. Mettre à jour la dernière connexion
        user.setDerniereConnexion(LocalDateTime.now());
        utilisateurRepository.save(user);

        // 5. Générer le token
        String token = jwtService.genererToken(user);

        log.info("Connexion réussie pour: {}", user.getUsername());

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationMs())
                .utilisateur(convertToDTO(user))
                .build();
    }

    public UtilisateurDTO getCurrentUser(UUID userId) {
        Utilisateur user = utilisateurRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilisateur non trouvé"));
        return convertToDTO(user);
    }

    private UtilisateurDTO convertToDTO(Utilisateur user) {
        UtilisateurDTO.UtilisateurDTOBuilder builder = UtilisateurDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .actif(user.getActif())
                .derniereConnexion(user.getDerniereConnexion())
                .createdAt(user.getCreatedAt());

        if (user.getProfil() != null) {
            builder.profilId(user.getProfil().getId())
                    .profilCode(user.getProfil().getCode())
                    .profilLibelle(user.getProfil().getLibelle());
        }

        if (user.getAgent() != null) {
            builder.agentId(user.getAgent().getId())
                    .agentNom(user.getAgent().getNomComplet());
        }

        return builder.build();
    }
}