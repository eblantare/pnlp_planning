import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
    selector: 'app-acces-refuse',
    standalone: true,
    imports: [CommonModule],
    templateUrl: './acces-refuse.component.html',
    styleUrls: ['./acces-refuse.component.css']
})
export class AccesRefuseComponent {

    constructor(private router: Router) { }

    retourAuDashboard(): void {
        this.router.navigate(['/dashboard']);
    }

    seDeconnecter(): void {
        localStorage.removeItem('token');
        localStorage.removeItem('user');
        this.router.navigate(['/login']);
    }
}