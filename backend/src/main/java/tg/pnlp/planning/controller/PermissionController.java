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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ✅ NOUVEAU : Endpoint qui expose les permissions du user connecté.
 * Le frontend peut appeler /api/permissions/moi pour récupérer les permissions
 * à afficher ou masquer.
 */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permissions de l'utilisateur connecté")
public class PermissionController {

    private final PermissionService permissionService;

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
            return ResponseEntity.ok(result);
        }

        // Récupérer tous les profils du user
        Set<String> profils = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", "").trim().toUpperCase())
                .collect(java.util.stream.Collectors.toSet());

        // Union des menus accessibles (si l'utilisateur a plusieurs profils)
        Set<String> menus = new java.util.HashSet<>();
        for (String profil : profils) {
            for (String menu : Set.of(
                    PermissionService.RESSOURCE_DASHBOARD,
                    PermissionService.RESSOURCE_PLANNING,
                    PermissionService.RESSOURCE_STATISTIQUES,
                    PermissionService.RESSOURCE_AGENTS,
                    PermissionService.RESSOURCE_PROFILS,
                    PermissionService.RESSOURCE_UTILISATEURS)) {
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

        result.put("menus", menus);
        result.put("crudPlanning", crudPlanning);
        result.put("crudAgents", crudAgents);
        result.put("crudProfils", crudProfils);
        result.put("crudUtilisateurs", crudUtilisateurs);
        result.put("profils", profils);

        return ResponseEntity.ok(result);
    }
}