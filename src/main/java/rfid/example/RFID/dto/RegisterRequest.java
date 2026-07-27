package rfid.example.RFID.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @Size(min = 6, max = 100, message = "Password must be at least 6 characters long")
    private String password;

    private String role; // ADMIN, MANAGER, OPERATOR
}
