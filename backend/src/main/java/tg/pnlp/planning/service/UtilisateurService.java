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

import java.util.*;
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
        if (request.getEmail() != null && utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Cet email est déjà utilisé");
        }

        // ✅ Vérifier que la liste de profils est valide
        Set<Profil> profils = chargerProfils(request.getProfilIds());

        // Vérifier que l'agent existe
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
                .profils(profils)                     // ✅ Liste de profils
                .agent(agent)
                .actif(false)
                .build();

        user = utilisateurRepository.save(user);
        log.info("Utilisateur créé: {} ({} profils, Agent: {} {})",
                user.getUsername(), profils.size(), agent.getPrenom(), agent.getNom());
        return convertToDTO(user);
    }

    @Transactional
    public UtilisateurDTO updateUtilisateur(UUID id, UpdateUtilisateurRequest request) {
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + id));

        // Mise à jour du mot de passe
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

        // ✅ Mise à jour de la liste de profils
        if (request.getProfilIds() != null && !request.getProfilIds().isEmpty()) {
            Set<Profil> profils = chargerProfils(request.getProfilIds());
            user.getProfils().clear();
            user.getProfils().addAll(profils);
        }

        // Mise à jour de l'agent
        if (request.getAgentId() != null) {
            Agent agent = agentRepository.findById(request.getAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé"));

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
        log.info("Utilisateur modifié: {} ({} profils)", user.getUsername(), user.getProfils().size());
        return convertToDTO(user);
    }

    /**
     * ✅ Charge la liste des profils à partir des IDs.
     * Vérifie que TOUS les profils existent.
     */
    private Set<Profil> chargerProfils(List<UUID> profilIds) {
        if (profilIds == null || profilIds.isEmpty()) {
            throw new BusinessException("Au moins un profil doit être sélectionné");
        }

        List<Profil> profils = profilRepository.findAllById(profilIds);
        if (profils.size() != profilIds.size()) {
            throw new BusinessException("Un ou plusieurs profils sont introuvables");
        }

        return new HashSet<>(profils);
    }

    @Transactional
    public void deleteUtilisateur(UUID id) {
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + id));

        utilisateurRepository.delete(user);
        log.info("Utilisateur supprimé définitivement: {}", user.getUsername());
    }

    @Transactional
    public UtilisateurDTO changerStatut(UUID id, Boolean actif) {
        Utilisateur user = utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé: " + id));
        user.setActif(actif);
        user = utilisateurRepository.save(user);
        return convertToDTO(user);
    }

    /**
     * ✅ Convertit l'entité en DTO avec la liste complète des profils.
     */
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
                .profilIds(profilIds)               // ✅ Nouveau
                .profilCodes(profilCodes)           // ✅ Nouveau
                .profilLibelles(profilLibelles)     // ✅ Nouveau
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