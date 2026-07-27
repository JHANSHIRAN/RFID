package rfid.example.RFID.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rfid.example.RFID.model.CardMapping;
import rfid.example.RFID.model.MappingStatus;

import java.util.Optional;

@Repository
public interface CardMappingRepository extends JpaRepository<CardMapping, Long> {
    Optional<CardMapping> findByPersonIdAndStatus(Long personId, MappingStatus status);
    Optional<CardMapping> findByCardIdAndStatus(Long cardId, MappingStatus status);
    Optional<CardMapping> findByCardCardUidAndStatus(String cardUid, MappingStatus status);
}
