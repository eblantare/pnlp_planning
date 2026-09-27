package tg.pnlp.planning.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.pnlp.planning.dto.ProfilDTO;
import tg.pnlp.planning.entity.Profil;
import tg.pnlp.planning.exception.BusinessException;
import tg.pnlp.planning.exception.ResourceNotFoundException;
import tg.pnlp.planning.repository.ProfilRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfilService {

    private final ProfilRepository profilRepository;

    public List<ProfilDTO> getAllProfils() {
        return profilRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ProfilDTO> getProfilsActifs() {
        return profilRepository.findByActifTrue().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ProfilDTO getProfilById(UUID id) {
        Profil profil = profilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil non trouvé: " + id));
        return convertToDTO(profil);
    }

    @Transactional
    public ProfilDTO createProfil(ProfilDTO dto) {
        if (profilRepository.existsByCode(dto.getCode())) {
            throw new BusinessException("Un profil avec ce code existe déjà: " + dto.getCode());
        }

        Profil profil = Profil.builder()
                .code(dto.getCode().toUpperCase())
                .libelle(dto.getLibelle())
                .description(dto.getDescription())
                .niveau(dto.getNiveau() != null ? dto.getNiveau() : 1)
                .actif(false)   // ✅ MODIFICATION : inactif par défaut
                .build();

        profil = profilRepository.save(profil);
        log.info("Profil créé (inactif par défaut): {}", profil.getCode());
        return convertToDTO(profil);
    }

    @Transactional
    public ProfilDTO updateProfil(UUID id, ProfilDTO dto) {
        Profil profil = profilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil non trouvé: " + id));

        if (!profil.getCode().equals(dto.getCode()) &&
                profilRepository.existsByCode(dto.getCode())) {
            throw new BusinessException("Un profil avec ce code existe déjà: " + dto.getCode());
        }

        profil.setCode(dto.getCode().toUpperCase());
        profil.setLibelle(dto.getLibelle());
        profil.setDescription(dto.getDescription());
        profil.setNiveau(dto.getNiveau());
        if (dto.getActif() != null) {
            profil.setActif(dto.getActif());
        }

        profil = profilRepository.save(profil);
        log.info("Profil modifié: {}", profil.getCode());
        return convertToDTO(profil);
    }

    /**
     * ✅ NOUVEAU : Basculer l'état actif/inactif
     */
    @Transactional
    public ProfilDTO toggleActif(UUID id) {
        Profil profil = profilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil non trouvé: " + id));

        boolean nouvelEtat = !profil.getActif();
        profil.setActif(nouvelEtat);
        profil = profilRepository.save(profil);

        log.info("Profil {} {}", profil.getCode(), nouvelEtat ? "activé" : "désactivé");
        return convertToDTO(profil);
    }

    /**
     * ✅ MODIFIÉ : Suppression réelle (pas de désactivation)
     */
    @Transactional
    public void deleteProfil(UUID id) {
        Profil profil = profilRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil non trouvé: " + id));

        profilRepository.delete(profil);
        log.info("Profil supprimé: {}", profil.getCode());
    }

    private ProfilDTO convertToDTO(Profil profil) {
        return ProfilDTO.builder()
                .id(profil.getId())
                .code(profil.getCode())
                .libelle(profil.getLibelle())
                .description(profil.getDescription())
                .niveau(profil.getNiveau())
                .actif(profil.getActif())
                .build();
    }
}