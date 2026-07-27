package rfid.example.RFID.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CardRequest {

    @NotBlank(message = "Card UID is required")
    private String cardUid;

    private String status; // AVAILABLE, ASSIGNED, LOST, DEACTIVATED
}
