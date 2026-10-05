package tg.pnlp.planning.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisateurDTO {
    private UUID id;
    private String username;
    private String email;

    // ✅ NOUVEAU : liste de profils
    private List<UUID> profilIds;
    private List<String> profilCodes;
    private List<String> profilLibelles;

    private UUID agentId;
    private String agentNom;
    private Boolean actif;
    private LocalDateTime derniereConnexion;
    private LocalDateTime createdAt;
}