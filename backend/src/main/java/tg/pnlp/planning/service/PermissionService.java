package tg.pnlp.planning.service;

import org.springframework.stereotype.Service;
import tg.pnlp.planning.security.ProfilCodeNormalizer;

import java.util.Map;
import java.util.Set;

/**
 * ✅ Service centralisé des permissions.
 * La normalisation des codes de profil est déléguée à
 * {@link ProfilCodeNormalizer} (source unique de vérité).
 */
@Service
public class PermissionService {

    // ============================================================
    // RÔLES
    // ============================================================
    public static final String SUPER_ADMIN         = ProfilCodeNormalizer.SUPER_ADMIN;
    public static final String ADMIN               = ProfilCodeNormalizer.ADMIN;
    public static final String CHEF_SERVICE        = ProfilCodeNormalizer.CHEF_SERVICE;
    public static final String PLANIFICATEUR       = ProfilCodeNormalizer.PLANIFICATEUR;
    public static final String CONSULTANT          = ProfilCodeNormalizer.CONSULTANT;
    public static final String OBSERVATEUR         = ProfilCodeNormalizer.OBSERVATEUR;
    public static final String AGENTS              = ProfilCodeNormalizer.AGENT;
    public static final String AGENT               = ProfilCodeNormalizer.AGENT;
    public static final String VALIDATEUR_NIVEAU_1 = ProfilCodeNormalizer.VALIDATEUR_NIVEAU_1;
    public static final String VALIDATEUR_NIVEAU_2 = ProfilCodeNormalizer.VALIDATEUR_NIVEAU_2;
    public static final String VALIDATEUR_NIVEAU_3 = ProfilCodeNormalizer.VALIDATEUR_NIVEAU_3;

    // ============================================================
    // RESSOURCES (menus)
    // ============================================================
    public static final String RESSOURCE_DASHBOARD    = "DASHBOARD";
    public static final String RESSOURCE_PLANNING     = "PLANNING";
    public static final String RESSOURCE_STATISTIQUES = "STATISTIQUES";
    public static final String RESSOURCE_AGENTS       = "AGENTS";
    public static final String RESSOURCE_PROFILS      = "PROFILS";
    public static final String RESSOURCE_UTILISATEURS = "UTILISATEURS";
    public static final String RESSOURCE_VALIDATION   = "VALIDATION";   // ✅ NOUVEAU

    // ============================================================
    // MENUS PAR PROFIL
    // ============================================================
    private static final Map<String, Set<String>> MENUS_PAR_PROFIL = Map.ofEntries(
            Map.entry(SUPER_ADMIN, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING, RESSOURCE_STATISTIQUES,
                    RESSOURCE_AGENTS, RESSOURCE_PROFILS, RESSOURCE_UTILISATEURS,
                    RESSOURCE_VALIDATION
            )),
            Map.entry(ADMIN, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING, RESSOURCE_STATISTIQUES,
                    RESSOURCE_AGENTS, RESSOURCE_VALIDATION
            )),
            Map.entry(CHEF_SERVICE, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING, RESSOURCE_STATISTIQUES,
                    RESSOURCE_AGENTS, RESSOURCE_VALIDATION
            )),
            Map.entry(PLANIFICATEUR, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING, RESSOURCE_STATISTIQUES
            )),
            // ✅ NOUVEAU : les validateurs accèdent à VALIDATION
            Map.entry(VALIDATEUR_NIVEAU_1, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING, RESSOURCE_STATISTIQUES,
                    RESSOURCE_VALIDATION
            )),
            Map.entry(VALIDATEUR_NIVEAU_2, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING, RESSOURCE_STATISTIQUES,
                    RESSOURCE_VALIDATION
            )),
            Map.entry(VALIDATEUR_NIVEAU_3, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING, RESSOURCE_STATISTIQUES,
                    RESSOURCE_VALIDATION
            )),
            Map.entry(CONSULTANT, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING
            )),
            Map.entry(OBSERVATEUR, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING
            )),
            Map.entry(AGENT, Set.of(
                    RESSOURCE_DASHBOARD, RESSOURCE_PLANNING
            ))
    );

    // ============================================================
    // PROFILS AVEC CRUD PLANNING
    // ============================================================
    private static final Set<String> PROFILS_AVEC_CRUD_PLANNING = Set.of(
            SUPER_ADMIN, ADMIN, PLANIFICATEUR
    );

    // ============================================================
    // MÉTHODES PUBLIQUES
    // ============================================================

    public boolean peutAccederAuMenu(String profilCode, String ressource) {
        if (profilCode == null || ressource == null) return false;
        String code = ProfilCodeNormalizer.normaliser(profilCode);
        return MENUS_PAR_PROFIL
                .getOrDefault(code, Set.of())
                .contains(ressource);
    }

    public boolean peutCrudPlanning(String profilCode) {
        return contient(PROFILS_AVEC_CRUD_PLANNING, profilCode);
    }

    public boolean peutValiderPlanning(String profilCode) {
        return contient(PROFILS_AVEC_CRUD_PLANNING, profilCode);
    }

    public boolean peutCrudAgents(String profilCode) {
        return contient(Set.of(SUPER_ADMIN, ADMIN), profilCode);
    }

    public boolean peutCrudProfils(String profilCode) {
        return contient(Set.of(SUPER_ADMIN), profilCode);
    }

    public boolean peutCrudUtilisateurs(String profilCode) {
        return contient(Set.of(SUPER_ADMIN), profilCode);
    }

    /**
     * ✅ NOUVEAU : retourne le niveau de validateur (1, 2, 3) ou null.
     * Si l'utilisateur a plusieurs niveaux, retourne le plus élevé.
     */
    public Integer getNiveauValidateur(String profilCode) {
        if (profilCode == null) return null;
        String code = ProfilCodeNormalizer.normaliser(profilCode);
        return switch (code) {
            case VALIDATEUR_NIVEAU_3 -> 3;
            case VALIDATEUR_NIVEAU_2 -> 2;
            case VALIDATEUR_NIVEAU_1 -> 1;
            default -> null;
        };
    }

    /**
     * ✅ NOUVEAU : vrai si le profil est un validateur, quel que soit le niveau.
     */
    public boolean estValidateur(String profilCode) {
        return getNiveauValidateur(profilCode) != null;
    }

    public Set<String> getProfilsAvecMenu(String ressource) {
        return MENUS_PAR_PROFIL.entrySet().stream()
                .filter(e -> e.getValue().contains(ressource))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    public Set<String> getProfilsAvecCrudPlanning() {
        return PROFILS_AVEC_CRUD_PLANNING;
    }

    // ============================================================
    // HELPER PRIVÉ
    // ============================================================

    private boolean contient(Set<String> profilsAutorises, String profilCode) {
        if (profilCode == null) return false;
        String code = ProfilCodeNormalizer.normaliser(profilCode);
        return profilsAutorises.contains(code);
    }
}