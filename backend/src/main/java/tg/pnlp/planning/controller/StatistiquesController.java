package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.StatistiquesGlobalesDTO;
import tg.pnlp.planning.service.StatistiquesService;

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
}
