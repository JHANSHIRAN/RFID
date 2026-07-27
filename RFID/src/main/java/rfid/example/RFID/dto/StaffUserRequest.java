package rfid.example.RFID.dto;

import lombok.Data;

@Data
public class StaffUserRequest {
    private String email;
    private String role; // ADMIN, MANAGER, OPERATOR
    private Boolean active;
    private String password;
}
