package rfid.example.RFID.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rfid.example.RFID.model.AttendanceEvent;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, Long> {
    List<AttendanceEvent> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime start, LocalDateTime end);
    List<AttendanceEvent> findByPersonIdOrderByOccurredAtDesc(Long personId);
    List<AttendanceEvent> findByCardUidAndOccurredAtAfterOrderByOccurredAtDesc(String cardUid, LocalDateTime after);
}
