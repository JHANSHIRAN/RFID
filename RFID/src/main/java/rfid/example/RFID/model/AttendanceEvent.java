package rfid.example.RFID.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_events")
@Immutable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_uid", nullable = false, updatable = false)
    private String cardUid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", updatable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccessDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", updatable = false)
    private AttendanceEventType eventType;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TapSource source;
}
