package tg.pnlp.planning.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "affectations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "activite_id"}))
@Getter
@Setter
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

    @Column(name = "zone_affectation", length = 255)
    private String zoneAffectation;

    @Column(name = "date_affectation")
    @Builder.Default
    private LocalDateTime dateAffectation = LocalDateTime.now();

    // ✅ equals/hashCode uniquement sur l'ID
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Affectation that = (Affectation) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Column(name = "en_conflit")
    @Builder.Default
    private Boolean enConflit = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_validation", length = 20)
    private ActionValidation actionValidation;   // EN_ATTENTE, RETIRER, REMPLACER, FORCER

    @Column(name = "force")
    @Builder.Default
    private Boolean force = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_remplacant_id")
    private Agent agentRemplacant;

    @Column(name = "motif_conflit", length = 500)
    private String motifConflit;

    public enum ActionValidation { EN_ATTENTE, RETIRER, REMPLACER, FORCER }
}