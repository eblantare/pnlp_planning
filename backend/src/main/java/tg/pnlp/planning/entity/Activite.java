package tg.pnlp.planning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "activites")
@Getter
@Setter
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

    // ✅ Dates NULLABLES pour permettre les brouillons
    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
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
    private StatutActivite statut = StatutActivite.BROUILLON;

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

    @PrePersist
    @PreUpdate
    public void calculerNombreJours() {
        if (dateDebut != null && dateFin != null) {
            this.nombreJours = (int) (dateFin.toEpochDay() - dateDebut.toEpochDay()) + 1;
        } else {
            this.nombreJours = null;
        }
    }

    public boolean peutAvoirDesAgents() {
        return this.statut != StatutActivite.BROUILLON;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Activite activite = (Activite) o;
        return id != null && Objects.equals(id, activite.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public enum StatutActivite {
        BROUILLON,
        PLANIFIEE,
        EN_COURS,
        TERMINEE,
        ANNULEE,
        REPORTEE
    }
}