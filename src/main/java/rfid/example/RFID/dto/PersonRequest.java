package rfid.example.RFID.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import rfid.example.RFID.model.MemberType;

@Data
public class PersonRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotNull(message = "Member type is required (STUDENT, EMPLOYEE, or GUEST)")
    private MemberType memberType;

    private String studentId;

    @NotBlank(message = "Group label is required")
    private String groupLabel;

    private String email;
    private String phone;
    
    private String status; // ACTIVE, INACTIVE
}
