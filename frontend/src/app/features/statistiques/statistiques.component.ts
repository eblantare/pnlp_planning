import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import {
  StatistiquesService,
  StatistiquesGlobales,
  StatistiquesAgent,
  StatistiquesAgent as AgentStat,
  ModePeriode,
  FinancementStat
} from '../../core/services/statistiques.service';
import { ExportService } from '../../core/services/export.service';
import { RefreshService } from '../../core/services/refresh.service';

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

  modePeriode: ModePeriode = 'MOIS';
  semaineISO = this.getSemaineISO(new Date());
  intervalleDebut = this.formatDateInput(new Date(new Date().getFullYear(), 0, 1));
  intervalleFin = this.formatDateInput(new Date());

  isLoading = false;
  errorMessage = '';

  searchTerm = '';
  filterStatut = '';

  currentPage = 1;
  itemsPerPage = 10;
  pageSizeOptions = [5, 10, 25, 50];

  showOccupationChart = false;
  showRepartitionChart = true;
  showDetailTable = true;
  showFinancementChart = false;

  showHistoriqueModal = false;
  historiqueAgent: AgentStat | null = null;
  isLoadingHistorique = false;
  historiqueAgentNom = '';

  searchAgentCard = '';

  showAgentDetailsModal = false;
  selectedAgentDetails: AgentStat | null = null;

  Math = Math;

  constructor(
      private statistiquesService: StatistiquesService,
      private exportService: ExportService,
      private refreshService: RefreshService
  ) { }

  ngOnInit(): void {
    // ✅ CORRECTION : on ne charge PLUS manuellement au démarrage.
    //    Le BehaviorSubject émet immédiatement sa valeur initiale (0),
    //    ce qui déclenche le 1er chargement. Cela évite un double appel HTTP
    //    (un pour ngOnInit, un pour le BehaviorSubject).
    //    À chaque mutation (création/modif/changement de statut) sur un autre
    //    écran, le compteur est incrémenté et le chargement se refait.
    this.refreshService.refresh$.subscribe(() => {
      this.loadStatistiques();
    });
  }

  // ============================================================
  // UTILITAIRES DATES
  // ============================================================
  private formatDateInput(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private getSemaineISO(d: Date): string {
    const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const dayNum = date.getUTCDay() || 7;
    date.setUTCDate(date.getUTCDate() + 4 - dayNum);
    const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
    const weekNo = Math.ceil((((date.getTime() - yearStart.getTime()) / 86400000) + 1) / 7);
    return `${date.getUTCFullYear()}-W${String(weekNo).padStart(2, '0')}`;
  }

  private getDatesSemaine(semaineStr: string): { debut: string; fin: string } {
    const [yearStr, weekStr] = semaineStr.split('-W');
    const year = parseInt(yearStr, 10);
    const week = parseInt(weekStr, 10);

    const simple = new Date(year, 0, 1 + (week - 1) * 7);
    const dow = simple.getDay();
    const ISOweekStart = simple;
    if (dow <= 4) {
      ISOweekStart.setDate(simple.getDate() - simple.getDay() + 1);
    } else {
      ISOweekStart.setDate(simple.getDate() + 8 - simple.getDay());
    }

    const fin = new Date(ISOweekStart);
    fin.setDate(fin.getDate() + 6);

    return {
      debut: this.formatDateInput(ISOweekStart),
      fin: this.formatDateInput(fin)
    };
  }

  // ============================================================
  // CHARGEMENT
  // ============================================================
  loadStatistiques(): void {
    this.isLoading = true;
    this.errorMessage = '';

    let obs$;

    switch (this.modePeriode) {
      case 'SEMAINE': {
        const { debut, fin } = this.getDatesSemaine(this.semaineISO);
        obs$ = this.statistiquesService.getStatistiquesPeriode(debut, fin);
        break;
      }
      case 'MOIS':
        obs$ = this.statistiquesService.getStatistiquesMensuelles(this.annee, this.mois);
        break;
      case 'ANNEE': {
        const debut = `${this.annee}-01-01`;
        const fin = `${this.annee}-12-31`;
        obs$ = this.statistiquesService.getStatistiquesPeriode(debut, fin);
        break;
      }
      case 'INTERVALLE':
        obs$ = this.statistiquesService.getStatistiquesPeriode(
            this.intervalleDebut, this.intervalleFin);
        break;
    }

    obs$.subscribe({
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

  onModeChange(): void {
    this.synchroniserBornesAvecAnnee();
    this.loadStatistiques();
  }

  setModePeriode(mode: ModePeriode): void {
    this.modePeriode = mode;
    this.synchroniserBornesAvecAnnee();
    this.loadStatistiques();
  }

  private synchroniserBornesAvecAnnee(): void {
    if (this.modePeriode === 'SEMAINE') {
      const [yearStr] = this.semaineISO.split('-W');
      const currentYear = parseInt(yearStr, 10);
      if (currentYear !== this.annee) {
        this.semaineISO = this.getSemaineISO(new Date(this.annee, 0, 4));
      }
    }

    if (this.modePeriode === 'INTERVALLE') {
      const debutYear = parseInt(this.intervalleDebut.substring(0, 4), 10);
      const finYear = parseInt(this.intervalleFin.substring(0, 4), 10);

      if (debutYear !== this.annee || finYear !== this.annee) {
        this.intervalleDebut = `${this.annee}-01-01`;
        this.intervalleFin = `${this.annee}-12-31`;
      }
    }
  }

  changerMois(delta: number): void {
    this.mois += delta;
    if (this.mois > 12) { this.mois = 1; this.annee++; }
    else if (this.mois < 1) { this.mois = 12; this.annee--; }
    this.synchroniserBornesAvecAnnee();
    this.loadStatistiques();
  }

  changerAnnee(delta: number): void {
    this.annee += delta;
    this.synchroniserBornesAvecAnnee();
    this.loadStatistiques();
  }

  changerSemaine(delta: number): void {
    const { debut } = this.getDatesSemaine(this.semaineISO);
    const d = new Date(debut);
    d.setDate(d.getDate() + delta * 7);
    this.semaineISO = this.getSemaineISO(d);

    const [yearStr] = this.semaineISO.split('-W');
    const nouvelleAnnee = parseInt(yearStr, 10);
    if (!isNaN(nouvelleAnnee)) {
      this.annee = nouvelleAnnee;
    }

    this.loadStatistiques();
  }

  getLibelleSemaine(): string {
    const { debut, fin } = this.getDatesSemaine(this.semaineISO);
    const d1 = new Date(debut);
    const d2 = new Date(fin);
    const fmt: Intl.DateTimeFormatOptions = { day: '2-digit', month: '2-digit' };
    return `${d1.toLocaleDateString('fr-FR', fmt)} → ${d2.toLocaleDateString('fr-FR', fmt)}`;
  }

  getMoisNom(mois: number): string {
    const moisNoms = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
      'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
    return moisNoms[mois - 1];
  }

  getPeriodeLibelle(): string {
    if (!this.statistiques) return '';
    switch (this.modePeriode) {
      case 'SEMAINE': return `Semaine du ${this.getLibelleSemaine()}`;
      case 'MOIS': return `${this.getMoisNom(this.mois)} ${this.annee}`;
      case 'ANNEE': return `Année ${this.annee}`;
      case 'INTERVALLE': return `${this.intervalleDebut} → ${this.intervalleFin}`;
    }
    return '';
  }

  // ============================================================
  // RECHERCHE + MODAL DÉTAILS "ÉTAT DES AGENTS"
  // ============================================================
  getFilteredAgentsCards(): StatistiquesAgent[] {
    if (!this.statistiques) return [];
    const all = this.statistiques.agents;

    if (!this.searchAgentCard) return all;

    const term = this.searchAgentCard.toLowerCase().trim();
    return all.filter(a =>
        a.nomComplet?.toLowerCase().includes(term) ||
        a.nom?.toLowerCase().includes(term) ||
        a.prenom?.toLowerCase().includes(term) ||
        a.poste?.toLowerCase().includes(term)
    );
  }

  ouvrirDetailsAgent(agent: StatistiquesAgent): void {
    this.selectedAgentDetails = agent;
    this.showAgentDetailsModal = true;
  }

  closeAgentDetailsModal(): void {
    this.showAgentDetailsModal = false;
    this.selectedAgentDetails = null;
  }

  getTacheLabel(agent: StatistiquesAgent): string {
    return agent.tacheEnCours || '';
  }

  getTooltipAgentCard(agent: StatistiquesAgent): string {
    let text = `${agent.nomComplet}\n`;
    text += `Statut : ${this.getStatutLabel(agent.statut)}\n`;
    text += `Activités : ${agent.nombreActivites}\n`;
    text += `Jours mission : ${agent.joursMission}j\n`;

    if (agent.statut === 'EN_MISSION' && agent.tacheEnCours) {
      text += `📌 En cours : ${agent.tacheEnCours}`;
      if (agent.periodeActuelle) text += ` (${agent.periodeActuelle})`;
    } else if (agent.statut === 'AU_PROGRAMME' && agent.tacheEnCours) {
      text += `📅 Au programme : ${agent.tacheEnCours}`;
      if (agent.periodeActuelle) text += ` (${agent.periodeActuelle})`;
    }

    return text;
  }

  // ============================================================
  // HISTORIQUE AGENT
  // ============================================================
  ouvrirHistorique(agent: AgentStat): void {
    if (!agent.agentId) return;

    this.historiqueAgentNom = agent.nomComplet;
    this.showHistoriqueModal = true;
    this.isLoadingHistorique = true;
    this.historiqueAgent = null;

    let debut: string, fin: string;

    switch (this.modePeriode) {
      case 'SEMAINE': {
        const s = this.getDatesSemaine(this.semaineISO);
        debut = s.debut; fin = s.fin; break;
      }
      case 'MOIS': {
        const d = new Date(this.annee, this.mois - 1, 1);
        const f = new Date(this.annee, this.mois, 0);
        debut = this.formatDateInput(d);
        fin = this.formatDateInput(f);
        break;
      }
      case 'ANNEE': {
        debut = `${this.annee}-01-01`;
        fin = `${this.annee}-12-31`;
        break;
      }
      case 'INTERVALLE':
        debut = this.intervalleDebut;
        fin = this.intervalleFin;
        break;
    }

    this.statistiquesService.getHistoriqueAgent(agent.agentId, debut, fin).subscribe({
      next: (data) => {
        this.historiqueAgent = data;
        this.isLoadingHistorique = false;
      },
      error: (err) => {
        console.error('Erreur chargement historique', err);
        this.isLoadingHistorique = false;
      }
    });
  }

  closeHistoriqueModal(): void {
    this.showHistoriqueModal = false;
    this.historiqueAgent = null;
  }

  imprimerHistorique(): void {
    if (!this.historiqueAgent) return;

    const win = window.open('', '_blank');
    if (!win) return;

    const agent = this.historiqueAgent;
    const act = agent.activites || [];

    let html = `<!DOCTYPE html><html><head><meta charset="utf-8">
        <title>Historique - ${agent.nomComplet}</title>
        <style>
            body { font-family: Arial, sans-serif; padding: 24px; color:#333; }
            h1 { color:#1B5E20; margin:0 0 4px 0; font-size:20px; }
            .sub { color:#666; font-size:13px; margin-bottom:20px; }
            .kpis { display:flex; gap:12px; margin-bottom:24px; flex-wrap:wrap; }
            .kpi { background:#F8FAFB; border-left:4px solid #2E7D32; padding:10px 14px; border-radius:6px; min-width:130px; }
            .kpi-val { font-size:20px; font-weight:800; color:#1B5E20; }
            .kpi-lab { font-size:10px; text-transform:uppercase; color:#666; letter-spacing:0.5px; }
            .kpi.resident { background:#E8F5E9; border-left-color:#2E7D32; }
            .kpi.non-resident { background:#FFF3E0; border-left-color:#E65100; }
            .kpi.resident .kpi-val { color:#2E7D32; }
            .kpi.non-resident .kpi-val { color:#E65100; }
            table { width:100%; border-collapse:collapse; font-size:12px; }
            th { background:#E3F2FD; color:#0D47A1; padding:8px; text-align:left; text-transform:uppercase; font-size:10px; }
            td { padding:8px; border-bottom:1px solid #eee; }
            .badge { padding:2px 8px; border-radius:10px; font-size:10px; font-weight:700; text-transform:uppercase; }
            .badge-PLANIFIEE { background:#E3F2FD; color:#1565C0; }
            .badge-EN_COURS { background:#FFF3E0; color:#E65100; }
            .badge-TERMINEE { background:#E8F5E9; color:#2E7D32; }
            .badge-ANNULEE { background:#FFEBEE; color:#C62828; }
            .badge-REPORTEE { background:#F3E5F5; color:#6A1B9A; }
            .badge-BROUILLON { background:#F3F4F6; color:#6B7280; }
            .type-resident { background:#E8F5E9; color:#2E7D32; padding:2px 8px; border-radius:10px; font-size:10px; font-weight:700; }
            .type-non-resident { background:#FFF3E0; color:#E65100; padding:2px 8px; border-radius:10px; font-size:10px; font-weight:700; }
            @media print { body { padding: 0; } .no-print { display:none; } }
        </style></head><body>
        <h1>PNLP — Historique des activités</h1>
        <div class="sub">
            <strong>${agent.nomComplet}</strong> — ${agent.poste || 'Agent'} — ${agent.unite || ''}<br>
            Période : ${this.getPeriodeLibelle()}
        </div>
        <div class="kpis">
            <div class="kpi"><div class="kpi-val">${agent.nombreActivites}</div><div class="kpi-lab">Activités</div></div>
            <div class="kpi resident"><div class="kpi-val">${agent.missionsResident || 0}</div><div class="kpi-lab">🏠 Résident</div></div>
            <div class="kpi non-resident"><div class="kpi-val">${agent.missionsNonResident || 0}</div><div class="kpi-lab">🚗 Non résident</div></div>
            <div class="kpi"><div class="kpi-val">${agent.joursMission}j</div><div class="kpi-lab">Jours mission</div></div>
            <div class="kpi"><div class="kpi-val">${agent.joursOuvrables}j</div><div class="kpi-lab">Jours ouvrables</div></div>
            <div class="kpi"><div class="kpi-val">${agent.tauxOccupation}%</div><div class="kpi-lab">Taux occupation</div></div>
        </div>
        <table>
            <thead><tr>
                <th>Titre</th><th>Début</th><th>Fin</th><th>Jours</th>
                <th>Lieu</th><th>Zone</th><th>Financement</th><th>Type</th><th>Statut</th>
            </tr></thead>
            <tbody>`;

    if (act.length === 0) {
      html += `<tr><td colspan="9" style="text-align:center;color:#999;padding:20px;">Aucune activité</td></tr>`;
    } else {
      for (const a of act) {
        const fmt = (d?: string) => d ? new Date(d).toLocaleDateString('fr-FR') : '-';
        const typeCls = a.typeLieu === 'RESIDENT' ? 'type-resident' : 'type-non-resident';
        const typeLbl = a.typeLieu === 'RESIDENT' ? '🏠 Résident' : '🚗 Non résident';
        html += `<tr>
                    <td><strong>${a.titre || '-'}</strong></td>
                    <td>${fmt(a.dateDebut)}</td>
                    <td>${fmt(a.dateFin)}</td>
                    <td>${a.nombreJours || 0}j</td>
                    <td>${a.lieu || '-'}</td>
                    <td>${a.zone || '-'}</td>
                    <td>${a.sourceFinancement || '-'}</td>
                    <td><span class="${typeCls}">${typeLbl}</span></td>
                    <td><span class="badge badge-${a.statut}">${this.getStatutLabel(a.statut || '')}</span></td>
                </tr>`;
      }
    }

    html += `</tbody></table>
        <div class="no-print" style="margin-top:20px;text-align:right;">
            <button onclick="window.print()" style="padding:8px 16px;background:#1B5E20;color:white;border:none;border-radius:6px;cursor:pointer;">🖨️ Imprimer</button>
        </div>
        </body></html>`;

    win.document.write(html);
    win.document.close();
  }

  // ============================================================
  // FINANCEMENT
  // ============================================================
  getFinancementMax(): number {
    if (!this.statistiques?.repartitionParFinancement) return 1;
    return Math.max(...this.statistiques.repartitionParFinancement.map(f => f.totalActivites), 1);
  }

  getFinancementBarWidth(value: number): number {
    return (value / this.getFinancementMax()) * 100;
  }

  toggleFinancementChart(): void {
    this.showFinancementChart = !this.showFinancementChart;
  }

  // ============================================================
  // TOOLTIP TAUX OCCUPATION
  // ============================================================
  getTooltipOccupation(agent: AgentStat): string {
    return `${agent.nomComplet}\n` +
        `Jours mission : ${agent.joursMission} / ${agent.joursOuvrables} jours ouvrables\n` +
        `Taux total : ${agent.tauxOccupation}%\n` +
        `  🏠 Résident : ${agent.joursResident || 0}j (${agent.tauxResident || 0}%)\n` +
        `  🚗 Non résident : ${agent.joursNonResident || 0}j (${agent.tauxNonResident || 0}%)\n` +
        `Activités : ${agent.nombreActivites}`;
  }

  // ============================================================
  // FILTRES & PAGINATION
  // ============================================================
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

  getAllAgentsCards(): StatistiquesAgent[] {
    if (!this.statistiques) return [];
    return this.statistiques.agents;
  }

  getAgentsSortedByOccupation(): StatistiquesAgent[] {
    if (!this.statistiques) return [];
    return [...this.statistiques.agents].sort((a, b) => b.tauxOccupation - a.tauxOccupation);
  }

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

  // ============================================================
  // STATUTS
  // ============================================================
  getStatutClass(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'statut-en-mission';
      case 'AU_PROGRAMME': return 'statut-au-programme';
      case 'OCCUPE': return 'statut-occupe';
      case 'DISPONIBLE': return 'statut-disponible';
      case 'INACTIF': return 'statut-inactif';
      default: return '';
    }
  }

  getStatutCardClass(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'card-en-mission';
      case 'AU_PROGRAMME': return 'card-au-programme';
      case 'OCCUPE': return 'card-occupe';
      case 'DISPONIBLE': return 'card-disponible';
      case 'INACTIF': return 'card-inactif';
      default: return '';
    }
  }

  getStatutBadgeClass(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'badge-en-mission';
      case 'AU_PROGRAMME': return 'badge-au-programme';
      case 'OCCUPE': return 'badge-occupe';
      case 'DISPONIBLE': return 'badge-disponible';
      case 'INACTIF': return 'badge-inactif';
      default: return '';
    }
  }

  getStatutLabel(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'En mission';
      case 'AU_PROGRAMME': return 'Au programme';
      case 'OCCUPE': return 'Occupé';
      case 'DISPONIBLE': return 'Disponible';
      case 'INACTIF': return 'Inactif';
      case 'PLANIFIEE': return 'Planifiée';
      case 'EN_COURS': return 'En cours';
      case 'TERMINEE': return 'Terminée';
      case 'ANNULEE': return 'Annulée';
      case 'REPORTEE': return 'Reportée';
      case 'BROUILLON': return 'Brouillon';
      default: return statut;
    }
  }

  getStatutIcon(statut: string): string {
    switch (statut) {
      case 'EN_MISSION': return 'pi pi-briefcase';
      case 'AU_PROGRAMME': return 'pi pi-calendar-clock';
      case 'OCCUPE': return 'pi pi-clock';
      case 'DISPONIBLE': return 'pi pi-check-circle';
      case 'INACTIF': return 'pi pi-ban';
      default: return 'pi pi-circle';
    }
  }

  // ============================================================
  // TAUX
  // ============================================================
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

  getMissionsProgramme(agent: StatistiquesAgent): number {
    return agent.missionsProgramme || 0;
  }

  getInitiales(agent: StatistiquesAgent): string {
    const prenom = agent.prenom?.charAt(0)?.toUpperCase() || '';
    const nom = agent.nom?.charAt(0)?.toUpperCase() || '';
    return prenom + nom;
  }

  getMaxRepartition(): number {
    if (!this.statistiques) return 0;
    const values = Object.values(this.statistiques.repartitionMensuelle);
    return Math.max(...values, 1);
  }

  getBarHeight(value: number): number {
    const max = this.getMaxRepartition();
    return max > 0 ? (value / max) * 100 : 0;
  }

  // ============================================================
  // HELPERS TYPE DE LIEU
  // ============================================================
  getTypeLieuLabel(typeLieu?: string): string {
    if (typeLieu === 'RESIDENT') return '🏠 Résident';
    if (typeLieu === 'NON_RESIDENT') return '🚗 Non résident';
    return '-';
  }

  getTypeLieuClass(typeLieu?: string): string {
    return typeLieu === 'RESIDENT' ? 'type-resident' : 'type-non-resident';
  }

  // ============================================================
  // ACCORDÉONS
  // ============================================================
  toggleOccupationChart(): void { this.showOccupationChart = !this.showOccupationChart; }
  toggleRepartitionChart(): void { this.showRepartitionChart = !this.showRepartitionChart; }
  toggleDetailTable(): void { this.showDetailTable = !this.showDetailTable; }

  // ============================================================
  // EXPORTS
  // ============================================================
  exportToExcel(): void {
    if (!this.statistiques) return;
    const columns = [
      { key: 'nomComplet', label: 'Agent' },
      { key: 'poste', label: 'Poste' },
      { key: 'unite', label: 'Unité' },
      { key: 'nombreActivites', label: 'Activités' },
      { key: 'missionsResident', label: 'Résident' },
      { key: 'missionsNonResident', label: 'Non résident' },
      { key: 'joursMission', label: 'Jours mission' },
      { key: 'tauxOccupation', label: 'Taux occupation (%)' },
      { key: 'statutLabel', label: 'Statut' }
    ];
    const exportData = this.statistiques.agents.map(a => ({
      ...a,
      missionsResident: a.missionsResident || 0,
      missionsNonResident: a.missionsNonResident || 0,
      statutLabel: this.getStatutLabel(a.statut)
    }));
    const title = 'PNLP - STATISTIQUES DU PERSONNEL';
    const subtitle = this.getPeriodeLibelle();
    this.exportService.exportToExcel(
        exportData, columns,
        `statistiques_${this.annee}_${this.mois}`,
        'Statistiques', title, subtitle
    );
  }

  exportToPdf(): void {
    if (!this.statistiques) return;
    const columns = [
      { key: 'nomComplet', label: 'Agent' },
      { key: 'poste', label: 'Poste' },
      { key: 'nombreActivites', label: 'Activités' },
      { key: 'missionsResident', label: 'Rés.' },
      { key: 'missionsNonResident', label: 'Non rés.' },
      { key: 'joursMission', label: 'Jours' },
      { key: 'tauxOccupation', label: 'Taux (%)' },
      { key: 'statutLabel', label: 'Statut' }
    ];
    const exportData = this.statistiques.agents.map(a => ({
      ...a,
      missionsResident: a.missionsResident || 0,
      missionsNonResident: a.missionsNonResident || 0,
      statutLabel: this.getStatutLabel(a.statut)
    }));
    const title = 'PNLP - STATISTIQUES DU PERSONNEL';
    const subtitle = this.getPeriodeLibelle();
    this.exportService.exportToPdf(
        exportData, columns,
        `statistiques_${this.annee}_${this.mois}`,
        title, subtitle
    );
  }
}