package tg.pnlp.planning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "activites")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Activite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "date_fin", nullable = false)
    private LocalDate dateFin;

    @Column(name = "nombre_jours")
    private Integer nombreJours;

    @Column(length = 255)
    private String lieu;

    @Column(name = "source_financement", length = 255)
    private String sourceFinancement;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private StatutActivite statut = StatutActivite.PLANIFIEE;

    @Column(columnDefinition = "TEXT")
    private String commentaires;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Utilisateur createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "activite", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Affectation> affectations = new HashSet<>();

    // Calcul automatique du nombre de jours
    @PrePersist
    @PreUpdate
    public void calculerNombreJours() {
        if (dateDebut != null && dateFin != null) {
            this.nombreJours = (int) (dateFin.toEpochDay() - dateDebut.toEpochDay()) + 1;
        }
    }

    public enum StatutActivite {
        PLANIFIEE,
        EN_COURS,
        TERMINEE,
        ANNULEE,
        REPORTEE
    }
}