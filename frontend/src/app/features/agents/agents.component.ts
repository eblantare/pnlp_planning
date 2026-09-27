import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';           // ✅ AJOUT
import { TooltipModule } from 'primeng/tooltip';         // ✅ AJOUT
import { AgentService, Agent, CreateAgentRequest } from '../../core/services/agent.service';
import { ExportService } from '../../core/services/export.service';
import { ActionButton, ActionButtonsComponent } from '../../shared/components/action-buttons/action-buttons.component';

@Component({
    selector: 'app-agents',
    standalone: true,
    imports: [
        CommonModule,
        FormsModule,
        ButtonModule,             // ✅ AJOUT
        TooltipModule,            // ✅ AJOUT
        ActionButtonsComponent
    ],
    templateUrl: './agents.component.html',
    styleUrls: ['./agents.component.css']
})
export class AgentsComponent implements OnInit {
    // Données
    allAgents: Agent[] = [];
    filteredAgents: Agent[] = [];
    paginatedAgents: Agent[] = [];

    // Filtres
    searchTerm = '';
    filterPoste = '';
    filterUnite = '';
    filterStatut = '';

    // Listes pour les filtres
    postes: string[] = [];
    unites: string[] = [];

    // Pagination
    currentPage = 1;
    itemsPerPage = 10;
    totalItems = 0;
    pageSizeOptions = [5, 10, 25, 50];

    // Formulaire
    formData: any = this.getEmptyForm();
    editingAgent: Agent | null = null;

    // Modals
    showForm = false;
    showDetailModal = false;
    selectedAgent: Agent | null = null;

    // États
    isLoading = false;
    errorMessage = '';
    successMessage = '';

    Math = Math;

    constructor(
        private agentService: AgentService,
        private exportService: ExportService
    ) { }

    ngOnInit(): void {
        this.loadAgents();
    }

    getEmptyForm(): any {
        return {
            nom: '',
            prenom: '',
            email: '',
            telephone: '',
            poste: '',
            unite: ''
        };
    }

    loadAgents(): void {
        this.isLoading = true;
        this.agentService.getAllAgents().subscribe({
            next: (data) => {
                this.allAgents = data.sort((a, b) =>
                    (a.nom || '').localeCompare(b.nom || ''));

                this.postes = [...new Set(data.map(a => a.poste).filter(Boolean))] as string[];
                this.unites = [...new Set(data.map(a => a.unite).filter(Boolean))] as string[];

                this.applyFilters();
                this.isLoading = false;
            },
            error: (err) => {
                console.error('Erreur chargement agents', err);
                this.showError('Erreur lors du chargement des agents');
                this.isLoading = false;
            }
        });
    }

    applyFilters(): void {
        let filtered = [...this.allAgents];

        if (this.searchTerm) {
            const term = this.searchTerm.toLowerCase();
            filtered = filtered.filter(a =>
                a.nom?.toLowerCase().includes(term) ||
                a.prenom?.toLowerCase().includes(term) ||
                a.poste?.toLowerCase().includes(term) ||
                a.unite?.toLowerCase().includes(term) ||
                a.email?.toLowerCase().includes(term) ||
                a.telephone?.toLowerCase().includes(term)
            );
        }

        if (this.filterPoste) {
            filtered = filtered.filter(a => a.poste === this.filterPoste);
        }

        if (this.filterUnite) {
            filtered = filtered.filter(a => a.unite === this.filterUnite);
        }

        if (this.filterStatut) {
            const isActif = this.filterStatut === 'ACTIF';
            filtered = filtered.filter(a => a.actif === isActif);
        }

        this.filteredAgents = filtered;
        this.totalItems = filtered.length;
        this.currentPage = 1;
        this.updatePaginated();
    }

    updatePaginated(): void {
        const start = (this.currentPage - 1) * this.itemsPerPage;
        const end = start + this.itemsPerPage;
        this.paginatedAgents = this.filteredAgents.slice(start, end);
    }

    resetFilters(): void {
        this.searchTerm = '';
        this.filterPoste = '';
        this.filterUnite = '';
        this.filterStatut = '';
        this.applyFilters();
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

        for (let i = start; i <= end; i++) {
            pages.push(i);
        }
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
        this.editingAgent = null;
        this.formData = this.getEmptyForm();
        this.showForm = true;
    }

    editAgent(agent: Agent): void {
        this.editingAgent = agent;
        this.formData = {
            nom: agent.nom,
            prenom: agent.prenom,
            email: agent.email || '',
            telephone: agent.telephone || '',
            poste: agent.poste || '',
            unite: agent.unite || ''
        };
        this.showForm = true;
    }

    closeForm(): void {
        this.showForm = false;
        this.editingAgent = null;
        this.formData = this.getEmptyForm();
    }

    saveAgent(): void {
        if (!this.formData.nom || !this.formData.prenom) {
            this.showError('Le nom et le prénom sont obligatoires');
            return;
        }

        this.isLoading = true;

        if (this.editingAgent?.id) {
            this.agentService.updateAgent(this.editingAgent.id, this.formData).subscribe({
                next: () => {
                    this.showSuccess('Agent modifié avec succès');
                    this.closeForm();
                    this.loadAgents();
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur lors de la modification');
                    this.isLoading = false;
                }
            });
        } else {
            this.agentService.createAgent(this.formData).subscribe({
                next: () => {
                    this.showSuccess('Agent créé avec succès');
                    this.closeForm();
                    this.loadAgents();
                },
                error: (err) => {
                    this.showError(err.error?.message || 'Erreur lors de la création');
                    this.isLoading = false;
                }
            });
        }
    }

    deleteAgent(agent: Agent): void {
        if (!agent.id) return;

        if (confirm(`Désactiver l'agent "${agent.prenom} ${agent.nom}" ?`)) {
            this.agentService.deleteAgent(agent.id).subscribe({
                next: () => {
                    this.showSuccess('Agent désactivé');
                    this.loadAgents();
                },
                error: () => {
                    this.showError('Erreur lors de la désactivation');
                }
            });
        }
    }

    viewAgent(agent: Agent): void {
        this.selectedAgent = agent;
        this.showDetailModal = true;
    }

    closeDetailModal(): void {
        this.showDetailModal = false;
        this.selectedAgent = null;
    }

    countActifs(): number {
        return this.allAgents.filter(a => a.actif).length;
    }

    hasActiveFilters(): boolean {
        return !!(this.searchTerm || this.filterPoste || this.filterUnite || this.filterStatut);
    }

    showSuccess(message: string): void {
        this.successMessage = message;
        setTimeout(() => this.successMessage = '', 3000);
    }

    showError(message: string): void {
        this.errorMessage = message;
        setTimeout(() => this.errorMessage = '', 5000);
    }

    changerStatut(agent: Agent): void {
        if (!agent.id) return;

        const nouveauStatut = !agent.actif;
        const label = nouveauStatut ? 'activer' : 'désactiver';

        if (!confirm(`Voulez-vous ${label} l'agent "${agent.prenom} ${agent.nom}" ?`)) {
            return;
        }

        this.agentService.changerStatut(agent.id, nouveauStatut).subscribe({
            next: () => {
                this.showSuccess(`Agent ${nouveauStatut ? 'activé' : 'désactivé'} avec succès`);
                this.loadAgents();
            },
            error: (err) => {
                this.showError(err.error?.message || 'Erreur lors du changement de statut');
            }
        });
    }

    // ✅ NOUVEAU : Actions pour un agent
    getActionsFor(agent: Agent): ActionButton[] {
        return [
            {
                id: 'view',
                icon: 'pi pi-eye',
                label: 'Voir détails',
                severity: 'info'
            },
            {
                id: 'toggle',
                icon: agent.actif ? 'pi pi-lock' : 'pi pi-lock-open',
                label: agent.actif ? 'Désactiver' : 'Activer',
                severity: agent.actif ? 'danger' : 'success'
            },
            {
                id: 'edit',
                icon: 'pi pi-pencil',
                label: 'Modifier',
                severity: 'warning',
                show: !agent.actif   // Modifier seulement si INACTIF
            },
            {
                id: 'delete',
                icon: 'pi pi-trash',
                label: 'Désactiver',
                severity: 'danger',
                show: !agent.actif   // Supprimer seulement si INACTIF
            }
        ];
    }

    onAction(actionId: string, agent: Agent): void {
        switch (actionId) {
            case 'view':   this.viewAgent(agent); break;
            case 'edit':   this.editAgent(agent); break;
            case 'toggle': this.changerStatut(agent); break;
            case 'delete': this.deleteAgent(agent); break;
        }
    }

    // ========== EXPORT ==========
    exportToExcel(): void {
        const dataToExport = this.filteredAgents.length > 0 ? this.filteredAgents : this.allAgents;
        const columns = [
            { key: 'nom', label: 'Nom' },
            { key: 'prenom', label: 'Prénom' },
            { key: 'email', label: 'Email' },
            { key: 'telephone', label: 'Téléphone' },
            { key: 'poste', label: 'Poste' },
            { key: 'unite', label: 'Unité / Service' },
            { key: 'actif', label: 'Statut' }
        ];

        const exportData = dataToExport.map(agent => ({
            ...agent,
            actif: agent.actif ? 'Actif' : 'Inactif'
        }));

        const fileName = this.hasActiveFilters() ? 'agents_filtres' : 'agents_complet';
        const title = 'PNLP - LISTE DU PERSONNEL';
        const subtitle = this.hasActiveFilters()
            ? `Filtres appliqués : ${this.getActiveFiltersDescription()}`
            : `Liste complète du personnel - Généré le ${new Date().toLocaleString('fr-FR')}`;

        this.exportService.exportToExcel(exportData, columns, fileName, 'Personnel', title, subtitle);
        this.showSuccess('Export Excel réussi');
    }

    exportToPdf(): void {
        const dataToExport = this.filteredAgents.length > 0 ? this.filteredAgents : this.allAgents;
        const columns = [
            { key: 'nom', label: 'Nom' },
            { key: 'prenom', label: 'Prénom' },
            { key: 'email', label: 'Email' },
            { key: 'telephone', label: 'Téléphone' },
            { key: 'poste', label: 'Poste' },
            { key: 'unite', label: 'Unité / Service' },
            { key: 'actif', label: 'Statut' }
        ];

        const exportData = dataToExport.map(agent => ({
            ...agent,
            actif: agent.actif ? 'Actif' : 'Inactif'
        }));

        const fileName = this.hasActiveFilters() ? 'agents_filtres' : 'agents_complet';
        const title = 'PNLP - LISTE DU PERSONNEL';
        const subtitle = this.hasActiveFilters()
            ? `Filtres appliqués : ${this.getActiveFiltersDescription()}`
            : `Liste complète du personnel`;

        this.exportService.exportToPdf(exportData, columns, fileName, title, subtitle);
        this.showSuccess('Export PDF réussi');
    }

    getActiveFiltersDescription(): string {
        const filters: string[] = [];
        if (this.searchTerm) filters.push(`Recherche: "${this.searchTerm}"`);
        if (this.filterPoste) filters.push(`Poste: ${this.filterPoste}`);
        if (this.filterUnite) filters.push(`Unité: ${this.filterUnite}`);
        if (this.filterStatut) filters.push(`Statut: ${this.filterStatut}`);
        return filters.join(' | ') || 'Aucun filtre';
    }
}