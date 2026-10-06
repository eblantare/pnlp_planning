import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
    PlanningService,
    Activite,
    PlanningMensuel,
    ConflitAgent,
    ValidationConflit,
    DecisionValidation
} from '../../core/services/planning.service';
import { AgentService, Agent } from '../../core/services/agent.service';
import { ExportService } from '../../core/services/export.service';
import { ActionButton, ActionButtonsComponent } from '../../shared/components/action-buttons/action-buttons.component';
import { concatMap, Observable, of } from "rxjs";
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PermissionService } from '../../core/services/permission.service';
import { RefreshService } from '../../core/services/refresh.service';

interface ZoneGroup {
    id: string;
    region: string;
    districts: Set<string>;
    agentIds: string[];
}

interface ConflitEditable {
    affectationId: string;
    agentId: string;
    agentNom: string;
    agentPrenom: string;
    agentPoste?: string;
    typeConflit: string;
    activiteConflit?: string;
    dateDebut?: string;
    dateFin?: string;
    motif?: string;
    action: 'EN_ATTENTE' | 'RETIRER' | 'REMPLACER' | 'FORCER';
    agentRemplacantId?: string;
    agentRemplacantNom?: string;
}

@Component({
    selector: 'app-planning',
    standalone: true,
    imports: [CommonModule, FormsModule, ActionButtonsComponent, HasPermissionDirective],
    templateUrl: './planning.component.html',
    styleUrls: ['./planning.component.css'],
    encapsulation: ViewEncapsulation.None
})
export class PlanningComponent implements OnInit {
    activites: Activite[] = [];
    filteredActivites: Activite[] = [];
    paginatedActivites: Activite[] = [];
    agents: Agent[] = [];
    filteredAgents: Agent[] = [];
    planningMensuel: PlanningMensuel | null = null;

    annee = new Date().getFullYear();
    mois = new Date().getMonth() + 1;

    searchTerm = '';
    filterStatut = '';
    searchAgent = '';

    currentPage = 1;
    itemsPerPage = 10;
    totalItems = 0;
    pageSizeOptions = [5, 10, 25, 50];

    nouvelleActivite: Activite = this.getEmptyActivite();
    editingActivite: Activite | null = null;

    tdrFile: File | null = null;
    tdrConforme = false;

    ordreMissionFile: File | null = null;
    ordreMissionConforme = false;

    lettreInvitationFile: File | null = null;
    lettreInvitationConforme = false;

    tdrExistant: { filename: string; uploadDate: string; conforme: boolean } | null = null;
    ordreMissionExistant: { filename: string; uploadDate: string; conforme: boolean } | null = null;
    lettreInvitationExistante: { filename: string; uploadDate: string; conforme: boolean } | null = null;

    zoneGroups: ZoneGroup[] = [];
    private zoneGroupCounter = 0;

    regions: { [key: string]: string[] } = {
        'Grand Lomé': ['Golfe', 'Agoènyivé'],
        'Maritime': ['Avé', 'Bas-Mono', 'Lacs', 'Vo', 'Yoto', 'Zio'],
        'Plateaux': ['Agou', 'Akébou', 'Amou', 'Anié', 'Danyi', 'Est-Mono', 'Haho', 'Kloto', 'Kpélé', 'Moyen-Mono', 'Ogou', 'Wawa'],
        'Centrale': ['Blitta', 'Mô', 'Sotouboua', 'Tchamba', 'Tchoudjo'],
        'Kara': ['Bassar', 'Binah', 'Dankpen', 'Doufelgou', 'Guérin-Kouka', 'Kéran', 'Kozah'],
        'Savanes': ['Cinkancé', 'Kpendjal', 'Kpendjal-Ouest', 'Oti', 'Oti-Sud', 'Tandjouaré', 'Tône']
    };

    showForm = false;
    showDetailModal = false;
    showConflitModal = false;
    showRemplacementModal = false;
    selectedActivite: Activite | null = null;

    conflitsDetectes: ConflitAgent[] = [];
    activiteEnAttente: Activite | null = null;

    // ============================================================
    // ✅ NOUVEAU : Modal de traitement des conflits par le validateur
    // ============================================================
    showValidationModal = false;
    activiteEnValidation: Activite | null = null;
    conflitsAValider: ConflitEditable[] = [];
    commentaireValidation = '';
    isLoadingValidation = false;

    // ✅ Remplaçants dans le modal de validation
    remplacantsValidation: Agent[] = [];
    isLoadingRemplacantsValidation = false;
    indexRemplacantValidationEnCours: number | null = null;

    // Modal remplacement (formulaire planificateur)
    slotsARemplacer: { [groupIndex: number]: { district: string }[] } = {};
    remplacementEnCours: { groupIndex: number; slotIndex: number } | null = null;
    remplacantsDisponibles: Agent[] = [];
    isLoadingRemplacants = false;

    searchAgentByGroup: { [groupIndex: number]: string } = {};

    isLoading = false;
    errorMessage = '';
    successMessage = '';

    Math = Math;

    constructor(
        private planningService: PlanningService,
        private agentService: AgentService,
        private exportService: ExportService,
        public permissionService: PermissionService,
        private refreshService: RefreshService
    ) { }

    ngOnInit(): void {
        this.loadAgents();
        this.loadPlanning();
    }

    getEmptyActivite(): Activite {
        return {
            titre: '',
            commentaires: '',
            sourceFinancement: '',
            typeLieu: 'NON_RESIDENT',
            auProgramme: false,
            statut: 'BROUILLON',
            agentIds: []
        };
    }

    isAuProgramme(): boolean {
        return this.nouvelleActivite.auProgramme === true;
    }

    onAuProgrammeChange(value: boolean): void {
        this.nouvelleActivite.auProgramme = value;
        if (value) {
            this.nouvelleActivite.lieu = undefined;
            this.nouvelleActivite.sourceFinancement = '';
            this.nouvelleActivite.typeLieu = undefined;
        }
    }

    isBrouillon(): boolean {
        return this.nouvelleActivite.statut === 'BROUILLON';
    }

    isChampsRequis(): boolean {
        return !this.isBrouillon();
    }

    estCloture(statut?: string): boolean {
        return statut === 'TERMINEE' || statut === 'ANNULEE' || statut === 'REPORTEE';
    }

    estEnAttenteValidation(statut?: string): boolean {
        return statut === 'EN_ATTENTE_VALIDATION' || statut === 'RENVOYE_POUR_CORRECTION';
    }

    // ============================================================
    // ✅ NOUVEAU : Vérifie si l'utilisateur peut traiter les conflits
    //             d'une activité donnée (validateur du bon niveau ou SUPER_ADMIN)
    // ============================================================
    peutTraiterConflits(activite: Activite): boolean {
        if (!activite || activite.statut !== 'EN_ATTENTE_VALIDATION') return false;
        const niveauUtilisateur = this.permissionService.getNiveauValidation();
        if (niveauUtilisateur === null) return false;
        if (niveauUtilisateur === -1) return true;
        return niveauUtilisateur === (activite.niveauValidationActuel || 1);
    }

    toggleAuProgramme(activite: Activite, event: any): void {
        if (!activite.id) return;

        const nouvelleValeur = event.target.checked;

        const updated: Activite = {
            ...activite,
            auProgramme: nouvelleValeur
        };

        this.planningService.updateActivite(activite.id, updated).subscribe({
            next: (reponse) => {
                if (reponse.succes) {
                    activite.auProgramme = nouvelleValeur;
                    this.showSuccess(nouvelleValeur
                        ? '✅ Activité marquée "au programme"'
                        : '↩️ Activité retirée du programme');
                    this.refreshService.demanderRafraichissement();
                } else {
                    event.target.checked = !nouvelleValeur;
                    this.showError(reponse.message || 'Impossible de modifier le statut programme');
                }
            },
            error: (err) => {
                event.target.checked = !nouvelleValeur;
                this.showError(err.error?.message || 'Erreur modification');
            }
        });
    }

    private parseLocalDate(dateStr: string | undefined | null): Date | null {
        if (!dateStr) return null;
        if (dateStr.includes('T')) {
            return new Date(dateStr);
        }
        const parts = dateStr.split('-').map(Number);
        if (parts.length !== 3 || parts.some(isNaN)) return null;
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }

    private estActif(agent: Agent): boolean {
        const v: any = (agent as any).actif;
        return v === true || v === 'true';
    }

    loadAgents(): void {
        this.agentService.getAllAgents().subscribe({
            next: (data) => {
                this.agents = data.filter(a => this.estActif(a));
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

    trierActivites(activites: Activite[]): Activite[] {
        const ordreStatut: { [key: string]: number } = {
            'BROUILLON': 1,
            'RENVOYE_POUR_CORRECTION': 2,
            'EN_ATTENTE_VALIDATION': 3,
            'PLANIFIEE': 4,
            'EN_COURS': 5,
            'REPORTEE': 6,
            'TERMINEE': 7,
            'ANNULEE': 8
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

    getAgentsOccupes(): number {
        const agentsOccupes = new Set<string>();
        this.activites
            .filter(a => a.statut === 'EN_COURS' && !a.auProgramme)
            .forEach(a => a.agentIds?.forEach(id => agentsOccupes.add(id)));
        return agentsOccupes.size;
    }

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

    openCreateForm(): void {
        this.loadAgents();

        this.tdrFile = null;
        this.tdrConforme = false;
        this.ordreMissionFile = null;
        this.ordreMissionConforme = false;
        this.lettreInvitationFile = null;
        this.lettreInvitationConforme = false;
        this.tdrExistant = null;
        this.ordreMissionExistant = null;
        this.lettreInvitationExistante = null;

        this.editingActivite = null;
        this.nouvelleActivite = this.getEmptyActivite();
        this.zoneGroups = [];
        this.slotsARemplacer = {};
        this.searchAgentByGroup = {};
        this.ajouterGroupe();
        this.showForm = true;
    }

    editActivite(activite: Activite, statutCible?: string): void {
        this.agentService.getAllAgents().subscribe({
            next: (data) => {
                this.agents = data.filter(a => this.estActif(a));
                this.filteredAgents = this.agents;
                this._doEditActivite(activite, statutCible);
            },
            error: (err) => {
                console.error('Erreur rechargement agents (edit)', err);
                this._doEditActivite(activite, statutCible);
            }
        });
    }

    private _doEditActivite(activite: Activite, statutCible?: string): void {
        this.tdrFile = null;
        this.tdrConforme = activite.tdrConforme || false;
        this.tdrExistant = activite.tdrFilename ? {
            filename: activite.tdrFilename,
            uploadDate: activite.tdrUploadedAt || '',
            conforme: activite.tdrConforme || false
        } : null;

        this.ordreMissionFile = null;
        this.ordreMissionConforme = activite.ordreMissionConforme || false;
        this.ordreMissionExistant = activite.ordreMissionFilename ? {
            filename: activite.ordreMissionFilename,
            uploadDate: activite.ordreMissionUploadedAt || '',
            conforme: activite.ordreMissionConforme || false
        } : null;

        this.lettreInvitationFile = null;
        this.lettreInvitationConforme = activite.lettreInvitationConforme || false;
        this.lettreInvitationExistante = activite.lettreInvitationFilename ? {
            filename: activite.lettreInvitationFilename,
            uploadDate: activite.lettreInvitationUploadedAt || '',
            conforme: activite.lettreInvitationConforme || false
        } : null;

        this.editingActivite = activite;
        this.nouvelleActivite = {
            ...activite,
            agentIds: activite.agentIds ? [...activite.agentIds] : []
        };

        if (statutCible) {
            this.nouvelleActivite.statut = statutCible;
        }

        this.slotsARemplacer = {};
        this.searchAgentByGroup = {};
        this.zoneGroups = [];

        if (activite.agentIds && activite.agentIds.length > 0) {
            const zonesMap = new Map<string, string[]>();
            const activeAgentIds = new Set(this.agents.map(a => a.id));
            activite.agentIds.forEach((id, index) => {
                if (!activeAgentIds.has(id)) return;
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
        this.tdrFile = null;
        this.tdrConforme = false;
        this.ordreMissionFile = null;
        this.ordreMissionConforme = false;
        this.lettreInvitationFile = null;
        this.lettreInvitationConforme = false;
        this.tdrExistant = null;
        this.ordreMissionExistant = null;
        this.lettreInvitationExistante = null;

        this.showForm = false;
        this.editingActivite = null;
        this.nouvelleActivite = this.getEmptyActivite();
        this.zoneGroups = [];
        this.slotsARemplacer = {};
        this.searchAgentByGroup = {};
    }

    calculerNombreJours(): void {
        const debut = this.parseLocalDate(this.nouvelleActivite.dateDebut);
        const fin = this.parseLocalDate(this.nouvelleActivite.dateFin);

        if (debut && fin) {
            let diff = Math.ceil((fin.getTime() - debut.getTime()) / (1000 * 60 * 60 * 24)) + 1;

            if (!this.isAuProgramme() && this.nouvelleActivite.typeLieu === 'NON_RESIDENT') {
                diff = diff + 1;
            }

            this.nouvelleActivite.nombreJours = diff > 0 ? diff : 0;
        } else {
            this.nouvelleActivite.nombreJours = undefined;
        }
    }

    onTypeLieuChange(newTypeLieu: string): void {
        this.nouvelleActivite.typeLieu = newTypeLieu;
        this.calculerNombreJours();
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
            if (!this.isAuProgramme() && !this.nouvelleActivite.lieu) erreurs.push('le lieu');

            if (erreurs.length > 0) {
                this.showError(`Veuillez renseigner ${erreurs.join(', ')} avant de planifier.`);
            }
        }
    }

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

    creerActivite(): void {
        if (!this.nouvelleActivite.titre) {
            this.showError('Le titre est obligatoire');
            return;
        }

        const isBrouillon = this.isBrouillon();
        const statut = this.nouvelleActivite.statut;
        const estAuProgramme = this.isAuProgramme();

        if (!isBrouillon) {
            const erreurs: string[] = [];
            if (!this.nouvelleActivite.dateDebut) erreurs.push('la date de début');
            if (!this.nouvelleActivite.dateFin) erreurs.push('la date de fin');

            if (this.getTotalSelectedAgents() === 0) {
                erreurs.push('au moins un participant');
            }

            if (!estAuProgramme) {
                if (!this.nouvelleActivite.lieu) erreurs.push('le lieu');
                if (!this.nouvelleActivite.sourceFinancement) erreurs.push('la source de financement');

                if (statut === 'PLANIFIEE') {
                    if (!this.tdrFile && !this.tdrExistant) {
                        erreurs.push('le TDR (Termes de Référence)');
                    } else if (!this.tdrConforme) {
                        erreurs.push('la confirmation "conforme à l\'original" du TDR');
                    }
                } else if (statut === 'EN_COURS') {
                    if (!this.ordreMissionFile && !this.ordreMissionExistant) {
                        erreurs.push('l\'Ordre de Mission');
                    } else if (!this.ordreMissionConforme) {
                        erreurs.push('la confirmation "conforme à l\'original" de l\'Ordre de Mission');
                    }
                }
            }

            if (erreurs.length > 0) {
                this.showError(`Veuillez renseigner ${erreurs.join(', ')}.`);
                return;
            }
        }

        const allAgentIds = isBrouillon ? [] : this.buildAllAgentIds();
        const agentZones = (isBrouillon || estAuProgramme) ? {} : this.buildAgentZones();

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
            const idActivite = this.editingActivite.id;
            this.planningService.updateActivite(idActivite, activiteFinale).subscribe({
                next: (reponse) => {
                    if (reponse.succes) {
                        if (this.estEnAttenteValidation(reponse.activite?.statut)) {
                            this.conflitsDetectes = reponse.conflits || [];
                            this.activiteEnAttente = reponse.activite || null;
                            this.closeForm();
                            this.showConflitModal = true;
                            this.isLoading = false;
                            return;
                        }
                        this.uploaderFichiersEtFinaliser(idActivite, 'Activité modifiée');
                    } else {
                        this.conflitsDetectes = reponse.conflits;
                        this.activiteEnAttente = { ...activiteFinale };
                        this.showConflitModal = true;
                        this.isLoading = false;
                    }
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur modification');
                    this.isLoading = false;
                }
            });
        } else {
            this.planningService.creerActivite(activiteFinale).subscribe({
                next: (reponse) => {
                    if (reponse.succes && reponse.activite?.id) {
                        const dateDebut = this.parseLocalDate(reponse.activite.dateDebut);
                        if (dateDebut) {
                            this.mois = dateDebut.getMonth() + 1;
                            this.annee = dateDebut.getFullYear();
                        }

                        if (this.estEnAttenteValidation(reponse.activite?.statut)) {
                            this.conflitsDetectes = reponse.conflits || [];
                            this.activiteEnAttente = reponse.activite || null;
                            this.closeForm();
                            this.showConflitModal = true;
                            this.isLoading = false;
                            return;
                        }

                        this.uploaderFichiersEtFinaliser(reponse.activite.id, 'Activité créée');
                    } else if (reponse.succes) {
                        this.showSuccess('Activité créée');
                        this.closeForm();
                        this.loadPlanning();
                        this.refreshService.demanderRafraichissement();
                        this.isLoading = false;
                    } else {
                        this.conflitsDetectes = reponse.conflits;
                        this.activiteEnAttente = { ...activiteFinale };
                        this.showConflitModal = true;
                        this.isLoading = false;
                    }
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur création');
                    this.isLoading = false;
                }
            });
        }
    }

    private uploaderFichiersEtFinaliser(activiteId: string, messageSucces: string): void {
        if (this.isAuProgramme()) {
            this.showSuccess(messageSucces);
            this.closeForm();
            this.loadPlanning();
            this.refreshService.demanderRafraichissement();
            this.isLoading = false;
            return;
        }

        const uploads: Observable<any>[] = [];

        if (this.tdrFile) {
            uploads.push(this.planningService.uploadTdr(
                activiteId, this.tdrFile, this.tdrConforme));
        }
        if (this.ordreMissionFile) {
            uploads.push(this.planningService.uploadOrdreMission(
                activiteId, this.ordreMissionFile, this.ordreMissionConforme));
        }
        if (this.lettreInvitationFile) {
            uploads.push(this.planningService.uploadLettreInvitation(
                activiteId, this.lettreInvitationFile, this.lettreInvitationConforme));
        }

        if (uploads.length === 0) {
            this.showSuccess(messageSucces);
            this.closeForm();
            this.loadPlanning();
            this.refreshService.demanderRafraichissement();
            this.isLoading = false;
            return;
        }

        const sequential$ = uploads.reduce(
            (acc, task) => acc.pipe(concatMap(() => task)),
            of(null) as Observable<any>
        );

        sequential$.subscribe({
            next: () => {
                this.showSuccess(messageSucces);
                this.closeForm();
                this.loadPlanning();
                this.refreshService.demanderRafraichissement();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError('Erreur upload fichier: ' + (err.error?.message || err.message));
                this.isLoading = false;
            }
        });
    }

    closeConflitModal(): void {
        this.showConflitModal = false;
        this.conflitsDetectes = [];
        this.activiteEnAttente = null;
    }

    confirmerSoumissionValidation(): void {
        this.showSuccess('✅ Activité soumise à validation. Vous serez notifié dès qu\'elle sera traitée.');
        this.closeConflitModal();
        this.loadPlanning();
        this.refreshService.demanderRafraichissement();
    }

    annulerSoumissionValidation(): void {
        if (!this.activiteEnAttente?.id) return;
        if (!confirm(
            'Annuler la soumission ? L\'activité repassera en Brouillon et les participants seront retirés.'
        )) return;

        this.isLoading = true;
        this.planningService.changerStatut(this.activiteEnAttente.id, 'BROUILLON').subscribe({
            next: () => {
                this.showSuccess('↩️ Soumission annulée. Activité repassée en Brouillon.');
                this.closeConflitModal();
                this.loadPlanning();
                this.refreshService.demanderRafraichissement();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors de l\'annulation');
                this.isLoading = false;
            }
        });
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
                    this.estActif(a) && !this.isAgentInAnyGroup(a.id!)
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

    changerStatutDirect(activite: Activite, nouveauStatut: string): void {
        if (!activite.id) return;
        if (activite.statut === nouveauStatut) return;

        if (this.estEnAttenteValidation(activite.statut)) {
            this.showError(
                'Cette activité est en attente de validation. Seul un validateur peut la traiter.'
            );
            this.loadPlanning();
            return;
        }

        if (activite.statut === 'PLANIFIEE' && nouveauStatut === 'EN_COURS') {
            const erreurs: string[] = [];

            if (!activite.auProgramme) {
                if (!activite.ordreMissionFilename) {
                    erreurs.push('l\'Ordre de Mission');
                } else if (!activite.ordreMissionConforme) {
                    erreurs.push('la confirmation "conforme à l\'original" de l\'Ordre de Mission');
                }
            }

            if (erreurs.length > 0) {
                this.showError(
                    `Impossible de démarrer : veuillez renseigner ${erreurs.join(', ')} via le formulaire de modification.`
                );
                if (confirm('Ouvrir le formulaire pour ajouter l\'Ordre de Mission ?')) {
                    this.editActivite(activite, 'EN_COURS');
                }
                this.loadPlanning();
                return;
            }
        }

        if (activite.statut === 'BROUILLON' && nouveauStatut === 'PLANIFIEE') {
            const erreurs: string[] = [];

            if (!activite.dateDebut) erreurs.push('la date de début');
            if (!activite.dateFin) erreurs.push('la date de fin');

            if (!activite.agentIds || activite.agentIds.length === 0) {
                erreurs.push('au moins un participant');
            }

            if (!activite.auProgramme) {
                if (!activite.lieu) erreurs.push('le lieu');
                if (!activite.sourceFinancement) erreurs.push('la source de financement');

                if (!activite.tdrFilename) {
                    erreurs.push('le TDR');
                } else if (!activite.tdrConforme) {
                    erreurs.push('la confirmation "conforme à l\'original" du TDR');
                }
            }

            if (erreurs.length > 0) {
                this.showError(
                    `Impossible de planifier : veuillez renseigner ${erreurs.join(', ')} via le formulaire de modification.`
                );
                if (confirm('Ouvrir le formulaire pour compléter l\'activité ?')) {
                    this.editActivite(activite, 'PLANIFIEE');
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
                this.refreshService.demanderRafraichissement();
                this.isLoading = false;
            },
            error: (err) => {
                const msg = err.error?.message || err.error?.error || 'Erreur changement statut';
                this.showError(msg);
                this.loadPlanning();
                this.isLoading = false;
            }
        });
    }

    getFilteredAgentsForGroup(groupIndex: number): Agent[] {
        const term = (this.searchAgentByGroup[groupIndex] || '').toLowerCase().trim();

        const actifs = this.agents.filter(a => this.estActif(a));

        if (!term) return actifs;
        return actifs.filter(a =>
            a.nom?.toLowerCase().includes(term) ||
            a.prenom?.toLowerCase().includes(term) ||
            a.poste?.toLowerCase().includes(term)
        );
    }

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
                    this.refreshService.demanderRafraichissement();
                },
                error: () => this.showError('Erreur suppression')
            });
        }
    }

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

    getStatutLabel(statut?: string): string {
        const labels: { [key: string]: string } = {
            'BROUILLON': 'Brouillon',
            'EN_ATTENTE_VALIDATION': 'En attente de validation',
            'RENVOYE_POUR_CORRECTION': 'Renvoyé pour correction',
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
            'EN_ATTENTE_VALIDATION': 'badge-attente-validation',
            'RENVOYE_POUR_CORRECTION': 'badge-renvoye-correction',
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
        const statut = activite.statut;
        const peutCrud = this.permissionService.peutCrudPlanning();

        const estCloture =
            statut === 'TERMINEE' || statut === 'ANNULEE' || statut === 'REPORTEE';

        const enValidation = this.estEnAttenteValidation(statut);

        const peutRenvoyerTdr =
            peutCrud && !activite.auProgramme && !estCloture && !enValidation &&
            statut !== 'BROUILLON' && !!activite.tdrFilename;

        const peutRenvoyerOm =
            peutCrud && !activite.auProgramme && statut === 'EN_COURS' &&
            !!activite.ordreMissionFilename;

        // ✅ CORRIGÉ : RENVOYE_POUR_CORRECTION doit être modifiable par le planificateur
        const modifiable =
            peutCrud && (
                statut === 'BROUILLON'
                || statut === 'PLANIFIEE'
                || statut === 'RENVOYE_POUR_CORRECTION'   // ✅ AJOUTÉ
            );

        const actions: ActionButton[] = [];

        // ✅ NOUVEAU : bouton "Traiter les conflits" pour les validateurs du bon niveau
        if (this.peutTraiterConflits(activite)) {
            actions.push({
                id: 'traiter-conflits',
                icon: 'pi pi-exclamation-triangle',
                label: 'Traiter les conflits',
                title: `${activite.conflits?.length || 0} conflit(s) à traiter - cliquez pour ouvrir`,
                severity: 'warning'
            });
        }

        actions.push({
            id: 'view',
            icon: 'pi pi-eye',
            label: 'Voir détails',
            title: 'Voir les détails de l\'activité',
            severity: 'info'
        });

        if (peutRenvoyerTdr) {
            actions.push({
                id: 'renvoyer-tdr',
                icon: 'pi pi-envelope',
                label: 'Renvoyer le TDR',
                title: 'Renvoyer le TDR aux participants',
                severity: 'success'
            });
        }

        if (peutRenvoyerOm) {
            actions.push({
                id: 'renvoyer-om',
                icon: 'pi pi-send',
                label: 'Renvoyer l\'Ordre de mission',
                title: 'Renvoyer l\'Ordre de mission / lettre d\'invitation',
                severity: 'success'
            });
        }

        actions.push(
            {
                id: 'edit',
                icon: 'pi pi-pencil',
                label: 'Modifier',
                title: 'Modifier l\'activité',
                severity: 'warning',
                show: modifiable
            },
            {
                id: 'delete',
                icon: 'pi pi-trash',
                label: 'Supprimer',
                title: 'Supprimer l\'activité',
                severity: 'danger',
                show: modifiable
            }
        );

        return actions;
    }

    onActionActivite(actionId: string, activite: Activite): void {
        switch (actionId) {
            case 'view': this.viewActivite(activite); break;
            case 'edit': this.editActivite(activite); break;
            case 'delete': this.deleteActivite(activite); break;
            case 'renvoyer-tdr': this.renvoyerTdr(activite); break;
            case 'renvoyer-om': this.renvoyerOrdreMission(activite); break;
            case 'traiter-conflits': this.ouvrirValidation(activite); break;   // ✅ NOUVEAU
        }
    }

    // ============================================================
    // ✅ NOUVEAU : WORKFLOW DE VALIDATION (modal intégré)
    // ============================================================

    ouvrirValidation(activite: Activite): void {
        this.activiteEnValidation = activite;
        this.commentaireValidation = '';
        this.conflitsAValider = (activite.conflits || []).map(c => ({
            affectationId: c.affectationId!,
            agentId: c.agentId,
            agentNom: c.agentNom,
            agentPrenom: c.agentPrenom,
            agentPoste: c.agentPoste,
            typeConflit: c.typeConflit,
            activiteConflit: c.activiteConflit,
            dateDebut: c.dateDebut,
            dateFin: c.dateFin,
            motif: c.motif,
            action: (c.actionValidation as any) || 'EN_ATTENTE',
            agentRemplacantId: c.agentRemplacantId,
            agentRemplacantNom: c.agentRemplacantNom
        }));
        this.showValidationModal = true;
    }

    fermerValidationModal(): void {
        this.showValidationModal = false;
        this.activiteEnValidation = null;
        this.conflitsAValider = [];
        this.commentaireValidation = '';
        this.indexRemplacantValidationEnCours = null;
        this.remplacantsValidation = [];
    }

    /**
     * ✅ MODIFIÉ : clic sur une action déjà sélectionnée → désélectionne
     * (revient à EN_ATTENTE).
     */
    setActionValidation(index: number, action: 'RETIRER' | 'REMPLACER' | 'FORCER'): void {
        const c = this.conflitsAValider[index];
        if (!c) return;

        // ✅ Si on clique sur l'action déjà active → on annule la sélection
        if (c.action === action) {
            c.action = 'EN_ATTENTE';
            c.agentRemplacantId = undefined;
            c.agentRemplacantNom = undefined;
            // Ferme la sélection de remplaçant si ouverte
            if (this.indexRemplacantValidationEnCours === index) {
                this.indexRemplacantValidationEnCours = null;
                this.remplacantsValidation = [];
            }
            return;
        }

        // Sinon, on applique la nouvelle action
        c.action = action;

        if (action !== 'REMPLACER') {
            c.agentRemplacantId = undefined;
            c.agentRemplacantNom = undefined;
            if (this.indexRemplacantValidationEnCours === index) {
                this.indexRemplacantValidationEnCours = null;
                this.remplacantsValidation = [];
            }
        } else {
            // Ouvre directement la sélection du remplaçant
            this.ouvrirSelectionRemplacant(index);
        }
    }

    ouvrirSelectionRemplacant(index: number): void {
        const c = this.conflitsAValider[index];
        if (!c) return;

        this.indexRemplacantValidationEnCours = index;
        this.isLoadingRemplacantsValidation = true;
        this.remplacantsValidation = [];

        const debut = this.activiteEnValidation?.dateDebut || '';
        const fin = this.activiteEnValidation?.dateFin || '';

        this.planningService.trouverRemplacants(debut, fin).subscribe({
            next: (agents) => {
                const idsDejaPresents = new Set(this.conflitsAValider.map(x => x.agentId));
                this.remplacantsValidation = agents.filter(a =>
                    a.id && !idsDejaPresents.has(a.id)
                );
                this.isLoadingRemplacantsValidation = false;
            },
            error: (err) => {
                console.error('Erreur chargement remplaçants validation', err);
                this.isLoadingRemplacantsValidation = false;
            }
        });
    }

    choisirRemplacantValidation(agent: Agent): void {
        if (this.indexRemplacantValidationEnCours === null) return;
        const c = this.conflitsAValider[this.indexRemplacantValidationEnCours];
        if (!c || !agent.id) return;

        c.agentRemplacantId = agent.id;
        c.agentRemplacantNom = `${agent.prenom} ${agent.nom}`;
        c.action = 'REMPLACER';
        this.indexRemplacantValidationEnCours = null;
        this.remplacantsValidation = [];
    }

    annulerSelectionRemplacantValidation(): void {
        this.indexRemplacantValidationEnCours = null;
        this.remplacantsValidation = [];
    }

    tousConflitsTraites(): boolean {
        if (this.conflitsAValider.length === 0) return true;
        return this.conflitsAValider.every(c =>
            c.action !== 'EN_ATTENTE'
            && (c.action !== 'REMPLACER' || !!c.agentRemplacantId)
        );
    }

    getNbConflitsRestants(): number {
        return this.conflitsAValider.filter(c =>
            c.action === 'EN_ATTENTE'
            || (c.action === 'REMPLACER' && !c.agentRemplacantId)
        ).length;
    }

    validerConflits(): void {
        if (!this.activiteEnValidation?.id) return;
        if (!this.tousConflitsTraites()) {   // ✅ Gardé pour Valider
            this.showError('Veuillez traiter tous les conflits avant de valider');
            return;
        }

        const actions: ValidationConflit[] = this.conflitsAValider.map(c => ({
            affectationId: c.affectationId,
            action: c.action as any,
            agentRemplacantId: c.agentRemplacantId
        }));

        const decision: DecisionValidation = {
            actions,
            decision: 'VALIDER'
        };

        this.isLoadingValidation = true;
        this.planningService.valider(this.activiteEnValidation.id, decision).subscribe({
            next: () => {
                this.showSuccess('✅ Activité validée avec succès');
                this.fermerValidationModal();
                this.loadPlanning();
                this.refreshService.demanderRafraichissement();
                this.isLoadingValidation = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors de la validation');
                this.isLoadingValidation = false;
            }
        });
    }

    renvoyerNiveauSuperieur(): void {
        if (!this.activiteEnValidation?.id) return;
        // ✅ MODIFIÉ : plus de vérification "tous conflits traités"
        // On peut renvoyer au niveau supérieur même si rien n'est décidé.
        if (!confirm('Renvoyer cette activité au niveau supérieur ?')) return;

        const actions: ValidationConflit[] = this.conflitsAValider
            .filter(c => c.action !== 'EN_ATTENTE')   // ✅ On n'envoie que les actions choisies
            .map(c => ({
                affectationId: c.affectationId,
                action: c.action as any,
                agentRemplacantId: c.agentRemplacantId
            }));

        const decision: DecisionValidation = {
            actions,
            decision: 'RENVOYER_NIVEAU_SUPERIEUR',
            commentaire: this.commentaireValidation
        };
        this.isLoadingValidation = true;
        this.planningService.valider(this.activiteEnValidation.id, decision).subscribe({
            next: () => {
                this.showSuccess('⬆️ Activité renvoyée au niveau supérieur');
                this.fermerValidationModal();
                this.loadPlanning();
                this.refreshService.demanderRafraichissement();
                this.isLoadingValidation = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors du renvoi');
                this.isLoadingValidation = false;
            }
        });
    }

    renvoyerAuPlanificateur(): void {
        if (!this.activiteEnValidation?.id) return;
        if (!this.commentaireValidation || this.commentaireValidation.trim().length === 0) {
            this.showError('Un commentaire est obligatoire pour renvoyer au planificateur');
            return;
        }
        if (!confirm('Renvoyer cette activité au planificateur pour correction ?')) return;

        const actions: ValidationConflit[] = this.conflitsAValider.map(c => ({
            affectationId: c.affectationId,
            action: c.action as any,
            agentRemplacantId: c.agentRemplacantId
        }));

        const decision: DecisionValidation = {
            actions,
            decision: 'RENVOYER_PLANIFICATEUR',
            commentaire: this.commentaireValidation
        };

        this.isLoadingValidation = true;
        this.planningService.valider(this.activiteEnValidation.id, decision).subscribe({
            next: () => {
                this.showSuccess('↩️ Activité renvoyée au planificateur');
                this.fermerValidationModal();
                this.loadPlanning();
                this.refreshService.demanderRafraichissement();
                this.isLoadingValidation = false;
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors du renvoi');
                this.isLoadingValidation = false;
            }
        });
    }

    getNiveauLabel(): string {
        const n = this.permissionService.getNiveauValidation();
        if (n === -1) return 'Super Admin';
        if (n === 1 || n === 2 || n === 3) return `Validateur Niveau ${n}`;
        return 'Validateur';
    }

    onTdrSelected(event: any): void {
        const file = event.target.files?.[0];
        if (file) this.tdrFile = file;
    }

    onOrdreMissionSelected(event: any): void {
        const file = event.target.files?.[0];
        if (file) this.ordreMissionFile = file;
    }

    onLettreInvitationSelected(event: any): void {
        const file = event.target.files?.[0];
        if (file) this.lettreInvitationFile = file;
    }

    telechargerFichier(activiteId: string, type: 'tdr' | 'ordre_mission' | 'lettre', filename: string): void {
        this.planningService.telechargerFichier(activiteId, type).subscribe({
            next: (blob) => {
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = filename;
                a.click();
                window.URL.revokeObjectURL(url);
            },
            error: () => this.showError('Erreur téléchargement fichier')
        });
    }

    renvoyerTdr(activite: Activite): void {
        if (!activite.id) return;
        if (!confirm('Renvoyer le TDR à tous les participants ?')) return;

        this.isLoading = true;
        this.planningService.renvoyerTdr(activite.id).subscribe({
            next: () => {
                this.showSuccess('TDR renvoyé aux participants');
                this.loadPlanning();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError('Erreur renvoi TDR: ' + (err.error?.message || ''));
                this.isLoading = false;
            }
        });
    }

    renvoyerOrdreMission(activite: Activite): void {
        if (!activite.id) return;
        if (!confirm('Renvoyer l\'Ordre de Mission à tous les participants ?')) return;

        this.isLoading = true;
        this.planningService.renvoyerOrdreMission(activite.id).subscribe({
            next: () => {
                this.showSuccess('Ordre de Mission renvoyé aux participants');
                this.loadPlanning();
                this.isLoading = false;
            },
            error: (err) => {
                this.showError('Erreur renvoi Ordre de Mission: ' + (err.error?.message || ''));
                this.isLoading = false;
            }
        });
    }
}