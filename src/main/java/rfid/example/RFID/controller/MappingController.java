package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.MappingRequest;
import rfid.example.RFID.model.*;
import rfid.example.RFID.repository.CardMappingRepository;
import rfid.example.RFID.repository.PersonRepository;
import rfid.example.RFID.repository.RFIDCardRepository;
import rfid.example.RFID.service.AuditLogService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/mappings")
@CrossOrigin(origins = "*", maxAge = 3600)
public class MappingController {

    @Autowired
    private CardMappingRepository cardMappingRepository;

    @Autowired
    private RFIDCardRepository rfidCardRepository;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<List<CardMapping>> getAllMappings() {
        return ApiResponse.success(cardMappingRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<CardMapping> createMapping(@Valid @RequestBody MappingRequest request) {
        RFIDCard card = rfidCardRepository.findByCardUid(request.getCardUid())
                .orElseThrow(() -> new RuntimeException("Error: Card not found in inventory!"));

        Person person = personRepository.findById(request.getPersonId())
                .orElseThrow(() -> new RuntimeException("Error: Person not found!"));

        // Card must be AVAILABLE
        if (card.getStatus() != CardStatus.AVAILABLE) {
            throw new RuntimeException("Error: Mapping fails because card is not AVAILABLE. Card is currently " + card.getStatus());
        }

        // Person must not have another active mapping.
        Optional<CardMapping> existingPersonMapping = cardMappingRepository.findByPersonIdAndStatus(person.getId(), MappingStatus.ACTIVE);
        if (existingPersonMapping.isPresent()) {
            throw new RuntimeException("Error: Person already has an active card. The current card must be marked as LOST before a new card can be assigned.");
        }

        // Create new active mapping
        CardMapping mapping = CardMapping.builder()
                .card(card)
                .person(person)
                .status(MappingStatus.ACTIVE)
                .assignedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .build();

        cardMappingRepository.save(mapping);

        // Update card status to ASSIGNED
        card.setStatus(CardStatus.ASSIGNED);
        rfidCardRepository.save(card);

        auditLogService.log("MAPPING_CREATE", "MAPPING", mapping.getId().toString(), null);

        return ApiResponse.success(mapping);
    }

    @PatchMapping("/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<Map<String, String>> releaseMapping(@PathVariable Long id) {
        CardMapping mapping = cardMappingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Mapping not found!"));

        if (mapping.getStatus() == MappingStatus.ACTIVE) {
            mapping.setStatus(MappingStatus.RELEASED);
            mapping.setReleasedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
            cardMappingRepository.save(mapping);

            RFIDCard card = mapping.getCard();
            if (card.getStatus() != CardStatus.LOST) {
                card.setStatus(CardStatus.AVAILABLE);
            }
            rfidCardRepository.save(card);

            auditLogService.log("MAPPING_RELEASE", "MAPPING", mapping.getId().toString(), "Manual release");
        }

        return ApiResponse.success(Map.of("message", "Mapping released successfully"));
    }
}
