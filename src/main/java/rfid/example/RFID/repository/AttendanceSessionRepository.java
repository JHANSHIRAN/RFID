package rfid.example.RFID.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rfid.example.RFID.model.AttendanceSession;
import rfid.example.RFID.model.SessionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findByPersonIdAndStatus(Long personId, SessionStatus status);
    List<AttendanceSession> findByPersonIdAndWorkDateBetween(Long personId, LocalDate start, LocalDate end);
    List<AttendanceSession> findByWorkDateBetween(LocalDate start, LocalDate end);
    List<AttendanceSession> findByStatus(SessionStatus status);
}
