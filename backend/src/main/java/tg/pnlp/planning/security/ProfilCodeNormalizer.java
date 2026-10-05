package tg.pnlp.planning.security;

import java.util.Set;

/**
 * ✅ SOURCE UNIQUE DE VÉRITÉ pour la normalisation des codes de profil.
 *
 * Cette classe est utilisée partout où un code de profil venant de la base
 * (ou d'un JWT) doit être aligné avec les constantes utilisées dans le code
 * métier (PermissionService) et dans Spring Security (SecurityConfig).
 *
 * ⚠️ RÈGLE : si tu ajoutes un nouveau profil dans la base, ajoute sa
 *    correspondance ICI, et nulle part ailleurs.
 */
public final class ProfilCodeNormalizer {

    private ProfilCodeNormalizer() {
        // Classe utilitaire : pas d'instanciation
    }

    // ============================================================
    // CODES DE PROFIL NORMALISÉS (référence unique)
    // ============================================================
    public static final String SUPER_ADMIN          = "SUPER_ADMIN";
    public static final String ADMIN                = "ADMIN";
    public static final String CHEF_SERVICE         = "CHEF_SERVICE";
    public static final String PLANIFICATEUR        = "PLANIFICATEUR";
    public static final String VALIDATEUR_NIVEAU_1  = "VALIDATEUR_NIVEAU_1";
    public static final String VALIDATEUR_NIVEAU_2  = "VALIDATEUR_NIVEAU_2";
    public static final String VALIDATEUR_NIVEAU_3  = "VALIDATEUR_NIVEAU_3";
    public static final String CONSULTANT           = "CONSULTANT";
    public static final String OBSERVATEUR          = "OBSERVATEUR";
    public static final String AGENT                = "AGENT";

    /**
     * Ensemble des codes normalisés connus.
     * Utile pour les validations (« ce profil est-il reconnu ? »).
     */
    public static final Set<String> CODES_CONNUS = Set.of(
            SUPER_ADMIN, ADMIN, CHEF_SERVICE, PLANIFICATEUR,
            VALIDATEUR_NIVEAU_1, VALIDATEUR_NIVEAU_2, VALIDATEUR_NIVEAU_3,
            CONSULTANT, OBSERVATEUR, AGENT
    );

    /**
     * ✅ Normalise un code de profil brut (venant de la BD ou d'un JWT)
     *    vers son code canonique utilisé dans le code Java.
     *
     * Exemples :
     *   "PLAN"     → "PLANIFICATEUR"
     *   "PLANIF"   → "PLANIFICATEUR"
     *   "val_1"    → "VALIDATEUR_NIVEAU_1"
     *   "AGENT"    → "AGENT"
     *   "superadmin" → "SUPER_ADMIN"
     *   "inconnu"  → "INCONNU" (renvoyé tel quel, en majuscules)
     *
     * @param code code brut (peut contenir espaces, casse variable, abréviations)
     * @return code normalisé (jamais null si l'entrée est non-null)
     */
    public static String normaliser(String code) {
        if (code == null) return null;
        String c = code.trim().toUpperCase();
        return switch (c) {
            case "OBS", "OBSERVATEUR"                                          -> OBSERVATEUR;
            case "AGENT", "AGENTS"                                             -> AGENT;
            case "CONS", "CONSULT", "CONSULTANT"                               -> CONSULTANT;
            case "CHEF_SER", "CHEFSERVICE", "CHEF-SERVICE", "CHEF_SERVICE"     -> CHEF_SERVICE;
            case "PLAN", "PLANIF", "PLANIFICATEUR"                             -> PLANIFICATEUR;
            case "SUPERADMIN", "SUPER-ADMIN", "SUPER_ADMIN"                    -> SUPER_ADMIN;
            case "ADMIN", "ADMINISTRATEUR"                                     -> ADMIN;
            case "VAL", "VAL_1", "VALIDATEUR", "VALIDATEUR_NIVEAU_1",
                 "VALIDATEUR NIVEAU 1"                                         -> VALIDATEUR_NIVEAU_1;
            case "VAL_2", "VALIDATEUR_NIVEAU_2", "VALIDATEUR NIVEAU 2"         -> VALIDATEUR_NIVEAU_2;
            case "VAL_3", "VALIDATEUR_NIVEAU_3", "VALIDATEUR NIVEAU 3"         -> VALIDATEUR_NIVEAU_3;
            default -> c;   // On renvoie tel quel en majuscules (utile pour debug)
        };
    }

    /**
     * ✅ Vérifie si un code brut correspond à un profil normalisé donné.
     *    Utilise la normalisation des deux côtés.
     */
    public static boolean correspond(String codeBrut, String codeNormalise) {
        if (codeBrut == null || codeNormalise == null) return false;
        return normaliser(codeBrut).equals(normaliser(codeNormalise));
    }
}