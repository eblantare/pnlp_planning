package tg.pnlp.planning.dto;

import lombok.*;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDTO {
    private UUID id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String poste;
    private String unite;          // ✅ NOUVEAU
    private Boolean actif;
}