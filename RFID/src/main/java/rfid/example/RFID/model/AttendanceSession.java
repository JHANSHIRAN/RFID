package rfid.example.RFID.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "check_in_at", nullable = false)
    private LocalDateTime checkInAt;

    @Column(name = "check_out_at")
    private LocalDateTime checkOutAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "is_late", nullable = false)
    private boolean isLate;

    @Column(name = "meets_minimum_hours")
    private Boolean meetsMinimumHours; // True if duration >= system configured minimum hours (default: 8h)

    public boolean isMeetsMinimumHours() {
        return meetsMinimumHours != null ? meetsMinimumHours : false;
    }

    @Transient
    public Double getDurationHours() {
        if (durationMinutes == null) return null;
        return Math.round((durationMinutes / 60.0) * 100.0) / 100.0;
    }
}
