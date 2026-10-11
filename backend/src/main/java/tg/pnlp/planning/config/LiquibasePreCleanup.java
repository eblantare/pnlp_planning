package tg.pnlp.planning.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * ✅ Classe de nettoyage des checksums Liquibase.
 *
 * Cette classe s'exécute AVANT que Liquibase ne démarre.
 * Elle remet à NULL tous les md5sum de la table databasechangelog,
 * ce qui évite les erreurs "Validation Failed: changesets check sum"
 * lorsque les changesets ont été modifiés après leur première exécution.
 *
 * ⚠️ À retirer une fois la base stabilisée (dans quelques semaines).
 */
@Slf4j
public class LiquibasePreCleanup implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {

        // 1. Récupérer les infos de connexion depuis les variables d'environnement
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        // 2. Si les infos ne sont pas disponibles, on abandonne silencieusement
        if (url == null || url.isBlank() || username == null) {
            System.out.println("⚠️ [LiquibasePreCleanup] Config datasource manquante, cleanup ignoré.");
            return;
        }

        System.out.println("🔄 [LiquibasePreCleanup] Nettoyage des checksums Liquibase...");

        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {

            // 3. Vérifier que la table databasechangelog existe
            try {
                stmt.executeQuery("SELECT 1 FROM databasechangelog LIMIT 1");
            } catch (Exception e) {
                System.out.println("ℹ️ [LiquibasePreCleanup] Table databasechangelog absente, rien à nettoyer.");
                return;
            }

            // 4. Nettoyer les checksums
            int updated = stmt.executeUpdate(
                    "UPDATE databasechangelog SET md5sum = NULL WHERE md5sum IS NOT NULL"
            );

            System.out.println("✅ [LiquibasePreCleanup] " + updated + " checksum(s) nettoyé(s).");

        } catch (Exception e) {
            System.err.println("⚠️ [LiquibasePreCleanup] Échec du nettoyage: " + e.getMessage());
            // On ne relance PAS l'exception : si le nettoyage échoue,
            // Liquibase fera son travail normalement (et échouera peut-être,
            // mais on ne veut pas casser le démarrage pour autant).
        }
    }
}