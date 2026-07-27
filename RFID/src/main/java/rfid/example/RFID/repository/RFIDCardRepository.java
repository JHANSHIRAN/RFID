package rfid.example.RFID.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rfid.example.RFID.model.RFIDCard;

import java.util.Optional;

@Repository
public interface RFIDCardRepository extends JpaRepository<RFIDCard, Long> {
    Optional<RFIDCard> findByCardUid(String cardUid);
    boolean existsByCardUid(String cardUid);
}
