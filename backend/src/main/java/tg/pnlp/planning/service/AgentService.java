package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.dto.AgentDTO;
import tg.pnlp.planning.entity.Agent;
import tg.pnlp.planning.exception.ResourceNotFoundException;
import tg.pnlp.planning.repository.AgentRepository;
import tg.pnlp.planning.exception.BusinessException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final AgentRepository agentRepository;

    public List<AgentDTO> getAllAgents() {
        // ✅ Retourne TOUS les agents (actifs ET inactifs)
        return agentRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public AgentDTO getAgentById(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + id));
        return convertToDTO(agent);
    }

    @Transactional
    public AgentDTO createAgent(AgentDTO dto) {
        // ✅ Vérifier si l'email existe déjà
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty()) {
            if (agentRepository.existsByEmail(dto.getEmail())) {
                throw new BusinessException("Un agent avec cet email existe déjà : " + dto.getEmail());
            }
        }

        Agent agent = Agent.builder()
                .nom(dto.getNom())
                .prenom(dto.getPrenom())
                .email(dto.getEmail())
                .telephone(dto.getTelephone())
                .poste(dto.getPoste())
                .unite(dto.getUnite())
                .actif(false)
                .build();

        agent = agentRepository.save(agent);
        log.info("Agent créé (inactif): {} (Unité: {})", agent.getNomComplet(), agent.getUnite());
        return convertToDTO(agent);
    }

    @Transactional
    public AgentDTO updateAgent(UUID id, AgentDTO dto) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + id));

        agent.setNom(dto.getNom());
        agent.setPrenom(dto.getPrenom());
        agent.setEmail(dto.getEmail());
        agent.setTelephone(dto.getTelephone());
        agent.setPoste(dto.getPoste());
        agent.setUnite(dto.getUnite());       // ✅ NOUVEAU

        agent = agentRepository.save(agent);
        return convertToDTO(agent);
    }

    @Transactional
    public void deleteAgent(UUID id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + id));
        agent.setActif(false);
        agentRepository.save(agent);
        log.info("Agent désactivé: {}", agent.getNomComplet());
    }

    private AgentDTO convertToDTO(Agent agent) {
        return AgentDTO.builder()
                .id(agent.getId())
                .nom(agent.getNom())
                .prenom(agent.getPrenom())
                .email(agent.getEmail())
                .telephone(agent.getTelephone())
                .poste(agent.getPoste())
                .unite(agent.getUnite())        // ✅ NOUVEAU
                .actif(agent.getActif())
                .build();
    }
    @Transactional
    public AgentDTO changerStatut(UUID id, Boolean actif) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent non trouvé: " + id));

        agent.setActif(actif);
        agent = agentRepository.save(agent);

        log.info("Statut de l'agent {} changé en: {}",
                agent.getNomComplet(), actif ? "ACTIF" : "INACTIF");
        return convertToDTO(agent);
    }
}