package tg.pnlp.planning.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tg.pnlp.planning.entity.Profil;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfilRepository extends JpaRepository<Profil, UUID> {

    Optional<Profil> findByCode(String code);

    List<Profil> findByActifTrue();

    boolean existsByCode(String code);
}