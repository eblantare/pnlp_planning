package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.StatistiquesAgentDTO;
import tg.pnlp.planning.dto.StatistiquesGlobalesDTO;
import tg.pnlp.planning.service.StatistiquesService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/statistiques")
@RequiredArgsConstructor
@Tag(name = "Statistiques", description = "Statistiques du personnel et des missions")
public class StatistiquesController {

    private final StatistiquesService statistiquesService;

    @GetMapping("/mensuel")
    @Operation(summary = "Statistiques mensuelles du personnel")
    public ResponseEntity<StatistiquesGlobalesDTO> getStatistiquesMensuelles(
            @RequestParam int annee,
            @RequestParam int mois) {
        return ResponseEntity.ok(statistiquesService.getStatistiquesMensuelles(annee, mois));
    }

    // ✅ NOUVEAU : période personnalisée
    @GetMapping("/periode")
    @Operation(summary = "Statistiques sur une période personnalisée")
    public ResponseEntity<StatistiquesGlobalesDTO> getStatistiquesPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getStatistiquesPeriode(debut, fin));
    }

    // ✅ NOUVEAU : historique d'un agent
    @GetMapping("/agent/{agentId}/historique")
    @Operation(summary = "Historique des activités d'un agent sur une période")
    public ResponseEntity<StatistiquesAgentDTO> getHistoriqueAgent(
            @PathVariable UUID agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(statistiquesService.getHistoriqueAgent(agentId, debut, fin));
    }
}