package rfid.example.RFID.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rfid.example.RFID.dto.*;
import rfid.example.RFID.model.Role;
import rfid.example.RFID.model.User;
import rfid.example.RFID.model.AuditLog;
import rfid.example.RFID.repository.UserRepository;
import rfid.example.RFID.security.JwtUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@Transactional
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private NotificationService notificationService;

    private User getCurrentAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    public String registerUser(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Error: Email is already taken!");
        }

        User currentUser = getCurrentAuthenticatedUser();
        Role targetRole;

        try {
            targetRole = Role.valueOf(registerRequest.getRole().toUpperCase());
        } catch (Exception e) {
            targetRole = Role.OPERATOR;
        }

        // RBAC Enforcements:
        // A Manager can only create OPERATOR users.
        // Only an Admin can create MANAGER or ADMIN users.
        // Operators cannot create anyone.
        if (currentUser != null) {
            if (currentUser.getRole() == Role.OPERATOR) {
                throw new RuntimeException("Error: Operators cannot create staff users.");
            } else if (currentUser.getRole() == Role.MANAGER && targetRole != Role.OPERATOR) {
                throw new RuntimeException("Error: Managers can only create Operator users.");
            }
        }

        String rawPassword = registerRequest.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = java.util.UUID.randomUUID().toString().substring(0, 8);
        }

        User user = User.builder()
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(rawPassword))
                .role(targetRole)
                .addedBy(currentUser)
                .passwordChangeRequired(true)
                .active(true)
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .build();

        userRepository.save(user);

        auditLogService.log("STAFF_CREATION", "USER", user.getId().toString(), null);

        notificationService.sendStaffAccountCreatedNotification(user.getEmail(), rawPassword);

        return "User registered successfully!";
    }

    public AuthResponse authenticateUser(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(() -> new RuntimeException("Error: Invalid credentials!"));

        if (!user.isActive()) {
            throw new RuntimeException("Error: This account has been deactivated.");
        }

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Error: Invalid credentials!");
        }

        String jwt = jwtUtils.generateToken(user.getEmail(), user.getRole().name());

        user.setLastLoginAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        userRepository.save(user);

        return AuthResponse.builder()
                .token(jwt)
                .email(user.getEmail())
                .role(user.getRole().name())
                .passwordChangeRequired(user.isPasswordChangeRequired())
                .build();
    }

    public String changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Error: User not found!"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Error: Invalid old password!");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangeRequired(false);
        userRepository.save(user);

        auditLogService.log("PASSWORD_CHANGE", "USER", user.getId().toString(), null);

        return "Password changed successfully!";
    }

    public List<User> getAllStaffUsers() {
        return userRepository.findAll();
    }

    public User getStaffUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Staff user not found!"));
    }

    public User updateStaffUser(Long id, StaffUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Staff user not found!"));

        User currentUser = getCurrentAuthenticatedUser();
        if (currentUser == null || (currentUser.getRole() != Role.ADMIN && currentUser.getRole() != Role.MANAGER)) {
            throw new RuntimeException("Error: Only Administrators and Managers can update staff users.");
        }

        if (currentUser.getRole() == Role.MANAGER && user.getRole() != Role.OPERATOR) {
            throw new RuntimeException("Error: Managers can only update Operator users.");
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new RuntimeException("Error: Email is already taken!");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getRole() != null) {
            try {
                user.setRole(Role.valueOf(request.getRole().toUpperCase()));
            } catch (Exception e) {
                throw new RuntimeException("Error: Invalid role specified.");
            }
        }

        if (request.getActive() != null) {
            if (currentUser.getRole() != Role.ADMIN) {
                throw new RuntimeException("Error: Only Administrators can deactivate or reactivate staff users.");
            }
            boolean wasInactive = !user.isActive();
            user.setActive(request.getActive());
            
            if (wasInactive && user.isActive()) {
                notificationService.sendAccountReactivatedNotification(user.getEmail());
            }
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setPasswordChangeRequired(true);
        }

        userRepository.save(user);

        auditLogService.log("STAFF_UPDATE", "USER", user.getId().toString(), null);

        return user;
    }

    // Seeder helper to register the first seed Admin
    public void seedAdmin() {
        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .email("admin@zencube.com")
                    .password(passwordEncoder.encode("adminpassword123"))
                    .role(Role.ADMIN)
                    .addedBy(null)
                    .passwordChangeRequired(true)
                    .active(true)
                    .createdAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                    .build();
            userRepository.save(admin);
            
            // Seed audit event
            AuditLog log = AuditLog.builder()
                    .actor(null)
                    .actorRole(null)
                    .actionType("SYSTEM_SEED")
                    .targetEntity("USER")
                    .targetId(admin.getId().toString())
                    .timestamp(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                    .ipAddress("SYSTEM")
                    .build();
            // Note: We bypass auditLogRepository circular reference during startup if we seed directly
            userRepository.save(admin);
        }
    }
}
