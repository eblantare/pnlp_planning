package tg.pnlp.planning.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUtilisateurRequest {

    @Email(message = "Email invalide")
    private String email;

    private String password;

    private UUID profilId;

    private UUID agentId;

    private Boolean actif;
}