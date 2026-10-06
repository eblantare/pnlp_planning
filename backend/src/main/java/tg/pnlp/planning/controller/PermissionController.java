package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.pnlp.planning.service.PermissionService;
import tg.pnlp.planning.service.ValidationService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ✅ Endpoint qui expose les permissions du user connecté.
 * Le frontend peut appeler /api/permissions/moi pour récupérer les permissions
 * à afficher ou masquer.
 */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permissions de l'utilisateur connecté")
public class PermissionController {

    private final PermissionService permissionService;
    private final ValidationService validationService;

    @GetMapping("/moi")
    @Operation(summary = "Permissions de l'utilisateur connecté")
    public ResponseEntity<Map<String, Object>> getMesPermissions(Authentication authentication) {
        Map<String, Object> result = new HashMap<>();

        if (authentication == null || authentication.getAuthorities() == null) {
            result.put("menus", Set.of());
            result.put("crudPlanning", false);
            result.put("crudAgents", false);
            result.put("crudProfils", false);
            result.put("crudUtilisateurs", false);
            result.put("peutValiderN1", false);
            result.put("peutValiderN2", false);
            result.put("peutValiderN3", false);
            result.put("niveauValidation", null);
            result.put("activitesAValider", 0);
            result.put("profils", Set.of());
            return ResponseEntity.ok(result);
        }

        // Récupérer tous les profils du user
        Set<String> profils = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", "").trim().toUpperCase())
                .collect(java.util.stream.Collectors.toSet());

        // Union des menus accessibles (si l'utilisateur a plusieurs profils)
        Set<String> menus = new HashSet<>();
        for (String profil : profils) {
            for (String menu : Set.of(
                    PermissionService.RESSOURCE_DASHBOARD,
                    PermissionService.RESSOURCE_PLANNING,
                    PermissionService.RESSOURCE_STATISTIQUES,
                    PermissionService.RESSOURCE_AGENTS,
                    PermissionService.RESSOURCE_PROFILS,
                    PermissionService.RESSOURCE_UTILISATEURS,
                    PermissionService.RESSOURCE_VALIDATION)) {
                if (permissionService.peutAccederAuMenu(profil, menu)) {
                    menus.add(menu);
                }
            }
        }

        // Flags CRUD (si AU MOINS UN profil le permet)
        boolean crudPlanning = profils.stream().anyMatch(permissionService::peutCrudPlanning);
        boolean crudAgents = profils.stream().anyMatch(permissionService::peutCrudAgents);
        boolean crudProfils = profils.stream().anyMatch(permissionService::peutCrudProfils);
        boolean crudUtilisateurs = profils.stream().anyMatch(permissionService::peutCrudUtilisateurs);

        // ✅ Dérivé d'une seule source (ValidationService, qui normalise déjà via ProfilCodeNormalizer)
        Integer niveauValidation = validationService.getNiveauValidation(profils);

        boolean peutValiderN1 = niveauValidation != null && (niveauValidation == 1 || niveauValidation == -1);
        boolean peutValiderN2 = niveauValidation != null && (niveauValidation == 2 || niveauValidation == -1);
        boolean peutValiderN3 = niveauValidation != null && (niveauValidation == 3 || niveauValidation == -1);

        long activitesAValider = 0;
        if (niveauValidation != null) {
            activitesAValider = validationService.compterActivitesAValider(niveauValidation);
        }

        result.put("menus", menus);
        result.put("crudPlanning", crudPlanning);
        result.put("crudAgents", crudAgents);
        result.put("crudProfils", crudProfils);
        result.put("crudUtilisateurs", crudUtilisateurs);
        result.put("peutValiderN1", peutValiderN1);
        result.put("peutValiderN2", peutValiderN2);
        result.put("peutValiderN3", peutValiderN3);
        result.put("niveauValidation", niveauValidation);
        result.put("activitesAValider", activitesAValider);
        result.put("profils", profils);

        return ResponseEntity.ok(result);
    }
}