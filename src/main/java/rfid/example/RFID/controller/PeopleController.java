package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.PersonRequest;
import rfid.example.RFID.model.AttendanceSession;
import rfid.example.RFID.model.CardMapping;
import rfid.example.RFID.model.CardStatus;
import rfid.example.RFID.model.MappingStatus;
import rfid.example.RFID.model.Person;
import rfid.example.RFID.model.PersonStatus;
import rfid.example.RFID.model.RFIDCard;
import rfid.example.RFID.repository.CardMappingRepository;
import rfid.example.RFID.repository.PersonRepository;
import rfid.example.RFID.repository.RFIDCardRepository;
import rfid.example.RFID.service.AttendanceService;
import rfid.example.RFID.service.AuditLogService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/people")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PeopleController {

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private CardMappingRepository cardMappingRepository;

    @Autowired
    private RFIDCardRepository rfidCardRepository;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AuditLogService auditLogService;

    private boolean isOperatorOnly() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"))
                && auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
    }

    private Person maskContactInfoIfOperator(Person p) {
        if (isOperatorOnly()) {
            p.setEmail(null);
            p.setPhone(null);
        }
        return p;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<List<Person>> getAllPeople(@RequestParam(required = false) String status) {
        List<Person> list = personRepository.findAll();
        if (status != null && !status.isBlank()) {
            PersonStatus ps = PersonStatus.valueOf(status.toUpperCase());
            list = list.stream().filter(p -> p.getStatus() == ps).collect(Collectors.toList());
        }
        list = list.stream()
                .map(p -> {
                    p = maskContactInfoIfOperator(p);
                    Optional<CardMapping> mappingOpt = cardMappingRepository.findByPersonIdAndStatus(p.getId(), MappingStatus.ACTIVE);
                    p.setAssignedCardUid(mappingOpt.map(m -> m.getCard().getCardUid()).orElse(null));
                    return p;
                })
                .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<Person> getPersonById(@PathVariable Long id) {
        Person p = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Person not found!"));
        return ApiResponse.success(maskContactInfoIfOperator(p));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<Person> registerPerson(@Valid @RequestBody PersonRequest request) {
        if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
            if (personRepository.existsByExternalRef(request.getStudentId())) {
                throw new RuntimeException("Error: Reference ID already exists!");
            }
        }

        Person p = Person.builder()
                .fullName(request.getFullName())
                .memberType(request.getMemberType())
                .externalRef(request.getStudentId())
                .groupLabel(request.getGroupLabel())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(PersonStatus.ACTIVE)
                .createdAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .build();

        personRepository.save(p);

        auditLogService.log("PERSON_REGISTRATION", "PERSON", p.getId().toString(), null);

        return ApiResponse.success(maskContactInfoIfOperator(p));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Person> updatePerson(@PathVariable Long id, @RequestBody PersonRequest request) {
        Person p = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Person not found!"));

        if (request.getFullName() != null) p.setFullName(request.getFullName());
        if (request.getMemberType() != null) p.setMemberType(request.getMemberType());
        
        if (request.getStudentId() != null && !request.getStudentId().isBlank() && !request.getStudentId().equals(p.getExternalRef())) {
            if (personRepository.existsByExternalRef(request.getStudentId())) {
                throw new RuntimeException("Error: Reference ID already exists!");
            }
            p.setExternalRef(request.getStudentId());
        }
        
        if (request.getGroupLabel() != null) p.setGroupLabel(request.getGroupLabel());
        if (request.getEmail() != null) p.setEmail(request.getEmail());
        if (request.getPhone() != null) p.setPhone(request.getPhone());
        
        if (request.getStatus() != null) {
            PersonStatus newStatus = PersonStatus.valueOf(request.getStatus().toUpperCase());
            p.setStatus(newStatus);
            if (newStatus == PersonStatus.INACTIVE) {
                Optional<CardMapping> mappingOpt = cardMappingRepository.findByPersonIdAndStatus(p.getId(), MappingStatus.ACTIVE);
                if (mappingOpt.isPresent()) {
                    CardMapping mapping = mappingOpt.get();
                    mapping.setStatus(MappingStatus.RELEASED);
                    mapping.setReleasedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
                    cardMappingRepository.save(mapping);

                    RFIDCard card = mapping.getCard();
                    if (card.getStatus() != CardStatus.LOST) {
                        card.setStatus(CardStatus.AVAILABLE);
                    }
                    rfidCardRepository.save(card);

                    auditLogService.log("MAPPING_RELEASE", "MAPPING", mapping.getId().toString(), "Person deactivation");
                }
            }
        }

        personRepository.save(p);

        auditLogService.log("PERSON_UPDATE", "PERSON", p.getId().toString(), null);

        return ApiResponse.success(p);
    }
    @PutMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Person> togglePersonStatus(@PathVariable Long id) {
        Person p = personRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Error: Person not found!"));
        
        PersonRequest req = new PersonRequest();
        if (p.getStatus() == PersonStatus.ACTIVE) {
            req.setStatus("INACTIVE");
        } else {
            req.setStatus("ACTIVE");
        }
        return updatePerson(id, req);
    }

    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<List<AttendanceSession>> getPersonAttendance(@PathVariable Long id) {
        LocalDate start = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusYears(2);
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1);
        List<AttendanceSession> sessions = attendanceService.getAttendanceReport(start, end, id, null);
        return ApiResponse.success(sessions);
    }
}
