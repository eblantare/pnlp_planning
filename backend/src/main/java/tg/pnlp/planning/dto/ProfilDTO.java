package tg.pnlp.planning.dto;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfilDTO {
    private UUID id;
    private String code;
    private String libelle;
    private String description;
    private Integer niveau;
    private Boolean actif;
}