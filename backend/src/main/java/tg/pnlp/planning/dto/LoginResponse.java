package tg.pnlp.planning.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String token;
    private String tokenType;      // "Bearer"
    private long expiresIn;        // en millisecondes
    private UtilisateurDTO utilisateur;
}
