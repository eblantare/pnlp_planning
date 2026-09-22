package tg.pnlp.planning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "indisponibilites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Indisponibilite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TypeIndisponibilite type;

    @Column(columnDefinition = "TEXT")
    private String motif;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public boolean chevauche(LocalDate debut, LocalDate fin) {
        return !(fin.isBefore(this.dateDebut) || debut.isAfter(this.dateFin));
    }

    public enum TypeIndisponibilite {
        CONGE,
        MISSION,
        FORMATION,
        MALADIE,
        AUTRE
    }
}