package tg.pnlp.planning.util;

import tg.pnlp.planning.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * Valide les mots de passe selon les règles de sécurité :
 * - Minimum 8 caractères
 * - Au moins 1 chiffre
 * - Au moins 1 caractère spécial
 * - Au moins 1 lettre majuscule
 */
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;

    public static void valider(String password) {
        List<String> erreurs = new ArrayList<>();

        if (password == null || password.length() < MIN_LENGTH) {
            erreurs.add("au moins " + MIN_LENGTH + " caractères");
        }
        if (password == null || !password.matches(".*\\d.*")) {
            erreurs.add("au moins 1 chiffre");
        }
        if (password == null || !password.matches(".*[A-Z].*")) {
            erreurs.add("au moins 1 lettre majuscule");
        }
        if (password == null || !password.matches(".*[!@#$%^&*(),.?\":{}|<>_\\-+=\\[\\]\\\\/~`';].*")) {
            erreurs.add("au moins 1 caractère spécial (!@#$%^&*...)");
        }

        if (!erreurs.isEmpty()) {
            throw new BusinessException(
                    "Le mot de passe doit contenir : " + String.join(", ", erreurs)
            );
        }
    }

    /**
     * Retourne la liste des règles pour le frontend
     */
    public static List<String> getRegles() {
        return List.of(
                "Au moins 8 caractères",
                "Au moins 1 chiffre (0-9)",
                "Au moins 1 lettre majuscule (A-Z)",
                "Au moins 1 caractère spécial (!@#$%^&*...)"
        );
    }
}
