package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.dto.AgentDTO;
import tg.pnlp.planning.entity.Agent;
import tg.pnlp.planning.exception.ResourceNotFoundException;
import tg.pnlp.planning.repository.AgentRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final AgentRepository agentRepository;

    public List<AgentDTO> getAllAgents() {
        return agentRepository.findByActifTrue().stream()
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
        Agent agent = Agent.builder()
                .nom(dto.getNom())
                .prenom(dto.getPrenom())
                .email(dto.getEmail())
                .telephone(dto.getTelephone())
                .poste(dto.getPoste())
                .build();

        agent = agentRepository.save(agent);
        log.info("Agent créé: {}", agent.getNomComplet());
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
                .actif(agent.getActif())
                .build();
    }
}