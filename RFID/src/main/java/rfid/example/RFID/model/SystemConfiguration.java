package rfid.example.RFID.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Entity
@Table(name = "system_configurations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expected_start_time", nullable = false)
    private LocalTime expectedStartTime;

    @Column(name = "late_grace_minutes", nullable = false)
    private int lateGraceMinutes;

    @Column(name = "auto_checkout_time")
    private LocalTime autoCheckoutTime; // Retained for schema compatibility but no longer used for auto-checkout

    @Column(name = "working_days", nullable = false)
    private String workingDays; // Comma separated DayOfWeek names, e.g., "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"

    @Column(name = "tap_debounce_seconds", nullable = false)
    private int tapDebounceSeconds;

    @Column(name = "minimum_hours_required")
    private Integer minimumHoursRequired; // Minimum hours an employee must spend to count as present (default: 8)

    public int getMinimumHoursRequired() {
        return minimumHoursRequired != null ? minimumHoursRequired : 8;
    }
}
