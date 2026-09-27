package tg.pnlp.planning.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tg.pnlp.planning.dto.LoginRequest;
import tg.pnlp.planning.dto.LoginResponse;
import tg.pnlp.planning.dto.UtilisateurDTO;
import tg.pnlp.planning.security.JwtService;
import tg.pnlp.planning.service.AuthService;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Login / Logout")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/login")
    @Operation(summary = "Connexion utilisateur")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Obtenir l'utilisateur connecté")
    public ResponseEntity<UtilisateurDTO> getCurrentUser(HttpServletRequest request) {
        String token = extraireToken(request);
        UUID userId = jwtService.extraireUserId(token);
        return ResponseEntity.ok(authService.getCurrentUser(userId));
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion (côté client)")
    public ResponseEntity<Void> logout() {
        // Le logout se fait côté client en supprimant le token
        return ResponseEntity.noContent().build();
    }

    private String extraireToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}