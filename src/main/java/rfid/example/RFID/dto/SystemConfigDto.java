package rfid.example.RFID.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Data
public class SystemConfigDto {

    @NotBlank(message = "Expected start time is required (e.g., '09:30')")
    private String expectedStartTime;

    @Min(value = 0, message = "Grace minutes must be at least 0")
    private int lateGraceMinutes;

    @NotBlank(message = "Auto checkout time is required (e.g., '20:00')")
    private String autoCheckoutTime;

    @NotNull(message = "Working days are required")
    private Set<String> workingDays; // e.g. ["MONDAY", "TUESDAY", ...]

    @Min(value = 0, message = "Tap debounce seconds must be at least 0")
    private int tapDebounceSeconds;

    @Min(value = 1, message = "Minimum hours required must be at least 1")
    private int minimumHoursRequired;
}
