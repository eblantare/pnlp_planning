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
    @Column(name = "type_lieu", length = 20)
    @Builder.Default
    private TypeLieu typeLieu = TypeLieu.NON_RESIDENT;

    @Column(name = "au_programme", nullable = false)
    @Builder.Default
    private Boolean auProgramme = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private StatutActivite statut = StatutActivite.BROUILLON;

    /**
     * ✅ NOUVEAU : niveau de validateur en cours (1, 2, 3).
     * Nullable : uniquement renseigné si le statut est EN_ATTENTE_VALIDATION.
     */
    @Column(name = "niveau_validation_actuel")
    private Integer niveauValidationActuel;

    /**
     * ✅ NOUVEAU : niveau du validateur qui a validé définitivement (traçabilité).
     */
    @Column(name = "valide_par_niveau")
    private Integer valideParNiveau;

    /**
     * ✅ NOUVEAU : niveau du validateur qui a renvoyé l'activité au planificateur.
     */
    @Column(name = "renvoye_par_niveau")
    private Integer renvoyeParNiveau;

    @Column(columnDefinition = "TEXT")
    private String commentaires;

    @Column(name = "tdr_filename", length = 255)
    private String tdrFilename;

    @Column(name = "tdr_path", length = 500)
    private String tdrPath;

    @Column(name = "tdr_uploaded_at")
    private LocalDateTime tdrUploadedAt;

    @Column(name = "tdr_conforme")
    @Builder.Default
    private Boolean tdrConforme = false;

    @Column(name = "ordre_mission_filename", length = 255)
    private String ordreMissionFilename;

    @Column(name = "ordre_mission_path", length = 500)
    private String ordreMissionPath;

    @Column(name = "ordre_mission_uploaded_at")
    private LocalDateTime ordreMissionUploadedAt;

    @Column(name = "ordre_mission_conforme")
    @Builder.Default
    private Boolean ordreMissionConforme = false;

    @Column(name = "lettre_invitation_filename", length = 255)
    private String lettreInvitationFilename;

    @Column(name = "lettre_invitation_path", length = 500)
    private String lettreInvitationPath;

    @Column(name = "lettre_invitation_uploaded_at")
    private LocalDateTime lettreInvitationUploadedAt;

    @Column(name = "lettre_invitation_conforme")
    @Builder.Default
    private Boolean lettreInvitationConforme = false;

    @Column(name = "tdr_email_envoye")
    @Builder.Default
    private Boolean tdrEmailEnvoye = false;

    @Column(name = "ordre_mission_email_envoye")
    @Builder.Default
    private Boolean ordreMissionEmailEnvoye = false;

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

    /**
     * ✅ Calcule le nombre de jours d'une activité.
     * RÈGLE : bonus +1 UNIQUEMENT si NON_RESIDENT ET hors programme.
     */
    @PrePersist
    @PreUpdate
    public void calculerNombreJours() {
        if (dateDebut != null && dateFin != null) {
            int base = (int) (dateFin.toEpochDay() - dateDebut.toEpochDay()) + 1;

            boolean isNonResident = (typeLieu == TypeLieu.NON_RESIDENT);
            boolean isHorsProgramme = !Boolean.TRUE.equals(this.auProgramme);

            if (isNonResident && isHorsProgramme) {
                base = base + 1;
            }

            this.nombreJours = base > 0 ? base : 0;
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
        EN_ATTENTE_VALIDATION,      // ✅ statut unique, niveau porté par niveauValidationActuel
        RENVOYE_POUR_CORRECTION,    // ✅ renvoi au planificateur
        PLANIFIEE,
        EN_COURS,
        TERMINEE,
        ANNULEE,
        REPORTEE
    }

    public enum TypeLieu {
        RESIDENT,
        NON_RESIDENT
    }
}