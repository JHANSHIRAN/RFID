package rfid.example.RFID.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Immutable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", updatable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", updatable = false)
    private Role actorRole;

    @Column(name = "action_type", nullable = false, updatable = false)
    private String actionType;

    @Column(name = "target_entity", nullable = false, updatable = false)
    private String targetEntity;

    @Column(name = "target_id", nullable = false, updatable = false)
    private String targetId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;
}
