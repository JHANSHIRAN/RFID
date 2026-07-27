package rfid.example.RFID.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CorrectionRequest {

    @NotNull(message = "Session ID is required")
    private Long sessionId;

    private LocalDateTime checkInAt;
    
    private LocalDateTime checkOutAt;

    @NotNull(message = "Reason for adjustment is required")
    private String reason;
}
