package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.ProfilDTO;
import tg.pnlp.planning.service.ProfilService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/profils")
@RequiredArgsConstructor
@Tag(name = "Profils", description = "Gestion des profils (rôles)")
public class ProfilController {

    private final ProfilService profilService;

    @GetMapping
    @Operation(summary = "Liste de tous les profils")
    public ResponseEntity<List<ProfilDTO>> getAllProfils() {
        return ResponseEntity.ok(profilService.getAllProfils());
    }

    @GetMapping("/actifs")
    @Operation(summary = "Liste des profils actifs")
    public ResponseEntity<List<ProfilDTO>> getProfilsActifs() {
        return ResponseEntity.ok(profilService.getProfilsActifs());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtenir un profil par ID")
    public ResponseEntity<ProfilDTO> getProfilById(@PathVariable UUID id) {
        return ResponseEntity.ok(profilService.getProfilById(id));
    }

    @PostMapping
    @Operation(summary = "Créer un nouveau profil")
    public ResponseEntity<ProfilDTO> createProfil(@RequestBody ProfilDTO dto) {
        return new ResponseEntity<>(profilService.createProfil(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un profil")
    public ResponseEntity<ProfilDTO> updateProfil(@PathVariable UUID id,
                                                  @RequestBody ProfilDTO dto) {
        return ResponseEntity.ok(profilService.updateProfil(id, dto));
    }

    /**
     * ✅ NOUVEAU : Basculer l'état actif/inactif
     */
    @PatchMapping("/{id}/toggle-actif")
    @Operation(summary = "Basculer l'état actif/inactif d'un profil")
    public ResponseEntity<ProfilDTO> toggleActif(@PathVariable UUID id) {
        return ResponseEntity.ok(profilService.toggleActif(id));
    }

    /**
     * ✅ MODIFIÉ : Suppression réelle
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer définitivement un profil")
    public ResponseEntity<Void> deleteProfil(@PathVariable UUID id) {
        profilService.deleteProfil(id);
        return ResponseEntity.noContent().build();
    }
}