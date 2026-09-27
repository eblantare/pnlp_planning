import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { StatistiquesService, StatistiquesGlobales, StatistiquesAgent } from '../../core/services/statistiques.service';
import { ExportService } from '../../core/services/export.service';

@Component({
  selector: 'app-statistiques',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, TooltipModule],
  templateUrl: './statistiques.component.html',
  styleUrls: ['./statistiques.component.css']
})
export class StatistiquesComponent implements OnInit {
  statistiques: StatistiquesGlobales | null = null;

  annee = new Date().getFullYear();
  mois = new Date().getMonth() + 1;

  isLoading = false;
  errorMessage = '';

  // Filtres
  searchTerm = '';
  filterStatut = '';

  // Pagination
  currentPage = 1;
  itemsPerPage = 10;
  pageSizeOptions = [5, 10, 25, 50];

  // États des accordéons
  showOccupationChart = false;
  showRepartitionChart = true;
  showDetailTable = true;

  Math = Math;

  constructor(
      private statistiquesService: StatistiquesService,
      private exportService: ExportService
  ) { }

  ngOnInit(): void {
    this.loadStatistiques();
  }

  loadStatistiques(): void {
    this.isLoading = true;
    this.statistiquesService.getStatistiquesMensuelles(this.annee, this.mois).subscribe({
      next: (data) => {
        this.statistiques = data;
        this.currentPage = 1;
        this.isLoading = false;
      },
      error: (err: any) => {
        console.error('Erreur chargement statistiques', err);
        this.errorMessage = 'Erreur lors du chargement des statistiques';
        this.isLoading = false;
      }
    });
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
    this.loadStatistiques();
  }

  changerAnnee(delta: number): void {
    this.annee += delta;
    this.loadStatistiques();
  }

  getMoisNom(mois: number): string {
    const moisNoms = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
      'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
    return moisNoms[mois - 1];
  }

  /**
   * Filtre la liste des agents
   */
  getFilteredAgents(): StatistiquesAgent[] {
    if (!this.statistiques) return [];

    let filtered = [...this.statistiques.agents];

    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(a =>
          a.nomComplet?.toLowerCase().includes(term) ||
          a.poste?.toLowerCase().includes(term) ||
          a.unite?.toLowerCase().includes(term)
      );
    }

    if (this.filterStatut) {
      filtered = filtered.filter(a => a.statut === this.filterStatut);
    }

    return filtered;
  }

  /**
   * Cartes agents — tous les agents avec statut
   */
  getAllAgentsCards(): StatistiquesAgent[] {
    if (!this.statistiques) return [];
    return this.statistiques.agents;
  }

  /**
   * Agents triés par taux d'occupation décroissant
   */
  getAgentsSortedByOccupation(): StatistiquesAgent[] {
    if (!this.statistiques) return [];
    return [...this.statistiques.agents]
        .sort((a, b) => b.tauxOccupation - a.tauxOccupation);
  }

  /**
   * Mois triés dans l'ordre Janvier → Décembre
   */
  getMonthsInOrder(): { key: string; value: number }[] {
    if (!this.statistiques) return [];

    const ordre: { [key: string]: number } = {
      'Jan': 1, 'Fév': 2, 'Mar': 3, 'Avr': 4, 'Mai': 5, 'Jun': 6,
      'Jul': 7, 'Aoû': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Déc': 12
    };

    return Object.entries(this.statistiques.repartitionMensuelle)
        .map(([key, value]) => ({ key, value }))
        .sort((a, b) => (ordre[a.key] || 99) - (ordre[b.key] || 99));
  }

  getMoisActifLibelle(): string {
    const moisAbrege = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Jun',
      'Jul', 'Aoû', 'Sep', 'Oct', 'Nov', 'Déc'];
    return moisAbrege[this.mois - 1];
  }

  hasActiveFilters(): boolean {
    return !!(this.searchTerm || this.filterStatut);
  }

  resetFilters(): void {
    this.searchTerm = '';
    this.filterStatut = '';
    this.currentPage = 1;
  }

  // ========== PAGINATION ==========
  get totalPages(): number {
    return Math.ceil(this.getFilteredAgents().length / this.itemsPerPage);
  }

  get paginatedAgents(): StatistiquesAgent[] {
    const start = (this.currentPage - 1) * this.itemsPerPage;
    const end = start + this.itemsPerPage;
    return this.getFilteredAgents().slice(start, end);
  }

  get pages(): number[] {
    const pages: number[] = [];
    const maxVisible = 5;
    let start = Math.max(1, this.currentPage - Math.floor(maxVisible / 2));
    const end = Math.min(this.totalPages, start + maxVisible - 1);
    if (end - start + 1 < maxVisible) {
      start = Math.max(1, end - maxVisible + 1);
    }
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  }

  getStartIndex(): number {
    const total = this.getFilteredAgents().length;
    return total === 0 ? 0 : (this.currentPage - 1) * this.itemsPerPage + 1;
  }

  getEndIndex(): number {
    return Math.min(this.currentPage * this.itemsPerPage, this.getFilteredAgents().length);
  }

  onPageChange(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
  }

  onItemsPerPageChange(): void {
    this.currentPage = 1;
  }

  onFilterChange(): void {
    this.currentPage = 1;
  }

  // ========== STATUTS ==========
  getStatutClass(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'statut-en-mission';
      case 'OCCUPE': return 'statut-occupe';
      case 'DISPONIBLE': return 'statut-disponible';
      case 'INACTIF': return 'statut-inactif';
      default: return '';
    }
  }

  getStatutCardClass(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'card-en-mission';
      case 'OCCUPE': return 'card-occupe';
      case 'DISPONIBLE': return 'card-disponible';
      case 'INACTIF': return 'card-inactif';
      default: return '';
    }
  }

  getStatutBadgeClass(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'badge-en-mission';
      case 'OCCUPE': return 'badge-occupe';
      case 'DISPONIBLE': return 'badge-disponible';
      case 'INACTIF': return 'badge-inactif';
      default: return '';
    }
  }

  getStatutLabel(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'En mission';
      case 'OCCUPE': return 'Occupé';
      case 'DISPONIBLE': return 'Disponible';
      case 'INACTIF': return 'Inactif';
      default: return statut;
    }
  }

  getStatutIcon(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'pi pi-briefcase';
      case 'OCCUPE': return 'pi pi-clock';
      case 'DISPONIBLE': return 'pi pi-check-circle';
      case 'INACTIF': return 'pi pi-ban';
      default: return 'pi pi-circle';
    }
  }

  // ========== TAUX ==========
  getTauxColor(taux: number): string {
    if (taux >= 80) return '#DC2626';
    if (taux >= 50) return '#F59E0B';
    if (taux >= 20) return '#10B981';
    return '#6B7280';
  }

  getTauxLabel(taux: number): string {
    if (taux >= 80) return 'Surchargé';
    if (taux >= 50) return 'Occupé';
    if (taux >= 20) return 'Normal';
    return 'Faible';
  }

  getInitiales(agent: StatistiquesAgent): string {
    const prenom = agent.prenom?.charAt(0)?.toUpperCase() || '';
    const nom = agent.nom?.charAt(0)?.toUpperCase() || '';
    return prenom + nom;
  }

  // ========== GRAPHIQUE ==========
  getMaxRepartition(): number {
    if (!this.statistiques) return 0;
    const values = Object.values(this.statistiques.repartitionMensuelle);
    return Math.max(...values, 1);
  }

  getBarHeight(value: number): number {
    const max = this.getMaxRepartition();
    return max > 0 ? (value / max) * 100 : 0;
  }

  // ========== EXPORTS ==========
  exportToExcel(): void {
    if (!this.statistiques) return;

    const columns = [
      { key: 'nomComplet', label: 'Agent' },
      { key: 'poste', label: 'Poste' },
      { key: 'unite', label: 'Unité' },
      { key: 'nombreActivites', label: 'Activités' },
      { key: 'joursMission', label: 'Jours mission' },
      { key: 'tauxOccupation', label: 'Taux occupation (%)' },
      { key: 'statutLabel', label: 'Statut' }
    ];

    const exportData = this.statistiques.agents.map(a => ({
      ...a,
      statutLabel: this.getStatutLabel(a.statut)
    }));

    const title = 'PNLP - STATISTIQUES DU PERSONNEL';
    const subtitle = `${this.getMoisNom(this.mois)} ${this.annee}`;

    this.exportService.exportToExcel(
        exportData,
        columns,
        `statistiques_${this.annee}_${this.mois}`,
        'Statistiques',
        title,
        subtitle
    );
  }

  exportToPdf(): void {
    if (!this.statistiques) return;

    const columns = [
      { key: 'nomComplet', label: 'Agent' },
      { key: 'poste', label: 'Poste' },
      { key: 'nombreActivites', label: 'Activités' },
      { key: 'joursMission', label: 'Jours' },
      { key: 'tauxOccupation', label: 'Taux (%)' },
      { key: 'statutLabel', label: 'Statut' }
    ];

    const exportData = this.statistiques.agents.map(a => ({
      ...a,
      statutLabel: this.getStatutLabel(a.statut)
    }));

    const title = 'PNLP - STATISTIQUES DU PERSONNEL';
    const subtitle = `${this.getMoisNom(this.mois)} ${this.annee}`;

    this.exportService.exportToPdf(
        exportData,
        columns,
        `statistiques_${this.annee}_${this.mois}`,
        title,
        subtitle
    );
  }

  // ========== ACCORDÉONS ==========
  toggleOccupationChart(): void {
    this.showOccupationChart = !this.showOccupationChart;
  }

  toggleRepartitionChart(): void {
    this.showRepartitionChart = !this.showRepartitionChart;
  }

  toggleDetailTable(): void {
    this.showDetailTable = !this.showDetailTable;
  }
}