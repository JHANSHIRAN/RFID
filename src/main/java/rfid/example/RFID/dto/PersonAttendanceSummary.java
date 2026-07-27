package rfid.example.RFID.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonAttendanceSummary {
    private Long personId;
    private String studentId;
    private String fullName;
    private String memberType;
    private String groupLabel;

    /** Number of working days in the requested range (Mon–Fri). */
    private int workingDaysInRange;

    /** Days the person had at least one CLOSED session. */
    private int daysPresent;

    /** Days where meetsMinimumHours = true (cumulative hours met the configured threshold). */
    private int daysMetMinimumHours;

    /** Derived: workingDaysInRange - daysPresent. */
    private int daysAbsent;

    /** Total cumulative minutes across all CLOSED sessions. */
    private int totalMinutes;

    /** Convenience: totalMinutes / 60.0 */
    private double totalHours;

    /** Distinct work-dates on which the first session was LATE. */
    private int lateCount;

    /**
     * Sessions where checkOutAt IS NULL but status is CLOSED/ABSENT
     * (auto-checked-out or marked absent without a real tap-out).
     * These indicate a forgotten checkout.
     */
    private int missedCheckOuts;

    /** The expected number of working hours in this range. */
    private int expectedHours;

    /** Attendance percentage based on total hours present vs expected hours. */
    private double attendancePct;
}
