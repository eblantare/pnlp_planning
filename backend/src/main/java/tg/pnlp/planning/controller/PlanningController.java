package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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

    // ============================================================
    // ✅ LECTURE (GET) : accessible à TOUS les utilisateurs authentifiés
    //    Nécessaire pour que CONSULTANT / OBSERVATEUR / AGENT puissent
    //    consulter le planning sans pouvoir le modifier.
    // ============================================================

    @GetMapping("/mensuel")
    @Operation(summary = "Planning mensuel (lecture seule pour CONSULTANT/OBSERVATEUR/AGENT)")
    public ResponseEntity<PlanningMensuelDTO> planningMensuel(
            @RequestParam int annee, @RequestParam int mois) {
        return ResponseEntity.ok(planningService.getPlanningMensuel(YearMonth.of(annee, mois)));
    }

    @GetMapping("/activites/{id}")
    public ResponseEntity<ActiviteDTO> getActivite(@PathVariable UUID id) {
        return ResponseEntity.ok(planningService.getActiviteById(id));
    }

    @GetMapping("/activites/{id}/fichiers/{type}")
    @Operation(summary = "Télécharger un fichier (tdr | ordre_mission | lettre)")
    public ResponseEntity<byte[]> telechargerFichier(
            @PathVariable UUID id, @PathVariable String type) {
        byte[] contenu = planningService.telechargerFichier(id, type);
        String nom = planningService.getNomFichier(id, type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(contenu);
    }

    @GetMapping("/jours-mission/mensuel")
    public ResponseEntity<Map<String, Integer>> joursMissionMensuel(
            @RequestParam int annee, @RequestParam int mois) {
        return ResponseEntity.ok(planningService.calculerJoursMissionMensuel(YearMonth.of(annee, mois)));
    }

    // ============================================================
    // ✅ ÉCRITURE (POST/PUT/PATCH/DELETE) : SUPER_ADMIN, ADMIN, PLANIFICATEUR
    //    CONSULTANT / OBSERVATEUR / AGENT en sont exclus.
    // ============================================================

    @PostMapping("/activites")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<ReponseCreationActiviteDTO> creerActivite(@RequestBody ActiviteDTO dto) {
        return ResponseEntity.ok(planningService.creerActivite(dto));
    }

    @PutMapping("/activites/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<ReponseCreationActiviteDTO> updateActivite(
            @PathVariable UUID id, @RequestBody ActiviteDTO dto) {
        return ResponseEntity.ok(planningService.updateActivite(id, dto));
    }

    @PatchMapping("/activites/{id}/statut")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<ActiviteDTO> changerStatut(
            @PathVariable UUID id, @RequestParam String statut) {
        return ResponseEntity.ok(planningService.changerStatut(id, statut));
    }

    @DeleteMapping("/activites/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<Void> supprimerActivite(@PathVariable UUID id) {
        planningService.supprimerActivite(id);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // UPLOAD / SUPPRESSION DE FICHIERS (écriture)
    // ============================================================

    @PostMapping(value = "/activites/{id}/tdr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    @Operation(summary = "Uploader le TDR")
    public ResponseEntity<ActiviteDTO> uploadTdr(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "conforme", defaultValue = "false") boolean conforme) {
        return ResponseEntity.ok(planningService.uploadTdr(id, file, conforme));
    }

    @PostMapping(value = "/activites/{id}/ordre-mission", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    @Operation(summary = "Uploader l'Ordre de Mission")
    public ResponseEntity<ActiviteDTO> uploadOrdreMission(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "conforme", defaultValue = "false") boolean conforme) {
        return ResponseEntity.ok(planningService.uploadOrdreMission(id, file, conforme));
    }

    @PostMapping(value = "/activites/{id}/lettre-invitation", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    @Operation(summary = "Uploader la Lettre d'invitation")
    public ResponseEntity<ActiviteDTO> uploadLettreInvitation(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "conforme", defaultValue = "false") boolean conforme) {
        return ResponseEntity.ok(planningService.uploadLettreInvitation(id, file, conforme));
    }

    @DeleteMapping("/activites/{id}/tdr")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<Void> supprimerTdr(@PathVariable UUID id) {
        planningService.supprimerTdr(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/activites/{id}/ordre-mission")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<Void> supprimerOrdreMission(@PathVariable UUID id) {
        planningService.supprimerOrdreMission(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/activites/{id}/lettre-invitation")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<Void> supprimerLettreInvitation(@PathVariable UUID id) {
        planningService.supprimerLettreInvitation(id);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // RENVOI D'EMAILS (action métier sensible → écriture)
    // ============================================================

    @PostMapping("/activites/{id}/renvoyer-tdr")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<Void> renvoyerTdr(@PathVariable UUID id) {
        planningService.renvoyerTdr(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/activites/{id}/renvoyer-ordre-mission")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<Void> renvoyerOrdreMission(@PathVariable UUID id) {
        planningService.renvoyerOrdreMission(id);
        return ResponseEntity.ok().build();
    }

    // ============================================================
    // AUTRES ENDPOINTS
    // ============================================================

    @GetMapping("/disponibilite")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<DisponibiliteDTO> verifierDisponibilite(
            @RequestParam UUID agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(planningService.verifierDisponibilite(agentId, debut, fin));
    }

    @GetMapping("/jours-mission/annuel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<Map<String, Integer>> joursMissionAnnuel(@RequestParam int annee) {
        return ResponseEntity.ok(planningService.calculerJoursMissionAnnuel(annee));
    }

    @GetMapping("/remplacants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'PLANIFICATEUR')")
    public ResponseEntity<List<AgentDTO>> trouverRemplacants(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return ResponseEntity.ok(planningService.trouverRemplacantsPossibles(debut, fin));
    }
}