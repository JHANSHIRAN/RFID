package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.AuthResponse;
import rfid.example.RFID.dto.ChangePasswordRequest;
import rfid.example.RFID.dto.LoginRequest;
import rfid.example.RFID.service.UserService;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse response = userService.authenticateUser(loginRequest);
        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout() {
        // Since JWT is stateless, client deletes token. Server just returns success.
        return ApiResponse.success(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/auth/change-password")
    public ApiResponse<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User is not authenticated");
        }
        String message = userService.changePassword(principal.getName(), request);
        return ApiResponse.success(Map.of("message", message));
    }
}
