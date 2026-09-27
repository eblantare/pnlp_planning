package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.AgentDTO;
import tg.pnlp.planning.service.AgentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
@Tag(name = "Agents", description = "API de gestion des agents")
public class AgentController {

    private final AgentService agentService;
 
    @GetMapping
    @Operation(summary = "Liste de tous les agents")
    public ResponseEntity<List<AgentDTO>> getAllAgents() {
        return ResponseEntity.ok(agentService.getAllAgents());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un agent par ID")
    public ResponseEntity<AgentDTO> getAgentById(@PathVariable UUID id) {
        return ResponseEntity.ok(agentService.getAgentById(id));
    }

    @PostMapping
    @Operation(summary = "Créer un nouvel agent")
    public ResponseEntity<AgentDTO> createAgent(@RequestBody AgentDTO dto) {
        return new ResponseEntity<>(agentService.createAgent(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un agent")
    public ResponseEntity<AgentDTO> updateAgent(@PathVariable UUID id, @RequestBody AgentDTO dto) {
        return ResponseEntity.ok(agentService.updateAgent(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Désactiver un agent")
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID id) {
        agentService.deleteAgent(id);
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/statut")
    @Operation(summary = "Changer le statut d'un agent")
    public ResponseEntity<AgentDTO> changerStatut(
            @PathVariable UUID id,
            @RequestParam Boolean actif) {
        return ResponseEntity.ok(agentService.changerStatut(id, actif));
    }
}