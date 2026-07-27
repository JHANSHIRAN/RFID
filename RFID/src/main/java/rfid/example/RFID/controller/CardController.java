package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.CardRequest;
import rfid.example.RFID.model.CardMapping;
import rfid.example.RFID.model.CardStatus;
import rfid.example.RFID.model.MappingStatus;
import rfid.example.RFID.model.RFIDCard;
import rfid.example.RFID.repository.CardMappingRepository;
import rfid.example.RFID.repository.RFIDCardRepository;
import rfid.example.RFID.service.AuditLogService;
import rfid.example.RFID.service.NotificationService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/cards")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CardController {

    @Autowired
    private RFIDCardRepository rfidCardRepository;

    @Autowired
    private CardMappingRepository cardMappingRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<List<RFIDCard>> getAllCards() {
        return ApiResponse.success(rfidCardRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<RFIDCard> registerCard(@Valid @RequestBody CardRequest request) {
        if (rfidCardRepository.existsByCardUid(request.getCardUid())) {
            throw new RuntimeException("Error: Card UID already exists in inventory!");
        }

        RFIDCard card = RFIDCard.builder()
                .cardUid(request.getCardUid())
                .status(CardStatus.AVAILABLE)
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .build();

        rfidCardRepository.save(card);

        auditLogService.log("CARD_CREATION", "CARD", card.getId().toString(), null);

        return ApiResponse.success(card);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<RFIDCard> updateCardStatus(@PathVariable Long id, @RequestBody CardRequest request) {
        RFIDCard card = rfidCardRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Card not found!"));

        if (request.getStatus() != null) {
            CardStatus status = CardStatus.valueOf(request.getStatus().toUpperCase());
            
            if (card.getStatus() == CardStatus.LOST && status == CardStatus.AVAILABLE) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                if (!isAdmin) {
                    throw new RuntimeException("Error: Only an Admin can return a LOST card to AVAILABLE.");
                }
            }
            
            card.setStatus(status);

            // Lost card: staff marks card LOST -> active mapping is RELEASED
            if (status == CardStatus.LOST || status == CardStatus.DEACTIVATED) {
                Optional<CardMapping> mappingOpt = cardMappingRepository.findByCardIdAndStatus(card.getId(), MappingStatus.ACTIVE);
                String personName = "Unknown/No Active Mapping";
                if (mappingOpt.isPresent()) {
                    CardMapping mapping = mappingOpt.get();
                    mapping.setStatus(MappingStatus.RELEASED);
                    mapping.setReleasedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
                    cardMappingRepository.save(mapping);
                    personName = mapping.getPerson().getFullName();
                    auditLogService.log("MAPPING_RELEASE", "MAPPING", mapping.getId().toString(), "Card marked " + status);
                }
                if (status == CardStatus.LOST) {
                    notificationService.sendCardReportedLostNotification(card.getCardUid(), personName);
                }
            }
        }

        rfidCardRepository.save(card);

        String details = request.getStatus() != null ? "Status changed to " + request.getStatus().toUpperCase() : null;
        auditLogService.log("CARD_STATUS_UPDATE", "CARD", card.getId().toString(), details);

        return ApiResponse.success(card);
    }
    @PutMapping("/{id}/mark-lost")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<RFIDCard> markCardLost(@PathVariable Long id) {
        CardRequest req = new CardRequest();
        req.setStatus("LOST");
        return updateCardStatus(id, req);
    }
}
