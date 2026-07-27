package rfid.example.RFID.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TapRequest {

    @NotBlank(message = "Card UID is required")
    private String cardUid;

    @NotBlank(message = "Direction is required (IN/OUT)")
    @jakarta.validation.constraints.Pattern(regexp = "^(IN|OUT)$", message = "Direction must be IN or OUT")
    private String direction;

    private String readerId; // optional
}
