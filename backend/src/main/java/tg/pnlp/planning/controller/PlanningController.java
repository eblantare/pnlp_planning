package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.*;
import tg.pnlp.planning.service.PlanningService;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/planning")
@RequiredArgsConstructor
@Tag(name = "Planning", description = "API de gestion du planning")
public class PlanningController {

    private final PlanningService planningService;

    @PostMapping("/activites")
    @Operation(summary = "Créer une nouvelle activité")
    public ResponseEntity<ReponseCreationActiviteDTO> creerActivite(@RequestBody ActiviteDTO dto) {
        return ResponseEntity.ok(planningService.creerActivite(dto));
    }

    @PutMapping("/activites/{id}")
    @Operation(summary = "Modifier une activité existante")
    public ResponseEntity<ReponseCreationActiviteDTO> updateActivite(
            @PathVariable UUID id,
            @RequestBody ActiviteDTO dto) {
        return ResponseEntity.ok(planningService.updateActivite(id, dto));
    }

    @PatchMapping("/activites/{id}/statut")
    @Operation(summary = "Changer le statut d'une activité")
    public ResponseEntity<ActiviteDTO> changerStatut(
            @PathVariable UUID id,
            @RequestParam String statut) {
        return ResponseEntity.ok(planningService.changerStatut(id, statut));
    }

    @DeleteMapping("/activites/{id}")
    @Operation(summary = "Supprimer une activité")
    public ResponseEntity<Void> supprimerActivite(@PathVariable UUID id) {
        planningService.supprimerActivite(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activites/{id}")
    @Operation(summary = "Obtenir une activité par ID")
    public ResponseEntity<ActiviteDTO> getActivite(@PathVariable UUID id) {
        return ResponseEntity.ok(planningService.getActiviteById(id));
    }

    @GetMapping("/disponibilite")
    @Operation(summary = "Vérifier la disponibilité d'un agent")
    public ResponseEntity<DisponibiliteDTO> verifierDisponibilite(
            @RequestParam UUID agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(planningService.verifierDisponibilite(agentId, debut, fin));
    }

    @GetMapping("/jours-mission/mensuel")
    @Operation(summary = "Calculer les jours de mission mensuels")
    public ResponseEntity<Map<String, Integer>> joursMissionMensuel(
            @RequestParam int annee,
            @RequestParam int mois) {
        return ResponseEntity.ok(planningService.calculerJoursMissionMensuel(YearMonth.of(annee, mois)));
    }

    @GetMapping("/jours-mission/annuel")
    @Operation(summary = "Calculer les jours de mission annuels")
    public ResponseEntity<Map<String, Integer>> joursMissionAnnuel(@RequestParam int annee) {
        return ResponseEntity.ok(planningService.calculerJoursMissionAnnuel(annee));
    }

    @GetMapping("/mensuel")
    @Operation(summary = "Obtenir le planning mensuel")
    public ResponseEntity<PlanningMensuelDTO> planningMensuel(
            @RequestParam int annee,
            @RequestParam int mois) {
        return ResponseEntity.ok(planningService.getPlanningMensuel(YearMonth.of(annee, mois)));
    }
    @GetMapping("/remplacants")
    @Operation(summary = "Trouver des agents disponibles sur une période")
    public ResponseEntity<List<AgentDTO>> trouverRemplacants(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(planningService.trouverRemplacantsPossibles(debut, fin));
    }
}