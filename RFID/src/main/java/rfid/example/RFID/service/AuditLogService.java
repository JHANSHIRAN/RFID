package rfid.example.RFID.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rfid.example.RFID.model.AuditLog;
import rfid.example.RFID.model.User;
import rfid.example.RFID.repository.AuditLogRepository;
import rfid.example.RFID.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@Transactional
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    public void log(String actionType, String targetEntity, String targetId, String ipAddress) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User actor = null;
        
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();
            actor = userRepository.findByEmail(email).orElse(null);
        }

        AuditLog log = AuditLog.builder()
                .actor(actor)
                .actorRole(actor != null ? actor.getRole() : null)
                .actionType(actionType)
                .targetEntity(targetEntity)
                .targetId(targetId)
                .timestamp(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(log);
    }
}
