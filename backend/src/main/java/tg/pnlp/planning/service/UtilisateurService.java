package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.dto.CreateUtilisateurRequest;
import tg.pnlp.planning.dto.UpdateUtilisateurRequest;
import tg.pnlp.planning.dto.UtilisateurDTO;
import tg.pnlp.planning.entity.Agent;
import tg.pnlp.planning.entity.Profil;
import tg.pnlp.planning.entity.Utilisateur;
import tg.pnlp.planning.exception.BusinessException;
import tg.pnlp.planning.exception.ResourceNotFoundException;
import tg.pnlp.planning.repository.AgentRepository;
import tg.pnlp.planning.repository.ProfilRepository;
import tg.pnlp.planning.repository.UtilisateurRepository;
import tg.pnlp.planning.util.PasswordValidator;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final ProfilRepository profilRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UtilisateurDTO> getAllUtilisateurs() {
        return utilisateurRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public UtilisateurDTO getUtilisateurById(UUID id) {
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + id));
        return convertToDTO(user);
    }

    @Transactional
    public UtilisateurDTO createUtilisateur(CreateUtilisateurRequest request) {
        // ✅ Valider le mot de passe
        PasswordValidator.valider(request.getPassword());
        // Vérifier l'unicité du username
        if (utilisateurRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("Ce nom d'utilisateur est déjà utilisé");
        }

        // Vérifier l'unicité de l'email
        if (request.getEmail() != null &&
                utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Cet email est déjà utilisé");
        }

        // Vérifier que le profil existe
        Profil profil = profilRepository.findById(request.getProfilId())
                .orElseThrow(() -> new ResourceNotFoundException("Profil non trouvé"));

        // ✅ Vérifier que l'agent existe (maintenant obligatoire)
        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé avec l'id: " + request.getAgentId()));

        // Vérifier qu'aucun utilisateur n'est déjà lié à cet agent
        if (utilisateurRepository.existsByAgentId(request.getAgentId())) {
            throw new BusinessException("Un utilisateur est déjà associé à cet agent");
        }

        Utilisateur user = Utilisateur.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .profil(profil)
                .agent(agent)
                .actif(false)   // ✅ Inactif par défaut
                .build();

        user = utilisateurRepository.save(user);
        log.info("Utilisateur créé: {} (Agent: {} {})",
                user.getUsername(), agent.getPrenom(), agent.getNom());
        return convertToDTO(user);
    }

    @Transactional
    public UtilisateurDTO updateUtilisateur(UUID id, UpdateUtilisateurRequest request) {
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + id));

        // ✅ Valider le mot de passe s'il est modifié
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            PasswordValidator.valider(request.getPassword());
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        // Mise à jour de l'email
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (utilisateurRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                throw new BusinessException("Cet email est déjà utilisé");
            }
            user.setEmail(request.getEmail());
        }

        // Mise à jour du mot de passe
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Mise à jour du profil
        if (request.getProfilId() != null) {
            Profil profil = profilRepository.findById(request.getProfilId())
                    .orElseThrow(() -> new ResourceNotFoundException("Profil non trouvé"));
            user.setProfil(profil);
        }

        // Mise à jour de l'agent (maintenant obligatoire)
        if (request.getAgentId() != null) {
            Agent agent = agentRepository.findById(request.getAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé"));

            // Vérifier qu'aucun autre utilisateur n'est lié à cet agent
            if (!agent.getId().equals(user.getAgent() != null ? user.getAgent().getId() : null) &&
                    utilisateurRepository.existsByAgentId(request.getAgentId())) {
                throw new BusinessException("Un autre utilisateur est déjà associé à cet agent");
            }

            user.setAgent(agent);
        }

        // Mise à jour du statut actif
        if (request.getActif() != null) {
            user.setActif(request.getActif());
        }

        user = utilisateurRepository.save(user);
        log.info("Utilisateur modifié: {}", user.getUsername());
        return convertToDTO(user);
    }

    @Transactional
    public void deleteUtilisateur(UUID id) {
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + id));

        utilisateurRepository.delete(user);   // ✅ Suppression réelle
        log.info("Utilisateur supprimé définitivement: {}", user.getUsername());
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
    @Transactional
    public UtilisateurDTO changerStatut(UUID id, Boolean actif) {
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + id));
        user.setActif(actif);
        user = utilisateurRepository.save(user);
        return convertToDTO(user);
    }
}