package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.dto.LoginRequest;
import tg.pnlp.planning.dto.LoginResponse;
import tg.pnlp.planning.dto.UtilisateurDTO;
import tg.pnlp.planning.entity.Profil;
import tg.pnlp.planning.entity.Utilisateur;
import tg.pnlp.planning.exception.BusinessException;
import tg.pnlp.planning.repository.UtilisateurRepository;
import tg.pnlp.planning.security.JwtService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        // ✅ Charger l'utilisateur AVEC ses profils (lazy → fetch)
        Utilisateur user = utilisateurRepository.findByUsernameWithProfils(request.getUsername())
                .orElseThrow(() -> new BusinessException("Nom d'utilisateur ou mot de passe incorrect"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Nom d'utilisateur ou mot de passe incorrect");
        }

        if (!user.getActif()) {
            throw new BusinessException("Votre compte est inactif. Contactez l'administrateur.");
        }

        user.setDerniereConnexion(LocalDateTime.now());
        utilisateurRepository.save(user);

        String token = jwtService.genererToken(user);

        log.info("Connexion réussie pour: {} (profils: {})",
                user.getUsername(), user.getProfilCodes());

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
        List<UUID> profilIds = new ArrayList<>();
        List<String> profilCodes = new ArrayList<>();
        List<String> profilLibelles = new ArrayList<>();

        if (user.getProfils() != null) {
            user.getProfils().stream()
                    .sorted(Comparator.comparing(Profil::getCode))
                    .forEach(p -> {
                        profilIds.add(p.getId());
                        profilCodes.add(p.getCode());
                        profilLibelles.add(p.getLibelle());
                    });
        }

        UtilisateurDTO.UtilisateurDTOBuilder builder = UtilisateurDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .profilIds(profilIds)
                .profilCodes(profilCodes)
                .profilLibelles(profilLibelles)
                .actif(user.getActif())
                .derniereConnexion(user.getDerniereConnexion())
                .createdAt(user.getCreatedAt());

        if (user.getAgent() != null) {
            builder.agentId(user.getAgent().getId())
                    .agentNom(user.getAgent().getNomComplet());
        }

        return builder.build();
    }
}