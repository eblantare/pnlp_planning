package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.ForgotPasswordRequest;
import tg.pnlp.planning.dto.ResetPasswordRequest;
import tg.pnlp.planning.service.PasswordResetService;

import java.util.Map;

@RestController
@RequestMapping("/auth/password")
@RequiredArgsConstructor
@Tag(name = "Mot de passe oublié", description = "Réinitialisation de mot de passe")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/forgot")
    @Operation(summary = "Demander la réinitialisation du mot de passe")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.demanderReinitialisation(request);
        return ResponseEntity.ok(Map.of(
                "message", "Un email de réinitialisation vous a été envoyé"
        ));
    }

    @GetMapping("/verify-token")
    @Operation(summary = "Vérifier qu'un token de réinitialisation est valide")
    public ResponseEntity<Map<String, Boolean>> verifyToken(@RequestParam String token) {
        boolean valide = passwordResetService.verifierToken(token);
        return ResponseEntity.ok(Map.of("valide", valide));
    }

    @PostMapping("/reset")
    @Operation(summary = "Réinitialiser le mot de passe avec le token")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reinitialiserMotDePasse(request);
        return ResponseEntity.ok(Map.of(
                "message", "Mot de passe réinitialisé avec succès"
        ));
    }
}
