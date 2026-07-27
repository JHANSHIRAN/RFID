package rfid.example.RFID.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.model.Notification;
import rfid.example.RFID.repository.NotificationRepository;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*", maxAge = 3600)
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<List<Notification>> getNotifications() {
        return ApiResponse.success(notificationRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping("/unread/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Long> getUnreadCount() {
        return ApiResponse.success(notificationRepository.countByIsReadFalse());
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Notification> markAsRead(@PathVariable Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        return ApiResponse.success(notificationRepository.save(notification));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<String> markAllAsRead() {
        List<Notification> unread = notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();
        for (Notification n : unread) {
            n.setRead(true);
        }
        notificationRepository.saveAll(unread);
        return ApiResponse.success("All marked as read");
    }
}
