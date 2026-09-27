import { Injectable } from '@angular/core';
import * as XLSX from 'xlsx';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';

@Injectable({
    providedIn: 'root'
})
export class ExportService {

    /**
     * Export Excel avec titre
     */
    exportToExcel(
        data: any[],
        columns: { key: string; label: string }[],
        fileName: string,
        sheetName: string = 'Données',
        title?: string,
        subtitle?: string
    ): void {
        // 1. Créer les en-têtes du titre
        const titleRows: any[][] = [];

        if (title) {
            titleRows.push([title]);
        }
        if (subtitle) {
            titleRows.push([subtitle]);
        }

        // Ajouter une ligne vide entre titre et tableau
        if (titleRows.length > 0) {
            titleRows.push([]); // Ligne vide
        }

        // 2. Créer les en-têtes des colonnes
        const headers = columns.map(col => col.label);

        // 3. Créer les données
        const rows = data.map(item =>
            columns.map(col => this.getValue(item, col.key))
        );

        // 4. Combiner : titres + en-têtes + données
        const wsData = [
            ...titleRows,
            headers,
            ...rows
        ];

        // 5. Créer la feuille
        const worksheet = XLSX.utils.aoa_to_sheet(wsData);

        // 6. Fusionner les cellules du titre sur toute la largeur
        if (title) {
            const lastCol = columns.length - 1;
            worksheet['!merges'] = worksheet['!merges'] || [];
            worksheet['!merges'].push({
                s: { r: 0, c: 0 },
                e: { r: 0, c: lastCol }
            });

            // Style du titre (gras, plus grand)
            const titleCell = XLSX.utils.encode_cell({ r: 0, c: 0 });
            if (worksheet[titleCell]) {
                worksheet[titleCell].s = {
                    font: { bold: true, size: 16, color: { rgb: "1B5E20" } },
                    alignment: { horizontal: "center", vertical: "center" }
                };
            }
        }

        if (subtitle) {
            const lastCol = columns.length - 1;
            const subtitleRowIndex = title ? 1 : 0;
            worksheet['!merges'] = worksheet['!merges'] || [];
            worksheet['!merges'].push({
                s: { r: subtitleRowIndex, c: 0 },
                e: { r: subtitleRowIndex, c: lastCol }
            });

            const subtitleCell = XLSX.utils.encode_cell({ r: subtitleRowIndex, c: 0 });
            if (worksheet[subtitleCell]) {
                worksheet[subtitleCell].s = {
                    font: { italic: true, size: 11, color: { rgb: "666666" } },
                    alignment: { horizontal: "center", vertical: "center" }
                };
            }
        }

        // 7. Ajuster la largeur des colonnes
        worksheet['!cols'] = columns.map(col => ({
            wch: Math.max(col.label.length + 5, 15)
        }));

        // 8. Créer le classeur et ajouter la feuille
        const workbook = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(workbook, worksheet, sheetName);

        // 9. Télécharger
        const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
        XLSX.writeFile(workbook, `${fileName}_${timestamp}.xlsx`);
    }

    /**
     * Export PDF générique
     */
    exportToPdf(
        data: any[],
        columns: { key: string; label: string }[],
        fileName: string,
        title: string,
        subtitle?: string
    ): void {
        const doc = new jsPDF('l', 'mm', 'a4'); // Paysage

        // Titre
        doc.setFontSize(18);
        doc.setTextColor(27, 94, 32);
        doc.text(title, 14, 20);

        // Sous-titre
        if (subtitle) {
            doc.setFontSize(11);
            doc.setTextColor(100, 100, 100);
            doc.text(subtitle, 14, 28);
        }

        // Date
        doc.setFontSize(9);
        doc.setTextColor(120, 120, 120);
        const dateStr = new Date().toLocaleString('fr-FR');
        doc.text(`Généré le : ${dateStr}`, 14, subtitle ? 34 : 28);

        // Préparer les données
        const head = [columns.map(col => col.label)];
        const body = data.map(item =>
            columns.map(col => this.getValue(item, col.key))
        );

        // Tableau
        autoTable(doc, {
            head: head,
            body: body,
            startY: subtitle ? 38 : 32,
            styles: {
                fontSize: 9,
                cellPadding: 3
            },
            headStyles: {
                fillColor: [27, 94, 32],
                textColor: [255, 255, 255],
                fontStyle: 'bold'
            },
            alternateRowStyles: {
                fillColor: [245, 245, 245]
            },
            margin: { left: 14, right: 14 }
        });

        // Télécharger
        const timestamp = new Date().toISOString().slice(0, 19).replace(/:/g, '-');
        doc.save(`${fileName}_${timestamp}.pdf`);
    }

    /**
     * Récupérer la valeur d'un objet par chemin (ex: "agent.nom")
     */
    private getValue(obj: any, path: string): any {
        const value = path.split('.').reduce((acc, part) => acc?.[part], obj);
        return value !== null && value !== undefined ? value : '-';
    }
}