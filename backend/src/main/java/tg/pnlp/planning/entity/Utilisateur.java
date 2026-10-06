package tg.pnlp.planning.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true)
    private String email;
    @Column(length = 20)
    private String telephone;

    // ✅ NOUVEAU : plusieurs profils
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "utilisateur_profils",
            joinColumns = @JoinColumn(name = "utilisateur_id"),
            inverseJoinColumns = @JoinColumn(name = "profil_id")
    )
    @Builder.Default
    private Set<Profil> profils = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(nullable = false)
    @Builder.Default
    private Boolean actif = false;

    @Column(name = "derniere_connexion")
    private LocalDateTime derniereConnexion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ============================================================
    // ✅ MÉTHODES UTILITAIRES
    // ============================================================

    /**
     * Retourne la liste des codes de profils (ex: ["ADMIN", "COORDINATEUR"]).
     */
    public Set<String> getProfilCodes() {
        return profils.stream()
                .map(Profil::getCode)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Vérifie si l'utilisateur a AU MOINS UN des rôles donnés.
     */
    public boolean hasAnyRole(String... codes) {
        if (codes == null || codes.length == 0) return false;
        Set<String> userCodes = getProfilCodes();
        for (String code : codes) {
            if (userCodes.contains(code)) return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Utilisateur utilisateur = (Utilisateur) o;
        return id != null && Objects.equals(id, utilisateur.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}