package tg.pnlp.planning.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUtilisateurRequest {

    @Email(message = "Email invalide")
    private String email;

    private String password;

    // ✅ NOUVEAU : liste de profils
    private List<UUID> profilIds;

    private UUID agentId;

    private Boolean actif;
}