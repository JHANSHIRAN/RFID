package rfid.example.RFID.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rfid.example.RFID.model.Person;

import java.util.Optional;
import java.util.List;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
    Optional<Person> findByExternalRef(String externalRef);
    boolean existsByExternalRef(String externalRef);
    List<Person> findByStatus(rfid.example.RFID.model.PersonStatus status);
}
