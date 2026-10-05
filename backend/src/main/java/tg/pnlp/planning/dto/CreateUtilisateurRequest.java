package tg.pnlp.planning.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUtilisateurRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 100, message = "Le nom d'utilisateur doit contenir entre 3 et 100 caractères")
    private String username;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    private String password;

    @Email(message = "Email invalide")
    private String email;

    // ✅ NOUVEAU : liste de profils
    @NotEmpty(message = "Au moins un profil est obligatoire")
    private List<UUID> profilIds;

    @NotNull(message = "L'agent associé est obligatoire")
    private UUID agentId;
}