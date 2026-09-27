import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
    providedIn: 'root'
})
export class PasswordResetService {
    private apiUrl = `${environment.apiUrl}/auth/password`;

    constructor(private http: HttpClient) { }

    demanderReinitialisation(email: string): Observable<{ message: string }> {
        return this.http.post<{ message: string }>(
            `${this.apiUrl}/forgot`,
            { email }
        );
    }

    verifierToken(token: string): Observable<{ valide: boolean }> {
        return this.http.get<{ valide: boolean }>(
            `${this.apiUrl}/verify-token`,
            { params: { token } }
        );
    }

    reinitialiserMotDePasse(token: string, nouveauMotDePasse: string): Observable<{ message: string }> {
        return this.http.post<{ message: string }>(
            `${this.apiUrl}/reset`,
            { token, nouveauMotDePasse }
        );
    }
}