package tg.pnlp.planning.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "affectations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "activite_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Affectation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activite_id", nullable = false)
    private Activite activite;

    @Column(length = 100)
    private String role;

    @Column(name = "date_affectation")
    @Builder.Default
    private LocalDateTime dateAffectation = LocalDateTime.now();
}