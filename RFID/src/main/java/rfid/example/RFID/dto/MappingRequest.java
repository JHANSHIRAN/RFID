package rfid.example.RFID.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MappingRequest {

    @NotBlank(message = "Card UID is required")
    private String cardUid;

    @NotNull(message = "Person ID is required")
    private Long personId;
}
