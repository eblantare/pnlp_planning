package tg.pnlp.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tg.pnlp.planning.entity.Utilisateur;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

    Optional<Utilisateur> findByUsername(String username);

    Optional<Utilisateur> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByAgentId(UUID agentId);

    List<Utilisateur> findByActifTrue();

    // ✅ MODIFIÉ : chercher par profil (via la table N-N)
    @Query("SELECT DISTINCT u FROM Utilisateur u JOIN u.profils p WHERE p.id = :profilId")
    List<Utilisateur> findByProfilId(@Param("profilId") UUID profilId);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END " +
            "FROM Utilisateur u WHERE u.email = :email AND u.id != :id")
    boolean existsByEmailAndIdNot(@Param("email") String email,
                                  @Param("id") UUID id);

    // ✅ NOUVEAU : charger l'utilisateur avec ses profils (pour le login)
    @Query("SELECT DISTINCT u FROM Utilisateur u LEFT JOIN FETCH u.profils WHERE u.username = :username")
    Optional<Utilisateur> findByUsernameWithProfils(@Param("username") String username);
    // Ajouter dans UtilisateurRepository :

    @Query("SELECT DISTINCT u FROM Utilisateur u JOIN u.profils p WHERE p.code = :code AND u.actif = true")
    List<Utilisateur> findByProfilCode(@Param("code") String code);
}