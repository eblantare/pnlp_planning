import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
    PlanningService,
    Activite,
    PlanningMensuel,
    ConflitAgent
} from '../../core/services/planning.service';
import { AgentService, Agent } from '../../core/services/agent.service';
import { ExportService } from '../../core/services/export.service';
import { ActionButton, ActionButtonsComponent } from '../../shared/components/action-buttons/action-buttons.component';

interface ZoneGroup {
    id: string;
    region: string;
    districts: Set<string>;
    agentIds: string[];
}

@Component({
    selector: 'app-planning',
    standalone: true,
    imports: [CommonModule, FormsModule, ActionButtonsComponent],
    templateUrl: './planning.component.html',
    styleUrls: ['./planning.component.css'],
    encapsulation: ViewEncapsulation.None
})
export class PlanningComponent implements OnInit {
    // Données
    activites: Activite[] = [];
    filteredActivites: Activite[] = [];
    paginatedActivites: Activite[] = [];
    agents: Agent[] = [];
    filteredAgents: Agent[] = [];
    planningMensuel: PlanningMensuel | null = null;

    annee = new Date().getFullYear();
    mois = new Date().getMonth() + 1;

    // Filtres
    searchTerm = '';
    filterStatut = '';
    searchAgent = '';

    // Pagination
    currentPage = 1;
    itemsPerPage = 10;
    totalItems = 0;
    pageSizeOptions = [5, 10, 25, 50];

    // Formulaire
    nouvelleActivite: Activite = this.getEmptyActivite();
    editingActivite: Activite | null = null;

    // Zones groupées
    zoneGroups: ZoneGroup[] = [];
    private zoneGroupCounter = 0;

    // Régions et districts
    regions: { [key: string]: string[] } = {
        'Grand Lomé': ['Golfe', 'Agoènyivé'],
        'Maritime': ['Avé', 'Bas-Mono', 'Lacs', 'Vo', 'Yoto', 'Zio'],
        'Plateaux': ['Agou', 'Akébou', 'Amou', 'Anié', 'Danyi', 'Est-Mono', 'Haho', 'Kloto', 'Kpélé', 'Moyen-Mono', 'Ogou', 'Wawa'],
        'Centrale': ['Blitta', 'Mô', 'Sotouboua', 'Tchamba', 'Tchoudjo'],
        'Kara': ['Bassar', 'Binah', 'Dankpen', 'Doufelgou', 'Guérin-Kouka', 'Kéran', 'Kozah'],
        'Savanes': ['Cinkancé', 'Kpendjal', 'Kpendjal-Ouest', 'Oti', 'Oti-Sud', 'Tandjouaré', 'Tône']
    };

    // Modals
    showForm = false;
    showDetailModal = false;
    showConflitModal = false;
    showRemplacementModal = false;
    selectedActivite: Activite | null = null;

    // Gestion des conflits
    conflitsDetectes: ConflitAgent[] = [];
    agentsForces: Set<string> = new Set();
    activiteEnAttente: Activite | null = null;

    // Slots vides
    slotsARemplacer: { [groupIndex: number]: { district: string }[] } = {};
    remplacementEnCours: { groupIndex: number; slotIndex: number } | null = null;
    remplacantsDisponibles: Agent[] = [];
    isLoadingRemplacants = false;

    // Recherche par groupe
    searchAgentByGroup: { [groupIndex: number]: string } = {};

    // États
    isLoading = false;
    errorMessage = '';
    successMessage = '';

    Math = Math;

    constructor(
        private planningService: PlanningService,
        private agentService: AgentService,
        private exportService: ExportService
    ) { }

    ngOnInit(): void {
        this.loadAgents();
        this.loadPlanning();
    }

    getEmptyActivite(): Activite {
        return {
            titre: '',
            commentaires: '',
            // ✅ Pas de dateDebut/dateFin/lieu : laissés undefined
            sourceFinancement: '',
            statut: 'BROUILLON',
            agentIds: []
        };
    }

    isBrouillon(): boolean {
        return this.nouvelleActivite.statut === 'BROUILLON';
    }

    isChampsRequis(): boolean {
        return !this.isBrouillon();
    }

    /**
     * ✅ Parse une date "YYYY-MM-DD" en heure LOCALE
     */
    private parseLocalDate(dateStr: string | undefined | null): Date | null {
        if (!dateStr) return null;
        if (dateStr.includes('T')) {
            return new Date(dateStr);
        }
        const parts = dateStr.split('-').map(Number);
        if (parts.length !== 3 || parts.some(isNaN)) return null;
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }

    loadAgents(): void {
        this.agentService.getAllAgents().subscribe({
            next: (data) => {
                this.agents = data.filter(a => a.actif === true);
                this.filteredAgents = this.agents;
            },
            error: (err) => console.error('Erreur chargement agents', err)
        });
    }

    loadPlanning(): void {
        this.isLoading = true;
        this.planningService.getPlanningMensuel(this.annee, this.mois).subscribe({
            next: (data) => {
                this.planningMensuel = data;
                this.activites = this.trierActivites(data.activites || []);
                this.applyFilters();
                this.isLoading = false;
            },
            error: (err) => {
                console.error('Erreur chargement planning', err);
                this.isLoading = false;
            }
        });
    }

    // ========== TRI ==========
    trierActivites(activites: Activite[]): Activite[] {
        const ordreStatut: { [key: string]: number } = {
            'BROUILLON': 1,
            'PLANIFIEE': 2,
            'EN_COURS': 3,
            'REPORTEE': 4,
            'TERMINEE': 5,
            'ANNULEE': 6
        };

        return [...activites].sort((a, b) => {
            const ordreA = ordreStatut[a.statut || 'PLANIFIEE'] || 99;
            const ordreB = ordreStatut[b.statut || 'PLANIFIEE'] || 99;
            if (ordreA !== ordreB) return ordreA - ordreB;
            const da = this.parseLocalDate(a.dateDebut)?.getTime() || Number.MAX_SAFE_INTEGER;
            const db = this.parseLocalDate(b.dateDebut)?.getTime() || Number.MAX_SAFE_INTEGER;
            return da - db;
        });
    }

    // ========== STATS ==========
    getAgentsOccupes(): number {
        const agentsOccupes = new Set<string>();
        this.activites
            .filter(a => a.statut === 'EN_COURS')
            .forEach(a => a.agentIds?.forEach(id => agentsOccupes.add(id)));
        return agentsOccupes.size;
    }

    // ========== FILTRES ==========
    applyFilters(): void {
        let filtered = [...this.activites];

        if (this.searchTerm) {
            const term = this.searchTerm.toLowerCase();
            filtered = filtered.filter(a =>
                a.titre?.toLowerCase().includes(term) ||
                a.lieu?.toLowerCase().includes(term) ||
                a.commentaires?.toLowerCase().includes(term)
            );
        }

        if (this.filterStatut) {
            filtered = filtered.filter(a => a.statut === this.filterStatut);
        }

        this.filteredActivites = filtered;
        this.totalItems = filtered.length;
        this.currentPage = 1;
        this.updatePaginated();
    }

    updatePaginated(): void {
        const start = (this.currentPage - 1) * this.itemsPerPage;
        const end = start + this.itemsPerPage;
        this.paginatedActivites = this.filteredActivites.slice(start, end);
    }

    resetFilters(): void {
        this.searchTerm = '';
        this.filterStatut = '';
        this.applyFilters();
    }

    hasActiveFilters(): boolean {
        return !!(this.searchTerm || this.filterStatut);
    }

    // ========== PAGINATION ==========
    onPageChange(page: number): void {
        if (page < 1 || page > this.totalPages) return;
        this.currentPage = page;
        this.updatePaginated();
    }

    onItemsPerPageChange(): void {
        this.currentPage = 1;
        this.updatePaginated();
    }

    get totalPages(): number {
        return Math.ceil(this.totalItems / this.itemsPerPage);
    }

    get pages(): number[] {
        const pages: number[] = [];
        const maxVisible = 5;
        let start = Math.max(1, this.currentPage - Math.floor(maxVisible / 2));
        let end = Math.min(this.totalPages, start + maxVisible - 1);
        if (end - start + 1 < maxVisible) {
            start = Math.max(1, end - maxVisible + 1);
        }
        for (let i = start; i <= end; i++) pages.push(i);
        return pages;
    }

    getStartIndex(): number {
        return this.totalItems === 0 ? 0 : (this.currentPage - 1) * this.itemsPerPage + 1;
    }

    getEndIndex(): number {
        return Math.min(this.currentPage * this.itemsPerPage, this.totalItems);
    }

    // ========== FORMULAIRE ==========
    openCreateForm(): void {
        this.editingActivite = null;
        this.nouvelleActivite = this.getEmptyActivite();
        this.zoneGroups = [];
        this.slotsARemplacer = {};
        this.searchAgentByGroup = {};
        this.ajouterGroupe();
        this.showForm = true;
    }

    editActivite(activite: Activite): void {
        this.editingActivite = activite;
        this.nouvelleActivite = {
            ...activite,
            agentIds: activite.agentIds ? [...activite.agentIds] : []
        };

        this.slotsARemplacer = {};
        this.searchAgentByGroup = {};
        this.zoneGroups = [];

        if (activite.agentIds && activite.agentIds.length > 0) {
            const zonesMap = new Map<string, string[]>();
            activite.agentIds.forEach((id, index) => {
                const zone = activite.agentZonesList?.[index];
                const key = zone || '__sans_zone__';
                if (!zonesMap.has(key)) {
                    zonesMap.set(key, []);
                }
                zonesMap.get(key)!.push(id);
            });

            zonesMap.forEach((agentIds, zoneKey) => {
                const group: ZoneGroup = {
                    id: this.generateGroupId(),
                    region: '',
                    districts: new Set(),
                    agentIds: agentIds
                };

                if (zoneKey !== '__sans_zone__') {
                    const parsed = this.parseZoneString(zoneKey);
                    if (parsed) {
                        group.region = parsed.region;
                        group.districts = new Set(parsed.districts);
                    }
                }

                this.zoneGroups.push(group);
            });
        }

        if (this.zoneGroups.length === 0) {
            this.ajouterGroupe();
        }

        this.showForm = true;
    }

    closeForm(): void {
        this.showForm = false;
        this.editingActivite = null;
        this.nouvelleActivite = this.getEmptyActivite();
        this.zoneGroups = [];
        this.slotsARemplacer = {};
        this.searchAgentByGroup = {};
        this.conflitsDetectes = [];
        this.agentsForces.clear();
        this.activiteEnAttente = null;
    }

    calculerNombreJours(): void {
        const debut = this.parseLocalDate(this.nouvelleActivite.dateDebut);
        const fin = this.parseLocalDate(this.nouvelleActivite.dateFin);
        if (debut && fin) {
            const diff = Math.ceil((fin.getTime() - debut.getTime()) / (1000 * 60 * 60 * 24)) + 1;
            this.nouvelleActivite.nombreJours = diff > 0 ? diff : 0;
        } else {
            this.nouvelleActivite.nombreJours = undefined;
        }
    }

    onStatutChange(newStatut: string): void {
        const ancienStatut = this.nouvelleActivite.statut;
        this.nouvelleActivite.statut = newStatut;

        if (newStatut === 'BROUILLON') {
            this.zoneGroups.forEach(g => g.agentIds = []);
            this.slotsARemplacer = {};
            this.showSuccess('Mode Brouillon : les participants seront associés lors de la planification');
        }

        if (ancienStatut === 'BROUILLON' && newStatut === 'PLANIFIEE') {
            const erreurs: string[] = [];
            if (!this.nouvelleActivite.dateDebut) erreurs.push('la date de début');
            if (!this.nouvelleActivite.dateFin) erreurs.push('la date de fin');
            if (!this.nouvelleActivite.lieu) erreurs.push('le lieu');

            if (erreurs.length > 0) {
                this.showError(
                    `Veuillez renseigner ${erreurs.join(', ')} avant de planifier.`
                );
            }
        }
    }

    // ========== GESTION DES GROUPES DE ZONES ==========
    private generateGroupId(): string {
        return `group_${++this.zoneGroupCounter}_${Date.now()}`;
    }

    ajouterGroupe(): void {
        this.zoneGroups.push({
            id: this.generateGroupId(),
            region: '',
            districts: new Set<string>(),
            agentIds: []
        });
    }

    supprimerGroupe(index: number): void {
        if (this.zoneGroups.length <= 1) return;
        if (!confirm('Supprimer ce groupe de zone et ses participants ?')) return;
        this.zoneGroups.splice(index, 1);
        const newSlots: { [k: number]: { district: string }[] } = {};
        Object.keys(this.slotsARemplacer).forEach(k => {
            const idx = parseInt(k, 10);
            if (idx < index) newSlots[idx] = this.slotsARemplacer[idx];
            else if (idx > index) newSlots[idx - 1] = this.slotsARemplacer[idx];
        });
        this.slotsARemplacer = newSlots;
    }

    getRegionsList(): string[] {
        return Object.keys(this.regions);
    }

    getAvailableDistricts(groupIndex: number): string[] {
        const group = this.zoneGroups[groupIndex];
        if (!group || !group.region) return [];
        return this.regions[group.region] || [];
    }

    onRegionChange(groupIndex: number, region: string): void {
        const group = this.zoneGroups[groupIndex];
        if (!group) return;
        group.region = region;
        group.districts.clear();
    }

    toggleDistrict(groupIndex: number, district: string): void {
        const group = this.zoneGroups[groupIndex];
        if (!group) return;
        if (group.districts.has(district)) {
            group.districts.delete(district);
        } else {
            group.districts.add(district);
        }
    }

    isDistrictSelected(groupIndex: number, district: string): boolean {
        const group = this.zoneGroups[groupIndex];
        return group ? group.districts.has(district) : false;
    }

    getGroupZone(groupIndex: number): string {
        const group = this.zoneGroups[groupIndex];
        if (!group || !group.region) return '';
        if (group.districts.size === 0) return group.region;
        return `${group.region}: ${Array.from(group.districts).join(', ')}`;
    }

    // ========== AGENTS PAR GROUPE ==========
    isAgentInGroup(groupIndex: number, agentId: string): boolean {
        const group = this.zoneGroups[groupIndex];
        return group ? group.agentIds.includes(agentId) : false;
    }

    isAgentInAnyGroup(agentId: string): boolean {
        return this.zoneGroups.some(g => g.agentIds.includes(agentId));
    }

    toggleAgentInGroup(groupIndex: number, agentId: string): void {
        if (this.isBrouillon()) {
            this.showError('Un brouillon ne peut pas avoir de participants. Passez le statut à "Planifiée" pour associer des agents.');
            return;
        }

        const group = this.zoneGroups[groupIndex];
        if (!group) return;

        const idx = group.agentIds.indexOf(agentId);
        if (idx > -1) {
            group.agentIds.splice(idx, 1);
        } else {
            if (this.isAgentInAnyGroup(agentId)) {
                this.showError('Cet agent est déjà affecté à un autre groupe');
                return;
            }
            group.agentIds.push(agentId);
        }
    }

    buildAgentZones(): { [agentId: string]: string } {
        const result: { [agentId: string]: string } = {};
        this.zoneGroups.forEach((group, index) => {
            const zone = this.getGroupZone(index);
            if (zone) {
                group.agentIds.forEach(id => {
                    result[id] = zone;
                });
            }
        });
        return result;
    }

    buildAllAgentIds(): string[] {
        const ids: string[] = [];
        this.zoneGroups.forEach(g => ids.push(...g.agentIds));
        return ids;
    }

    getTotalSelectedAgents(): number {
        return this.zoneGroups.reduce((sum, g) => sum + g.agentIds.length, 0);
    }

    private parseZoneString(zone: string): { region: string; districts: string[] } | null {
        if (!zone) return null;
        const [region, districtsPart] = zone.split(':').map(s => s.trim());
        if (!region) return null;
        const districts = districtsPart
            ? districtsPart.split(',').map(s => s.trim()).filter(s => s)
            : [];
        return { region, districts };
    }

    // ========== CRÉATION / MODIFICATION ==========
    creerActivite(): void {
        if (!this.nouvelleActivite.titre) {
            this.showError('Le titre est obligatoire');
            return;
        }

        const isBrouillon = this.isBrouillon();

        if (!isBrouillon) {
            const erreurs: string[] = [];
            if (!this.nouvelleActivite.dateDebut) erreurs.push('la date de début');
            if (!this.nouvelleActivite.dateFin) erreurs.push('la date de fin');
            if (!this.nouvelleActivite.lieu) erreurs.push('le lieu');
            if (this.getTotalSelectedAgents() === 0) erreurs.push('au moins un participant');

            if (erreurs.length > 0) {
                this.showError(`Veuillez renseigner ${erreurs.join(', ')}.`);
                return;
            }
        }

        const allAgentIds = isBrouillon ? [] : this.buildAllAgentIds();
        const agentZones = isBrouillon ? {} : this.buildAgentZones();

        // ✅ Convertir les chaînes vides en undefined
        const activiteFinale: Activite = {
            ...this.nouvelleActivite,
            dateDebut: this.nouvelleActivite.dateDebut || undefined,
            dateFin: this.nouvelleActivite.dateFin || undefined,
            lieu: this.nouvelleActivite.lieu || undefined,
            agentIds: allAgentIds,
            agentZones: agentZones
        };

        this.isLoading = true;

        if (this.editingActivite && this.editingActivite.id) {
            this.planningService.updateActivite(this.editingActivite.id, activiteFinale).subscribe({
                next: (reponse) => {
                    if (reponse.succes) {
                        this.showSuccess('Activité modifiée');
                        this.closeForm();
                        this.loadPlanning();
                    } else {
                        this.conflitsDetectes = reponse.conflits;
                        this.activiteEnAttente = { ...activiteFinale };
                        this.showConflitModal = true;
                    }
                    this.isLoading = false;
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur modification');
                    this.isLoading = false;
                }
            });
        } else {
            this.planningService.creerActivite(activiteFinale).subscribe({
                next: (reponse) => {
                    if (reponse.succes) {
                        const dateDebut = this.parseLocalDate(reponse.activite?.dateDebut);
                        if (dateDebut) {
                            this.mois = dateDebut.getMonth() + 1;
                            this.annee = dateDebut.getFullYear();
                        }
                        this.showSuccess('Activité créée');
                        this.closeForm();
                        this.loadPlanning();
                    } else {
                        this.conflitsDetectes = reponse.conflits;
                        this.activiteEnAttente = { ...activiteFinale };
                        this.showConflitModal = true;
                    }
                    this.isLoading = false;
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur création');
                    this.isLoading = false;
                }
            });
        }
    }

    // ========== GESTION DES CONFLITS ==========
    toggleAgentForce(agentId: string): void {
        if (this.agentsForces.has(agentId)) {
            this.agentsForces.delete(agentId);
        } else {
            this.agentsForces.add(agentId);
        }
    }

    isAgentForce(agentId: string): boolean {
        return this.agentsForces.has(agentId);
    }

    closeConflitModal(): void {
        this.showConflitModal = false;
        this.conflitsDetectes = [];
        this.agentsForces.clear();
        this.activiteEnAttente = null;
    }

    retirerAgentConflit(agentId: string): void {
        let groupIndex = -1;
        let group: ZoneGroup | null = null;

        for (let i = 0; i < this.zoneGroups.length; i++) {
            if (this.zoneGroups[i].agentIds.includes(agentId)) {
                groupIndex = i;
                group = this.zoneGroups[i];
                break;
            }
        }

        if (group && groupIndex > -1) {
            group.agentIds = group.agentIds.filter(id => id !== agentId);
            if (!this.slotsARemplacer[groupIndex]) {
                this.slotsARemplacer[groupIndex] = [];
            }
            this.slotsARemplacer[groupIndex].push({ district: '' });
            this.showSuccess(
                `${group.agentIds.length} participant(s) restant(s). ` +
                `Un slot de remplacement est disponible dans la zone ${groupIndex + 1}.`
            );
        }

        this.nouvelleActivite.agentIds = (this.nouvelleActivite.agentIds || [])
            .filter(id => id !== agentId);

        if (this.activiteEnAttente) {
            this.activiteEnAttente.agentIds = (this.activiteEnAttente.agentIds || [])
                .filter(id => id !== agentId);
        }

        this.conflitsDetectes = this.conflitsDetectes.filter(c => c.agentId !== agentId);
        this.agentsForces.delete(agentId);
    }

    supprimerSlot(groupIndex: number, slotIndex: number): void {
        if (this.slotsARemplacer[groupIndex]) {
            this.slotsARemplacer[groupIndex].splice(slotIndex, 1);
            if (this.slotsARemplacer[groupIndex].length === 0) {
                delete this.slotsARemplacer[groupIndex];
            }
        }
    }

    ouvrirRemplacementPourSlot(groupIndex: number, slotIndex: number): void {
        this.remplacementEnCours = { groupIndex, slotIndex };
        this.showRemplacementModal = true;
        this.isLoadingRemplacants = true;
        this.remplacantsDisponibles = [];

        this.planningService.trouverRemplacants(
            this.nouvelleActivite.dateDebut || '',
            this.nouvelleActivite.dateFin || ''
        ).subscribe({
            next: (agents) => {
                this.remplacantsDisponibles = agents.filter(a =>
                    !this.isAgentInAnyGroup(a.id!)
                );
                this.isLoadingRemplacants = false;
            },
            error: (err) => {
                console.error('Erreur chargement remplaçants', err);
                this.isLoadingRemplacants = false;
            }
        });
    }

    confirmerRemplacement(nouvelAgent: Agent): void {
        if (!this.remplacementEnCours || !nouvelAgent.id) return;

        const { groupIndex, slotIndex } = this.remplacementEnCours;

        const group = this.zoneGroups[groupIndex];
        if (group) {
            group.agentIds.push(nouvelAgent.id);
        }

        if (this.slotsARemplacer[groupIndex]) {
            this.slotsARemplacer[groupIndex].splice(slotIndex, 1);
            if (this.slotsARemplacer[groupIndex].length === 0) {
                delete this.slotsARemplacer[groupIndex];
            }
        }

        this.showSuccess(`${nouvelAgent.prenom} ${nouvelAgent.nom} ajouté(e) à la zone ${groupIndex + 1}`);

        this.showRemplacementModal = false;
        this.remplacementEnCours = null;
        this.remplacantsDisponibles = [];
    }

    closeRemplacementModal(): void {
        this.showRemplacementModal = false;
        this.remplacementEnCours = null;
        this.remplacantsDisponibles = [];
    }

    confirmerAvecForcage(): void {
        if (!this.activiteEnAttente) return;

        const agentIdsActuels = this.buildAllAgentIds();
        const agentZones = this.buildAgentZones();

        const activiteForcee: Activite = {
            ...this.activiteEnAttente,
            agentIds: agentIdsActuels,
            agentIdsForces: Array.from(this.agentsForces),
            agentZones: agentZones
        };

        this.isLoading = true;

        if (this.editingActivite && this.editingActivite.id) {
            this.planningService.updateActivite(this.editingActivite.id, activiteForcee).subscribe({
                next: (reponse) => {
                    if (reponse.succes) {
                        this.showSuccess('Activité modifiée malgré les conflits');
                        this.closeConflitModal();
                        this.closeForm();
                        this.loadPlanning();
                    } else {
                        this.conflitsDetectes = reponse.conflits;
                        this.showError(reponse.message);
                    }
                    this.isLoading = false;
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur modification');
                    this.isLoading = false;
                }
            });
        } else {
            this.planningService.creerActivite(activiteForcee).subscribe({
                next: (reponse) => {
                    if (reponse.succes) {
                        const dateDebut = this.parseLocalDate(reponse.activite?.dateDebut);
                        if (dateDebut) {
                            this.mois = dateDebut.getMonth() + 1;
                            this.annee = dateDebut.getFullYear();
                        }
                        this.showSuccess('Activité créée malgré les conflits');
                        this.closeConflitModal();
                        this.closeForm();
                        this.loadPlanning();
                    } else {
                        this.conflitsDetectes = reponse.conflits;
                        this.showError(reponse.message);
                    }
                    this.isLoading = false;
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur création');
                    this.isLoading = false;
                }
            });
        }
    }

    // ========== STATUT ==========
    changerStatutDirect(activite: Activite, nouveauStatut: string): void {
        if (!activite.id) return;
        if (activite.statut === nouveauStatut) return;

        if (activite.statut === 'BROUILLON' && nouveauStatut === 'PLANIFIEE') {
            const erreurs: string[] = [];
            if (!activite.dateDebut) erreurs.push('la date de début');
            if (!activite.dateFin) erreurs.push('la date de fin');
            if (!activite.lieu) erreurs.push('le lieu');
            if (!activite.agentIds || activite.agentIds.length === 0) {
                erreurs.push('au moins un participant');
            }

            if (erreurs.length > 0) {
                this.showError(
                    `Impossible de planifier : veuillez renseigner ${erreurs.join(', ')} via le formulaire de modification.`
                );
                if (confirm('Ouvrir le formulaire pour compléter l\'activité ?')) {
                    this.editActivite(activite);
                }
                this.loadPlanning();
                return;
            }
        }

        if (activite.statut === 'BROUILLON' &&
            (nouveauStatut === 'EN_COURS' || nouveauStatut === 'TERMINEE')) {
            this.showError(
                'Une activité en "Brouillon" doit d\'abord être "Planifiée" avant de changer d\'état.'
            );
            this.loadPlanning();
            return;
        }

        if (nouveauStatut === 'BROUILLON' && activite.statut !== 'BROUILLON') {
            if (!confirm(
                'Repasser en "Brouillon" supprimera tous les participants associés. Continuer ?'
            )) {
                this.loadPlanning();
                return;
            }
        }

        const label = this.getStatutLabel(nouveauStatut);
        if (!confirm(`Changer le statut en "${label}" ?`)) {
            this.loadPlanning();
            return;
        }

        this.isLoading = true;
        this.planningService.changerStatut(activite.id, nouveauStatut).subscribe({
            next: () => {
                this.showSuccess(`Statut changé en "${label}"`);
                this.loadPlanning();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur changement statut');
                this.loadPlanning();
                this.isLoading = false;
            }
        });
    }

    // ========== RECHERCHE AGENTS ==========
    getFilteredAgentsForGroup(groupIndex: number): Agent[] {
        const term = (this.searchAgentByGroup[groupIndex] || '').toLowerCase().trim();
        if (!term) return this.agents;
        return this.agents.filter(a =>
            a.nom?.toLowerCase().includes(term) ||
            a.prenom?.toLowerCase().includes(term) ||
            a.poste?.toLowerCase().includes(term)
        );
    }

    // ========== ACTIONS ==========
    viewActivite(activite: Activite): void {
        this.selectedActivite = activite;
        this.showDetailModal = true;
    }

    closeDetailModal(): void {
        this.showDetailModal = false;
        this.selectedActivite = null;
    }

    deleteActivite(activite: Activite): void {
        if (!activite.id) return;
        if (confirm(`Supprimer l'activité "${activite.titre}" ?`)) {
            this.planningService.supprimerActivite(activite.id).subscribe({
                next: () => {
                    this.showSuccess('Activité supprimée');
                    this.loadPlanning();
                },
                error: () => this.showError('Erreur suppression')
            });
        }
    }

    // ========== EXPORT ==========
    exportToExcel(): void {
        const dataToExport = this.filteredActivites.length > 0 ? this.filteredActivites : this.activites;
        const columns = [
            { key: 'titre', label: 'Titre' },
            { key: 'dateDebut', label: 'Date début' },
            { key: 'dateFin', label: 'Date fin' },
            { key: 'nombreJours', label: 'Jours' },
            { key: 'lieu', label: 'Lieu' },
            { key: 'sourceFinancement', label: 'Financement' },
            { key: 'statut', label: 'Statut' },
            { key: 'agentNoms', label: 'Participants' }
        ];

        const exportData = dataToExport.map(a => ({
            ...a,
            dateDebut: a.dateDebut || '-',
            dateFin: a.dateFin || '-',
            lieu: a.lieu || '-',
            agentNoms: a.agentNoms?.join(', ') || '-',
            statut: this.getStatutLabel(a.statut)
        }));

        const fileName = this.hasActiveFilters() ? 'activites_filtrees' : 'activites_complet';
        const title = 'PNLP - LISTE DES ACTIVITÉS';
        const subtitle = this.hasActiveFilters()
            ? `Filtres appliqués - ${this.getMoisNom(this.mois)} ${this.annee}`
            : `Liste complète - ${this.getMoisNom(this.mois)} ${this.annee}`;

        this.exportService.exportToExcel(exportData, columns, fileName, 'Activités', title, subtitle);
        this.showSuccess('Export Excel réussi');
    }

    exportToPdf(): void {
        const dataToExport = this.filteredActivites.length > 0 ? this.filteredActivites : this.activites;
        const columns = [
            { key: 'titre', label: 'Titre' },
            { key: 'dateDebut', label: 'Début' },
            { key: 'dateFin', label: 'Fin' },
            { key: 'nombreJours', label: 'Jours' },
            { key: 'lieu', label: 'Lieu' },
            { key: 'statut', label: 'Statut' },
            { key: 'agentNoms', label: 'Participants' }
        ];

        const exportData = dataToExport.map(a => ({
            ...a,
            dateDebut: a.dateDebut || '-',
            dateFin: a.dateFin || '-',
            lieu: a.lieu || '-',
            agentNoms: a.agentNoms?.join(', ') || '-',
            statut: this.getStatutLabel(a.statut)
        }));

        const fileName = this.hasActiveFilters() ? 'activites_filtrees' : 'activites_complet';
        const title = 'PNLP - LISTE DES ACTIVITÉS';
        const subtitle = `${this.getMoisNom(this.mois)} ${this.annee}`;

        this.exportService.exportToPdf(exportData, columns, fileName, title, subtitle);
        this.showSuccess('Export PDF réussi');
    }

    // ========== UTILITAIRES ==========
    getStatutLabel(statut?: string): string {
        const labels: { [key: string]: string } = {
            'BROUILLON': 'Brouillon',
            'PLANIFIEE': 'Planifiée',
            'EN_COURS': 'En cours',
            'TERMINEE': 'Terminée',
            'ANNULEE': 'Annulée',
            'REPORTEE': 'Reportée'
        };
        return labels[statut || 'PLANIFIEE'] || statut || '-';
    }

    getStatutClass(statut?: string): string {
        const classes: { [key: string]: string } = {
            'BROUILLON': 'badge-brouillon',
            'PLANIFIEE': 'badge-planifiee',
            'EN_COURS': 'badge-en-cours',
            'TERMINEE': 'badge-terminee',
            'ANNULEE': 'badge-annulee',
            'REPORTEE': 'badge-reportee'
        };
        return classes[statut || 'PLANIFIEE'] || 'badge-planifiee';
    }

    getMoisNom(mois: number): string {
        const moisNoms = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
            'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
        return moisNoms[mois - 1];
    }

    changerMois(delta: number): void {
        this.mois += delta;
        if (this.mois > 12) {
            this.mois = 1;
            this.annee++;
        } else if (this.mois < 1) {
            this.mois = 12;
            this.annee--;
        }
        this.loadPlanning();
    }

    showSuccess(message: string): void {
        this.successMessage = message;
        setTimeout(() => this.successMessage = '', 4000);
    }

    showError(message: string): void {
        this.errorMessage = message;
        setTimeout(() => this.errorMessage = '', 5000);
    }

    getActionsForActivite(activite: Activite): ActionButton[] {
        const modifiable =
            activite.statut === 'PLANIFIEE' ||
            activite.statut === 'BROUILLON';

        return [
            {
                id: 'view',
                icon: 'pi pi-eye',
                label: 'Voir détails',
                severity: 'info'
            },
            {
                id: 'edit',
                icon: 'pi pi-pencil',
                label: 'Modifier',
                severity: 'warning',
                show: modifiable
            },
            {
                id: 'delete',
                icon: 'pi pi-trash',
                label: 'Supprimer',
                severity: 'danger',
                show: modifiable
            }
        ];
    }

    onActionActivite(actionId: string, activite: Activite): void {
        switch (actionId) {
            case 'view': this.viewActivite(activite); break;
            case 'edit': this.editActivite(activite); break;
            case 'delete': this.deleteActivite(activite); break;
        }
    }
}