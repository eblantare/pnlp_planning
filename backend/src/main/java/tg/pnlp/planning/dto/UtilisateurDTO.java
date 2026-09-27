package tg.pnlp.planning.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisateurDTO {
    private UUID id;
    private String username;
    private String email;
    private UUID profilId;
    private String profilCode;
    private String profilLibelle;
    private UUID agentId;
    private String agentNom;
    private Boolean actif;
    private LocalDateTime derniereConnexion;
    private LocalDateTime createdAt;
}