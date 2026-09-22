package tg.pnlp.planning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaAuditing
@EnableTransactionManagement
public class PnlpPlanningApplication {

    public static void main(String[] args) {
        SpringApplication.run(PnlpPlanningApplication.class, args);
        System.out.println("==============================================");
        System.out.println("   PNLP PLANNING - Application démarrée      ");
        System.out.println("   API: http://localhost:8080/api            ");
        System.out.println("   Swagger: http://localhost:8080/api/swagger-ui.html");
        System.out.println("==============================================");
    }
}