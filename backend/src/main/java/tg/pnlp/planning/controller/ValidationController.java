package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.ActiviteDTO;
import tg.pnlp.planning.dto.DecisionValidationDTO;
import tg.pnlp.planning.dto.ValidationConflitDTO;
import tg.pnlp.planning.service.ValidationService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/planning/validation")
@RequiredArgsConstructor
@Tag(name = "Validation", description = "Workflow de validation des conflits")
public class ValidationController {

    private final ValidationService validationService;

    private Set<String> extraireProfils(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", "").trim().toUpperCase())
                .collect(Collectors.toSet());
    }

    // ============================================================
    // FILE D'ATTENTE
    // ============================================================

    @GetMapping("/a-valider")
    @PreAuthorize("hasAnyRole('VALIDATEUR_NIVEAU_1','VALIDATEUR_NIVEAU_2','VALIDATEUR_NIVEAU_3','SUPER_ADMIN')")
    @Operation(summary = "Liste des activités à valider pour mon niveau")
    public ResponseEntity<List<ActiviteDTO>> lister(Authentication auth) {
        Set<String> profils = extraireProfils(auth);
        Integer niveau = validationService.getNiveauValidation(profils);
        // ✅ CORRECTIF : on ne filtre plus -1 ici, ValidationService sait déjà
        // gérer ce cas (liste complète, tous niveaux confondus, pour SUPER_ADMIN)
        if (niveau == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(validationService.listerActivitesAValider(niveau));
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('VALIDATEUR_NIVEAU_1','VALIDATEUR_NIVEAU_2','VALIDATEUR_NIVEAU_3','SUPER_ADMIN')")
    @Operation(summary = "Compteur pour le badge")
    public ResponseEntity<Map<String, Object>> count(Authentication auth) {
        Set<String> profils = extraireProfils(auth);
        Integer niveau = validationService.getNiveauValidation(profils);
        long count = (niveau == null) ? 0 : validationService.compterActivitesAValider(niveau);
        return ResponseEntity.ok(Map.of("niveau", niveau == null ? 0 : niveau, "count", count));
    }

    // ============================================================
    // TRAITEMENT DES CONFLITS
    // ============================================================

    @PostMapping("/{id}/conflits")
    @PreAuthorize("hasAnyRole('VALIDATEUR_NIVEAU_1','VALIDATEUR_NIVEAU_2','VALIDATEUR_NIVEAU_3','SUPER_ADMIN')")
    @Operation(summary = "Traiter les conflits (retirer / remplacer / forcer)")
    public ResponseEntity<ActiviteDTO> traiterConflits(
            @PathVariable UUID id,
            @RequestBody List<ValidationConflitDTO> actions,
            Authentication auth) {
        Set<String> profils = extraireProfils(auth);
        return ResponseEntity.ok(validationService.traiterConflits(id, actions, profils));
    }

    // ============================================================
    // DÉCISION DE VALIDATION
    // ============================================================

    @PostMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('VALIDATEUR_NIVEAU_1','VALIDATEUR_NIVEAU_2','VALIDATEUR_NIVEAU_3','SUPER_ADMIN')")
    @Operation(summary = "Décision : VALIDER, RENVOYER_NIVEAU_SUPERIEUR, RENVOYER_PLANIFICATEUR")
    public ResponseEntity<ActiviteDTO> valider(
            @PathVariable UUID id,
            @RequestBody DecisionValidationDTO decision,
            Authentication auth) {
        Set<String> profils = extraireProfils(auth);
        return ResponseEntity.ok(validationService.valider(id, decision, profils));
    }
}