package rfid.example.RFID.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rfid.example.RFID.model.Notification;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByOrderByCreatedAtDesc();
    List<Notification> findByIsReadFalseOrderByCreatedAtDesc();
    long countByIsReadFalse();
}
