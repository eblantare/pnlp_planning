package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.CreateUtilisateurRequest;
import tg.pnlp.planning.dto.UpdateUtilisateurRequest;
import tg.pnlp.planning.dto.UtilisateurDTO;
import tg.pnlp.planning.service.UtilisateurService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/utilisateurs")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Gestion des utilisateurs")
public class UtilisateurController {

    private final UtilisateurService utilisateurService;

    @GetMapping
    @Operation(summary = "Liste de tous les utilisateurs")
    public ResponseEntity<List<UtilisateurDTO>> getAllUtilisateurs() {
        return ResponseEntity.ok(utilisateurService.getAllUtilisateurs());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un utilisateur par ID")
    public ResponseEntity<UtilisateurDTO> getUtilisateurById(@PathVariable UUID id) {
        return ResponseEntity.ok(utilisateurService.getUtilisateurById(id));
    }

    @PostMapping
    @Operation(summary = "Créer un nouvel utilisateur")
    public ResponseEntity<UtilisateurDTO> createUtilisateur(
            @Valid @RequestBody CreateUtilisateurRequest request) {
        return new ResponseEntity<>(utilisateurService.createUtilisateur(request),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un utilisateur")
    public ResponseEntity<UtilisateurDTO> updateUtilisateur(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUtilisateurRequest request) {
        return ResponseEntity.ok(utilisateurService.updateUtilisateur(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Désactiver un utilisateur")
    public ResponseEntity<Void> deleteUtilisateur(@PathVariable UUID id) {
        utilisateurService.deleteUtilisateur(id);
        return ResponseEntity.noContent().build();
    }
    @PatchMapping("/{id}/statut")
    @Operation(summary = "Changer le statut d'un utilisateur")
    public ResponseEntity<UtilisateurDTO> changerStatut(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body) {
        Boolean actif = body.get("actif");
        return ResponseEntity.ok(utilisateurService.changerStatut(id, actif));
    }
}