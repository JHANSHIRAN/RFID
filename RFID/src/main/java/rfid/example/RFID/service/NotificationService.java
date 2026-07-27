package rfid.example.RFID.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneId;
import rfid.example.RFID.model.Notification;
import rfid.example.RFID.repository.NotificationRepository;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    @Autowired(required = false)
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String adminEmail;

    @Autowired
    private NotificationRepository notificationRepository;

    public void sendStaffAccountCreatedNotification(String email, String tempPassword) {
        logger.info("====================================================================");
        logger.info("EMAIL NOTIFICATION: Staff Account Created");
        logger.info("Recipient: {}", email);
        logger.info("Subject: Your AccessTrack Staff Account Credentials");
        logger.info("Body: Welcome to ZenCube AccessTrack. Your temporary password is: {}", tempPassword);
        logger.info("====================================================================");
        
        try {
            if (mailSender != null) {
                org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Your AccessTrack Staff Account Credentials");
                message.setText("Welcome to ZenCube AccessTrack.\n\nYour temporary password is: " + tempPassword + "\n\nYou will be required to change this password on your first login.");
                mailSender.send(message);
                logger.info("Successfully sent real email to {}", email);
            }
        } catch (Exception e) {
            logger.warn("Failed to send real email. Check SMTP configuration. Reason: {}", e.getMessage());
        }
    }

    public void sendPasswordResetNotification(String email) {
        logger.info("====================================================================");
        logger.info("EMAIL NOTIFICATION: Password Reset Required");
        logger.info("Recipient: {}", email);
        logger.info("Subject: Reset Your AccessTrack Password");
        logger.info("Body: A password reset has been triggered for your account. Please log in and update your password.");
        logger.info("====================================================================");
    }

    public void sendAccountReactivatedNotification(String email) {
        logger.info("====================================================================");
        logger.info("EMAIL NOTIFICATION: Staff Account Reactivated");
        logger.info("Recipient: {}", email);
        logger.info("Subject: Your AccessTrack Staff Account is Reactivated");
        logger.info("Body: Your ZenCube AccessTrack account has been successfully reactivated. You may now log in.");
        logger.info("====================================================================");
        
        try {
            if (mailSender != null) {
                org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
                message.setTo(email);
                message.setSubject("Your AccessTrack Staff Account is Reactivated");
                message.setText("Hello,\n\nYour ZenCube AccessTrack account has been successfully reactivated by an Administrator.\n\nYou may now log in using your existing credentials.");
                mailSender.send(message);
                logger.info("Successfully sent reactivation real email to {}", email);
            }
        } catch (Exception e) {
            logger.warn("Failed to send reactivation email. Check SMTP configuration. Reason: {}", e.getMessage());
        }
    }

    public void sendCardReportedLostNotification(String cardUid, String personName) {
        String msg = "ALERT: Card UID " + cardUid + " belonging to " + personName + " marked as LOST. Active mappings released.";
        logger.info("IN-APP NOTIFICATION: {}", msg);
        
        Notification notification = Notification.builder()
                .message(msg)
                .type("ALERT")
                .isRead(false)
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .build();
        notificationRepository.save(notification);
    }

    public void sendDailyDigestNotification(List<String> lateArrivals, List<String> absentees) {
        logger.info("====================================================================");
        logger.info("EMAIL NOTIFICATION: Daily Late-Arrival & Absentee Digest");
        logger.info("Recipient: Manager + Admin ({})", adminEmail);
        logger.info("Subject: Daily Attendance Digest");
        logger.info("Body: Late Arrivals: {} | Absentees: {}", lateArrivals, absentees);
        logger.info("====================================================================");
        
        try {
            if (mailSender != null) {
                org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
                message.setTo(adminEmail);
                message.setSubject("Daily Attendance Digest");
                
                StringBuilder emailBody = new StringBuilder("Hello Admin/Manager,\n\nHere is the daily attendance digest:\n\n");
                
                emailBody.append("Absentees:\n");
                if (absentees.isEmpty()) {
                    emailBody.append("- None\n");
                } else {
                    absentees.forEach(a -> emailBody.append("- ").append(a).append("\n"));
                }
                
                emailBody.append("\nLate Arrivals:\n");
                if (lateArrivals.isEmpty()) {
                    emailBody.append("- None\n");
                } else {
                    lateArrivals.forEach(l -> emailBody.append("- ").append(l).append("\n"));
                }
                
                emailBody.append("\n\nRegards,\nAccessTrack System");
                message.setText(emailBody.toString());
                
                mailSender.send(message);
                logger.info("Successfully sent daily digest real email to {}", adminEmail);
            }
        } catch (Exception e) {
            logger.warn("Failed to send daily digest email. Reason: {}", e.getMessage());
        }
    }

    public void sendAutoCheckoutSummaryNotification(int missedCheckoutsCount) {
        logger.info("====================================================================");
        logger.info("IN-APP NOTIFICATION: Auto-Checkout Summary");
        logger.info("Recipient: Manager");
        logger.info("Subject: Daily Auto-Checkout Execution Report");
        logger.info("Body: Auto-checkout execution completed. Total missed check-outs auto-closed: {}", missedCheckoutsCount);
        logger.info("====================================================================");
    }

    public void sendRepeatedDeniedTapsNotification(String cardUid, int attempts) {
        logger.info("====================================================================");
        logger.info("IN-APP NOTIFICATION: Security Alert");
        logger.info("Recipient: Manager");
        logger.info("Subject: Repeated Denied Taps Detected");
        logger.info("Body: Card UID {} has generated {} denied taps in near-real-time. Possible misuse detected.", cardUid, attempts);
        logger.info("====================================================================");
    }
}
