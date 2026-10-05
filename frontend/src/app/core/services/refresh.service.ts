import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

/**
 * ✅ Service de rafraîchissement global.
 *
 * Émet un signal à chaque mutation métier (création, modification,
 * suppression, changement de statut, toggle "au programme"...).
 * Tous les écrans (Dashboard, Planning, Statistiques) s'y abonnent
 * pour recharger automatiquement leurs données.
 *
 * ✅ CORRECTION : on utilise un BehaviorSubject (avec compteur) au lieu
 *    d'un simple Subject. Cela permet :
 *      - De conserver le dernier signal émis
 *      - De le réémettre à tout nouvel abonné (utile quand on navigue
 *        d'un écran à un autre : le composant qui s'initialise reçoit
 *        immédiatement le signal du dernier changement)
 *
 *    ⚠️ Conséquence : un composant qui s'abonne dans ngOnInit va recevoir
 *    une 1ère émission "initiale" (valeur 0). Pour éviter un double
 *    chargement, les composants abonnés NE DOIVENT PAS appeler
 *    loadXxx() dans ngOnInit — l'abonnement suffit.
 *    (Voir statistiques.component.ts et dashboard.component.ts corrigés.)
 */
@Injectable({ providedIn: 'root' })
export class RefreshService {

    // Compteur incrémental : chaque appel augmente la valeur
    private refreshCounter = new BehaviorSubject<number>(0);

    /**
     * Observable public. Émet à chaque demande de rafraîchissement.
     */
    readonly refresh$: Observable<number> = this.refreshCounter.asObservable();

    /**
     * Demande un rafraîchissement de tous les écrans abonnés.
     */
    demanderRafraichissement(): void {
        this.refreshCounter.next(this.refreshCounter.value + 1);
    }

    /**
     * ✅ NOUVEAU : compteur courant (utile pour distinguer
     *    l'émission initiale des vraies demandes).
     */
    getCompteurCourant(): number {
        return this.refreshCounter.value;
    }
}