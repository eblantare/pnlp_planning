package tg.pnlp.planning.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourOccupationDTO {
    private LocalDate date;
    private String activiteTitre;
}