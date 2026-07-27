package rfid.example.RFID.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rfid.example.RFID.dto.CorrectionRequest;
import rfid.example.RFID.dto.PersonAttendanceSummary;
import rfid.example.RFID.dto.SystemConfigDto;
import rfid.example.RFID.model.*;
import rfid.example.RFID.repository.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AttendanceService {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RFIDCardRepository rfidCardRepository;

    @Autowired
    private CardMappingRepository cardMappingRepository;

    @Autowired
    private AttendanceEventRepository attendanceEventRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    @Autowired
    private SystemConfigurationRepository systemConfigurationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private NotificationService notificationService;

    // Fetch the active system settings
    private SystemConfiguration getConfiguration() {
        return systemConfigurationRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    SystemConfiguration config = SystemConfiguration.builder()
                            .expectedStartTime(LocalTime.of(9, 30))
                            .lateGraceMinutes(0)
                            .autoCheckoutTime(LocalTime.of(21, 0))
                            .workingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
                            .tapDebounceSeconds(10)
                            .minimumHoursRequired(8)
                            .build();
                    return systemConfigurationRepository.save(config);
                });
    }

    // ─── Tap Processing (Heart of the System) ──────────────────────────────────
    public AttendanceEvent processTap(String cardUid, String direction, String sourceStr) {
        LocalDateTime now = LocalDateTime.now(IST_ZONE);
        TapSource source = "DEVICE".equalsIgnoreCase(sourceStr) ? TapSource.DEVICE : TapSource.SIMULATED;
        SystemConfiguration config = getConfiguration();

        // 1. Debounce Check: Ignore repeat taps of the same card within window
        LocalDateTime debounceLimit = now.minusSeconds(config.getTapDebounceSeconds());
        List<AttendanceEvent> recentEvents = attendanceEventRepository
                .findByCardUidAndOccurredAtAfterOrderByOccurredAtDesc(cardUid, debounceLimit);

        if (!recentEvents.isEmpty()) {
            // Log a DENIED event for debounce and return
            return saveDeniedEvent(cardUid, null, "DEBOUNCED", now, source);
        }

        // 2. Card Resolution
        Optional<RFIDCard> cardOpt = rfidCardRepository.findByCardUid(cardUid);
        if (cardOpt.isEmpty()) {
            return saveDeniedEvent(cardUid, null, "UNKNOWN_CARD", now, source);
        }

        RFIDCard card = cardOpt.get();

        // 3. Mapping Resolution
        Optional<CardMapping> mappingOpt = cardMappingRepository.findByCardIdAndStatus(card.getId(), MappingStatus.ACTIVE);
        if (mappingOpt.isEmpty()) {
            return saveDeniedEvent(cardUid, null, "NO_MAPPING", now, source);
        }

        CardMapping mapping = mappingOpt.get();
        Person person = mapping.getPerson();

        // 4. Access Decision Rules (Deny lost, deactivated cards, or inactive people)
        if (card.getStatus() == CardStatus.LOST) {
            return saveDeniedEvent(cardUid, person, "CARD_LOST", now, source);
        }
        if (card.getStatus() == CardStatus.DEACTIVATED) {
            return saveDeniedEvent(cardUid, person, "CARD_DEACTIVATED", now, source);
        }
        if (person.getStatus() == PersonStatus.INACTIVE) {
            return saveDeniedEvent(cardUid, person, "PERSON_INACTIVE", now, source);
        }

        // 5. Attendance Transition with Direction Enforcement
        Optional<AttendanceSession> openSessionOpt = attendanceSessionRepository
                .findByPersonIdAndStatus(person.getId(), SessionStatus.OPEN);

        AttendanceEventType eventType;
        AttendanceSession session;
        LocalDate workDate = LocalDate.now(IST_ZONE);

        if ("IN".equalsIgnoreCase(direction)) {
            if (openSessionOpt.isPresent()) {
                // Redundant check-in: they are already checked in.
                return saveDeniedEvent(cardUid, person, "ALREADY_CHECKED_IN", now, source);
            }

            // CHECK_IN: Open new session
            eventType = AttendanceEventType.CHECK_IN;

            // Check if late: tap time is after the exact configured expected start time + grace period
            // ONLY if this is the first check-in of the day
            boolean isLate = false;
            long todaysClosedCount = attendanceSessionRepository.findByWorkDateBetween(workDate, workDate).stream()
                    .filter(s -> s.getPerson().getId().equals(person.getId()) && s.getStatus() == SessionStatus.CLOSED)
                    .count();
            if (todaysClosedCount == 0) {
                LocalTime tapLocalTime = now.toLocalTime();
                LocalTime latestAllowedTime = config.getExpectedStartTime().plusMinutes(config.getLateGraceMinutes());
                isLate = tapLocalTime.isAfter(latestAllowedTime);
            }

            session = AttendanceSession.builder()
                    .person(person)
                    .workDate(workDate)
                    .checkInAt(now)
                    .status(SessionStatus.OPEN)
                    .isLate(isLate)
                    .meetsMinimumHours(false) // will be evaluated on checkout
                    .build();

            attendanceSessionRepository.save(session);
        } else if ("OUT".equalsIgnoreCase(direction)) {
            if (openSessionOpt.isEmpty()) {
                // Redundant check-out: they are not currently checked in.
                return saveDeniedEvent(cardUid, person, "NOT_CHECKED_IN", now, source);
            }

            session = openSessionOpt.get();

            // CROSS-DAY CHECKOUT FIX
            if (!session.getWorkDate().equals(workDate)) {
                autoCheckoutForgottenSessions(); // Auto-closes the old session
                return saveDeniedEvent(cardUid, person, "NOT_CHECKED_IN_TODAY", now, source);
            }

            // CHECK_OUT: Close open session and evaluate hours cumulatively
            eventType = AttendanceEventType.CHECK_OUT;
            session.setCheckOutAt(now);
            session.setStatus(SessionStatus.CLOSED);

            long currentSessionMinutes = ChronoUnit.MINUTES.between(session.getCheckInAt(), now);
            session.setDurationMinutes((int) currentSessionMinutes);

            // Calculate cumulative minutes for the day
            List<AttendanceSession> todaysClosedSessions = attendanceSessionRepository.findByWorkDateBetween(workDate, workDate).stream()
                    .filter(s -> s.getPerson().getId().equals(person.getId()) && s.getStatus() == SessionStatus.CLOSED && !s.getId().equals(session.getId()))
                    .collect(Collectors.toList());

            long totalPreviousMinutes = todaysClosedSessions.stream()
                    .mapToLong(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                    .sum();

            long totalDailyMinutes = totalPreviousMinutes + currentSessionMinutes;

            // Attendance is counted as present only if minimum hours are met cumulatively
            int minimumMinutes = config.getMinimumHoursRequired() * 60;
            boolean meetsMin = totalDailyMinutes >= minimumMinutes;
            session.setMeetsMinimumHours(meetsMin);

            attendanceSessionRepository.save(session);

            // Ensure all previous sessions for today share the same meetsMinimumHours flag
            if (!todaysClosedSessions.isEmpty()) {
                for (AttendanceSession prevSession : todaysClosedSessions) {
                    prevSession.setMeetsMinimumHours(meetsMin);
                    attendanceSessionRepository.save(prevSession);
                }
            }
        } else {
            return saveDeniedEvent(cardUid, person, "INVALID_DIRECTION", now, source);
        }

        AttendanceEvent event = AttendanceEvent.builder()
                .cardUid(cardUid)
                .person(person)
                .decision(AccessDecision.GRANTED)
                .eventType(eventType)
                .reason("OK")
                .occurredAt(now)
                .source(source)
                .build();

        return attendanceEventRepository.save(event);
    }

    private AttendanceEvent saveDeniedEvent(String cardUid, Person person, String reason, LocalDateTime now, TapSource source) {
        AttendanceEvent event = AttendanceEvent.builder()
                .cardUid(cardUid)
                .person(person)
                .decision(AccessDecision.DENIED)
                .eventType(null)
                .reason(reason)
                .occurredAt(now)
                .source(source)
                .build();
        AttendanceEvent saved = attendanceEventRepository.save(event);

        // Audit log the failed authorization / access attempt
        String targetId = (person != null) ? person.getId().toString() : "UNKNOWN";
        auditLogService.log("ACCESS_DENIED", "CARD", cardUid, "Reason: " + reason + " | Person ID: " + targetId);

        // Check for repeated denied taps within the last 5 minutes (near-real-time)
        LocalDateTime windowStart = now.minusMinutes(5);
        List<AttendanceEvent> eventsInWindow = attendanceEventRepository.findByCardUidAndOccurredAtAfterOrderByOccurredAtDesc(cardUid, windowStart);
        long deniedCount = eventsInWindow.stream()
                .filter(e -> e.getDecision() == AccessDecision.DENIED)
                .count();

        if (deniedCount >= 3) {
            notificationService.sendRepeatedDeniedTapsNotification(cardUid, (int) deniedCount);
        }

        return saved;
    }
    // ─── Manual Attendance ─────────────────────────────────────────────────────
    public AttendanceSession manualAttendance(Long personId, String direction) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new RuntimeException("Person not found"));
        LocalDateTime now = LocalDateTime.now(IST_ZONE);
        SystemConfiguration config = getConfiguration();
        
        Optional<AttendanceSession> openSessionOpt = attendanceSessionRepository
                .findByPersonIdAndStatus(person.getId(), SessionStatus.OPEN);
                
        AttendanceEventType eventType;
        AttendanceSession session;
        LocalDate workDate = LocalDate.now(IST_ZONE);
        
        if ("IN".equalsIgnoreCase(direction)) {
            if (openSessionOpt.isPresent()) {
                throw new RuntimeException("Already checked in");
            }
            eventType = AttendanceEventType.CHECK_IN;
            boolean isLate = false;
            long todaysClosedCount = attendanceSessionRepository.findByWorkDateBetween(workDate, workDate).stream()
                    .filter(s -> s.getPerson().getId().equals(person.getId()) && s.getStatus() == SessionStatus.CLOSED)
                    .count();
            if (todaysClosedCount == 0) {
                LocalTime tapLocalTime = now.toLocalTime();
                LocalTime latestAllowedTime = config.getExpectedStartTime().plusMinutes(config.getLateGraceMinutes());
                isLate = tapLocalTime.isAfter(latestAllowedTime);
            }

            session = AttendanceSession.builder()
                    .person(person)
                    .workDate(workDate)
                    .checkInAt(now)
                    .status(SessionStatus.OPEN)
                    .isLate(isLate)
                    .meetsMinimumHours(false)
                    .build();

            attendanceSessionRepository.save(session);
        } else if ("OUT".equalsIgnoreCase(direction)) {
            if (openSessionOpt.isEmpty()) {
                throw new RuntimeException("Not checked in");
            }
            
            session = openSessionOpt.get();

            if (!session.getWorkDate().equals(workDate)) {
                autoCheckoutForgottenSessions();
                throw new RuntimeException("Not checked in today (previous session auto-closed)");
            }
            
            eventType = AttendanceEventType.CHECK_OUT;
            session.setCheckOutAt(now);
            session.setStatus(SessionStatus.CLOSED);

            long currentSessionMinutes = ChronoUnit.MINUTES.between(session.getCheckInAt(), now);
            session.setDurationMinutes((int) currentSessionMinutes);

            List<AttendanceSession> todaysClosedSessions = attendanceSessionRepository.findByWorkDateBetween(workDate, workDate).stream()
                    .filter(s -> s.getPerson().getId().equals(person.getId()) && s.getStatus() == SessionStatus.CLOSED && !s.getId().equals(session.getId()))
                    .collect(Collectors.toList());

            long totalPreviousMinutes = todaysClosedSessions.stream()
                    .mapToLong(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                    .sum();

            long totalDailyMinutes = totalPreviousMinutes + currentSessionMinutes;

            int minimumMinutes = config.getMinimumHoursRequired() * 60;
            boolean meetsMin = totalDailyMinutes >= minimumMinutes;
            session.setMeetsMinimumHours(meetsMin);

            attendanceSessionRepository.save(session);

            if (!todaysClosedSessions.isEmpty()) {
                for (AttendanceSession prevSession : todaysClosedSessions) {
                    prevSession.setMeetsMinimumHours(meetsMin);
                    attendanceSessionRepository.save(prevSession);
                }
            }
        } else {
            throw new RuntimeException("Invalid direction");
        }
        
        AttendanceEvent event = AttendanceEvent.builder()
                .cardUid("MANUAL-" + personId)
                .person(person)
                .decision(AccessDecision.GRANTED)
                .eventType(eventType)
                .reason("MANUAL")
                .occurredAt(now)
                .source(TapSource.SIMULATED)
                .build();
        attendanceEventRepository.save(event);
        
        return session;
    }

    // ─── Startup Event & Nightly Auto-Checkout Job ─────────────────────────────
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void fixCorruptedSessions() {
        autoCheckoutForgottenSessions(); // Catch any currently OPEN forgotten sessions
        
        SystemConfiguration config = getConfiguration();
        // Fix already CLOSED sessions with duration > 16 hours (960 minutes)
        List<AttendanceSession> corrupted = attendanceSessionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED && s.getDurationMinutes() != null && s.getDurationMinutes() > 960)
                .collect(Collectors.toList());
                
        for (AttendanceSession session : corrupted) {
            LocalDateTime correctCheckout = session.getWorkDate().atTime(config.getAutoCheckoutTime());
            if (session.getCheckInAt().isAfter(correctCheckout)) {
                correctCheckout = session.getCheckInAt().plusMinutes(1);
            }
            session.setCheckOutAt(correctCheckout);
            long correctMinutes = ChronoUnit.MINUTES.between(session.getCheckInAt(), correctCheckout);
            session.setDurationMinutes((int) correctMinutes);
            
            int minimumMinutes = config.getMinimumHoursRequired() * 60;
            session.setMeetsMinimumHours(correctMinutes >= minimumMinutes);
            
            attendanceSessionRepository.save(session);
        }
    }

    // If an employee forgot to check out, their OPEN session carries over midnight.
    // This job automatically checks them out at the system's configured autoCheckoutTime.
    @Scheduled(cron = "0 1 0 * * *", zone = "Asia/Kolkata") // Runs daily at 00:01 IST (start of new day)
    public void autoCheckoutForgottenSessions() {
        LocalDate today = LocalDate.now(IST_ZONE);
        SystemConfiguration config = getConfiguration();
        List<AttendanceSession> openSessions = attendanceSessionRepository.findByStatus(SessionStatus.OPEN);
        int autoCheckoutCount = 0;
        for (AttendanceSession session : openSessions) {
            // If the open session belongs to a previous work day, the person forgot to check out
            if (session.getWorkDate().isBefore(today)) {
                LocalDateTime checkoutTime = session.getWorkDate().atTime(config.getAutoCheckoutTime());
                // If they checked in AFTER the auto checkout time, just check them out 1 min later to avoid negative duration
                if (session.getCheckInAt().isAfter(checkoutTime)) {
                    checkoutTime = session.getCheckInAt().plusMinutes(1);
                }
                session.setCheckOutAt(checkoutTime);
                session.setStatus(SessionStatus.CLOSED);

                long currentSessionMinutes = ChronoUnit.MINUTES.between(session.getCheckInAt(), checkoutTime);
                session.setDurationMinutes((int) currentSessionMinutes);

                // Re-evaluate cumulative hours for that day
                List<AttendanceSession> pastClosedSessions = attendanceSessionRepository.findByWorkDateBetween(session.getWorkDate(), session.getWorkDate()).stream()
                        .filter(s -> s.getPerson().getId().equals(session.getPerson().getId()) && s.getStatus() == SessionStatus.CLOSED && !s.getId().equals(session.getId()))
                        .collect(Collectors.toList());

                long totalPreviousMinutes = pastClosedSessions.stream()
                        .mapToLong(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                        .sum();

                long totalDailyMinutes = totalPreviousMinutes + currentSessionMinutes;

                int minimumMinutes = config.getMinimumHoursRequired() * 60;
                boolean meetsMin = totalDailyMinutes >= minimumMinutes;
                session.setMeetsMinimumHours(meetsMin);

                attendanceSessionRepository.save(session);
                
                // Update past sessions for the same day too
                for (AttendanceSession prevSession : pastClosedSessions) {
                    prevSession.setMeetsMinimumHours(meetsMin);
                    attendanceSessionRepository.save(prevSession);
                }
                
                autoCheckoutCount++;
            }
        }
        if (autoCheckoutCount > 0) {
            notificationService.sendAutoCheckoutSummaryNotification(autoCheckoutCount);
        }
    }

    // Daily Late-Arrival & Absentee digest notification (runs at 11:00 AM IST)
    @Scheduled(cron = "0 0 11 * * *", zone = "Asia/Kolkata")
    public void sendDailyDigest() {
        LocalDate today = LocalDate.now(IST_ZONE);
        List<AttendanceSession> todaySessions = attendanceSessionRepository.findByWorkDateBetween(today, today);

        List<String> lateArrivals = todaySessions.stream()
                .filter(AttendanceSession::isLate)
                .map(s -> s.getPerson().getFullName())
                .distinct()
                .toList();

        List<Person> activePeople = personRepository.findAll().stream()
                .filter(p -> p.getStatus() == PersonStatus.ACTIVE)
                .toList();

        // Absentees: active people with NO completed checkout (CLOSED session) today
        Set<Long> presentPersonIds = todaySessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                .map(s -> s.getPerson().getId())
                .collect(Collectors.toSet());

        List<String> absentees = activePeople.stream()
                .filter(p -> !presentPersonIds.contains(p.getId()))
                .map(Person::getFullName)
                .toList();

        notificationService.sendDailyDigestNotification(lateArrivals, absentees);
    }

    // ─── Corrections / Manual Adjustments ──────────────────────────────────────
    public AttendanceSession correctSession(CorrectionRequest request) {
        AttendanceSession session = attendanceSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new RuntimeException("Error: Session not found!"));

        if (request.getCheckInAt() != null) {
            session.setCheckInAt(request.getCheckInAt());
            // Recalculate isLate
            SystemConfiguration config = getConfiguration();
            LocalTime latestAllowedTime = config.getExpectedStartTime().plusMinutes(config.getLateGraceMinutes());
            session.setLate(session.getCheckInAt().toLocalTime().isAfter(latestAllowedTime));
        }

        if (request.getCheckOutAt() != null) {
            session.setCheckOutAt(request.getCheckOutAt());
            session.setStatus(SessionStatus.CLOSED);
        }

        if (session.getCheckInAt() != null && session.getCheckOutAt() != null) {
            long minutes = ChronoUnit.MINUTES.between(session.getCheckInAt(), session.getCheckOutAt());
            session.setDurationMinutes((int) minutes);
            
            // Recalculate meets minimum hours
            SystemConfiguration config = getConfiguration();
            session.setMeetsMinimumHours(minutes >= config.getMinimumHoursRequired() * 60);
        }

        // Overlap validation
        List<AttendanceSession> dailySessions = attendanceSessionRepository.findByPersonIdAndWorkDateBetween(
                session.getPerson().getId(), session.getWorkDate(), session.getWorkDate());
        for (AttendanceSession other : dailySessions) {
            if (other.getId().equals(session.getId())) continue;
            
            LocalDateTime checkIn1 = session.getCheckInAt();
            LocalDateTime checkOut1 = session.getCheckOutAt() != null ? session.getCheckOutAt() : LocalDateTime.now(IST_ZONE);
            LocalDateTime checkIn2 = other.getCheckInAt();
            LocalDateTime checkOut2 = other.getCheckOutAt() != null ? other.getCheckOutAt() : LocalDateTime.now(IST_ZONE);
            
            if (checkIn1.isBefore(checkOut2) && checkIn2.isBefore(checkOut1)) {
                throw new RuntimeException("Error: Time overlaps with an existing session.");
            }
        }

        attendanceSessionRepository.save(session);

        // Audited adjustments
        auditLogService.log("ATTENDANCE_ADJUSTMENT", "SESSION", session.getId().toString(), request.getReason());

        return session;
    }

    public List<Person> getAbsentees(LocalDate date) {
        List<Person> activePersons = personRepository.findByStatus(PersonStatus.ACTIVE);
        List<AttendanceSession> sessionsOnDate = attendanceSessionRepository.findByWorkDateBetween(date, date);
        Set<Long> presentPersonIds = sessionsOnDate.stream().map(s -> s.getPerson().getId()).collect(Collectors.toSet());
        
        return activePersons.stream()
                .filter(p -> !presentPersonIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    // ─── Config Settings ───────────────────────────────────────────────────────
    public SystemConfiguration getSettings() {
        return getConfiguration();
    }

    public SystemConfiguration updateSettings(SystemConfigDto request) {
        SystemConfiguration config = getConfiguration();

        config.setExpectedStartTime(LocalTime.parse(request.getExpectedStartTime()));
        config.setLateGraceMinutes(request.getLateGraceMinutes());
        config.setAutoCheckoutTime(LocalTime.parse(request.getAutoCheckoutTime()));
        config.setWorkingDays(String.join(",", request.getWorkingDays()));
        config.setTapDebounceSeconds(request.getTapDebounceSeconds());
        config.setMinimumHoursRequired(request.getMinimumHoursRequired());

        systemConfigurationRepository.save(config);

        // Recalculate isLate for all of today's sessions based on new configuration
        LocalDate today = LocalDate.now(IST_ZONE);
        LocalTime latestAllowedTime = config.getExpectedStartTime().plusMinutes(config.getLateGraceMinutes());
        List<AttendanceSession> todaySessions = attendanceSessionRepository.findByWorkDateBetween(today, today);
        
        Map<Long, List<AttendanceSession>> sessionsByPerson = todaySessions.stream()
                .collect(Collectors.groupingBy(s -> s.getPerson().getId()));
                
        for (List<AttendanceSession> personSessions : sessionsByPerson.values()) {
            personSessions.sort(Comparator.comparing(AttendanceSession::getCheckInAt));
            boolean first = true;
            for (AttendanceSession session : personSessions) {
                if (first && session.getCheckInAt() != null) {
                    boolean shouldBeLate = session.getCheckInAt().toLocalTime().isAfter(latestAllowedTime);
                    if (session.isLate() != shouldBeLate) {
                        session.setLate(shouldBeLate);
                        attendanceSessionRepository.save(session);
                    }
                    first = false;
                } else if (!first && session.isLate()) {
                    session.setLate(false);
                    attendanceSessionRepository.save(session);
                }
            }
        }

        auditLogService.log("CONFIG_CHANGE", "CONFIG", config.getId().toString(), null);

        return config;
    }

    // ─── Analytics ─────────────────────────────────────────────────────────────
    public Map<String, Object> getAnalytics() {
        LocalDate today = LocalDate.now(IST_ZONE);
        SystemConfiguration config = getConfiguration();

        List<AttendanceSession> todaySessions = attendanceSessionRepository.findByWorkDateBetween(today, today);

        // 2. Active members
        List<Person> activePeople = personRepository.findAll().stream()
                .filter(p -> p.getStatus() == PersonStatus.ACTIVE)
                .toList();
        long activeCount = activePeople.size();

        // 1. Currently checked in (OPEN sessions = tapped in, haven't tapped out yet)
        long currentlyCheckedIn = todaySessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.OPEN)
                .map(s -> s.getPerson().getId())
                .distinct()
                .count();

        // 3. Present = people who have checked in today (OPEN or CLOSED session)
        //    This is the attendance count for the day
        Set<Long> presentPersonIds = todaySessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED || s.getStatus() == SessionStatus.OPEN)
                .map(s -> s.getPerson().getId())
                .collect(Collectors.toSet());

        // 3a. Attendance rate = present (checked in + checked out) / total active
        double attendanceRate = activeCount > 0 ? (double) presentPersonIds.size() / activeCount : 0.0;

        // 4. Average hours in office per present person
        double totalHoursToday = todaySessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED && s.getDurationMinutes() != null)
                .mapToInt(AttendanceSession::getDurationMinutes)
                .sum() / 60.0;
        double avgHours = presentPersonIds.isEmpty() ? 0.0 : totalHoursToday / presentPersonIds.size();

        // 5. Late arrivals (checked in after expectedStartTime)
        long lateCount = todaySessions.stream()
                .filter(AttendanceSession::isLate)
                .map(s -> s.getPerson().getId())
                .distinct()
                .count();

        // 6. Met minimum hours: present AND stayed >= minimumHoursRequired
        long metMinimumHours = todaySessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED && s.isMeetsMinimumHours())
                .map(s -> s.getPerson().getId())
                .distinct()
                .count();

        // 7. Short hours: present (checked out) but did NOT meet the minimum hour requirement
        long shortHours = todaySessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED && !s.isMeetsMinimumHours())
                .map(s -> s.getPerson().getId())
                .distinct()
                .count();

        // 8. Absentees: active people who have NO session at all today
        //    (people with OPEN-only sessions are still in office; ABSENT sessions = forgot checkout yesterday)
        long absentCount = activePeople.stream()
                .filter(p -> !presentPersonIds.contains(p.getId())
                          && todaySessions.stream().noneMatch(s -> s.getPerson().getId().equals(p.getId()) && s.getStatus() == SessionStatus.OPEN))
                .count();

        // 9. Denied taps today
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();
        long deniedTaps = attendanceEventRepository
                .findByOccurredAtBetweenOrderByOccurredAtDesc(startOfDay, endOfDay).stream()
                .filter(e -> e.getDecision() == AccessDecision.DENIED)
                .count();

        return Map.of(
                "currentlyCheckedIn",    currentlyCheckedIn,      // tapped in, not yet out
                "presentToday",          presentPersonIds.size(),  // completed checkout (any hours)
                "attendanceRate",        attendanceRate,           // presentToday / totalActive
                "metMinimumHours",       metMinimumHours,         // present AND >= 8h (informational)
                "shortHours",            shortHours,               // present but < 8h (informational flag)
                "minimumHoursRequired",  config.getMinimumHoursRequired(),
                "averageHours",          avgHours,
                "lateArrivals",          lateCount,
                "absentees",             absentCount,
                "deniedTaps",            deniedTaps
        );
    }

    // ─── Reports & Export ──────────────────────────────────────────────────────
    public List<AttendanceSession> getAttendanceReport(LocalDate start, LocalDate end, Long personId, String groupLabel) {
        List<AttendanceSession> sessions = attendanceSessionRepository.findByWorkDateBetween(start, end);

        return sessions.stream()
                .filter(s -> personId == null || s.getPerson().getId().equals(personId))
                .filter(s -> groupLabel == null || groupLabel.equalsIgnoreCase(s.getPerson().getGroupLabel()))
                .collect(Collectors.toList());
    }

    public byte[] exportCSV(LocalDate start, LocalDate end, Long personId, String groupLabel) {
        List<AttendanceSession> sessions = getAttendanceReport(start, end, personId, groupLabel);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out)) {
            writer.println("SessionID,PersonID,FullName,MemberType,GroupLabel,WorkDate,CheckInAt,CheckOutAt,DurationMinutes,DurationHours,Status,IsLate,MeetsMinimumHours");
            for (AttendanceSession s : sessions) {
                int dur = s.getDurationMinutes() != null ? s.getDurationMinutes() : 0;
                writer.printf("%d,%d,%s,%s,%s,%s,%s,%s,%s,%.2f,%s,%s,%s%n",
                        s.getId(),
                        s.getPerson().getId(),
                        s.getPerson().getFullName(),
                        s.getPerson().getMemberType().name(),
                        s.getPerson().getGroupLabel(),
                        s.getWorkDate().toString(),
                        s.getCheckInAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        s.getCheckOutAt() != null ? s.getCheckOutAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "",
                        dur,
                        dur / 60.0,
                        s.getStatus().name(),
                        s.isLate() ? "YES" : "NO",
                        s.isMeetsMinimumHours() ? "YES" : "NO"
                );
            }
        }
        return out.toByteArray();
    }

    public Map<String, Object> calculateStatistics(LocalDate start, LocalDate end, Long personId) {
        // 1. Calculate working days (excluding Saturday and Sunday)
        int workingDays = 0;
        LocalDate current = start;
        while (!current.isAfter(end)) {
            DayOfWeek day = current.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startDate", start.toString());
        result.put("endDate", end.toString());
        result.put("workingDaysInRange", workingDays);

        int finalWorkingDays = workingDays > 0 ? workingDays : 1;

        // 2. Individual stats if personId is provided
        if (personId != null) {
            Person person = personRepository.findById(personId)
                    .orElseThrow(() -> new RuntimeException("Error: Person not found!"));
            
            List<AttendanceSession> personSessions = attendanceSessionRepository.findByWorkDateBetween(start, end).stream()
                    .filter(s -> s.getPerson().getId().equals(personId))
                    .collect(Collectors.toList());

            long presentDays = personSessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                    .map(AttendanceSession::getWorkDate)
                    .distinct()
                    .count();

            long lateDays = personSessions.stream()
                    .filter(AttendanceSession::isLate)
                    .map(AttendanceSession::getWorkDate)
                    .distinct()
                    .count();

            long metMinHoursDays = personSessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.CLOSED && s.isMeetsMinimumHours())
                    .map(AttendanceSession::getWorkDate)
                    .distinct()
                    .count();

            long shortHoursDays = personSessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.CLOSED && !s.isMeetsMinimumHours())
                    .map(AttendanceSession::getWorkDate)
                    .distinct()
                    .count();

            double attendanceRate = (double) presentDays / finalWorkingDays;

            Map<String, Object> individualStats = new LinkedHashMap<>();
            individualStats.put("personId", person.getId());
            individualStats.put("fullName", person.getFullName());
            individualStats.put("presentDays", presentDays);
            individualStats.put("lateDays", lateDays);
            individualStats.put("metMinHoursDays", metMinHoursDays);
            individualStats.put("shortHoursDays", shortHoursDays);
            individualStats.put("attendanceRate", attendanceRate);

            result.put("individualStats", individualStats);
        }

        // 3. Overall stats for all active members
        List<Person> activePeople = personRepository.findAll().stream()
                .filter(p -> p.getStatus() == PersonStatus.ACTIVE)
                .collect(Collectors.toList());
        int activeCount = activePeople.size();

        List<AttendanceSession> allSessions = attendanceSessionRepository.findByWorkDateBetween(start, end);
        Set<Long> activePeopleIds = activePeople.stream().map(Person::getId).collect(Collectors.toSet());

        long totalClosedSessionsForActive = allSessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.CLOSED && activePeopleIds.contains(s.getPerson().getId()))
                .map(s -> s.getPerson().getId() + "_" + s.getWorkDate())
                .distinct()
                .count();

        long totalPossibleDays = (long) activeCount * workingDays;
        double overallAttendanceRate = totalPossibleDays > 0 ? (double) totalClosedSessionsForActive / totalPossibleDays : 0.0;

        Map<String, Object> overallStats = new LinkedHashMap<>();
        overallStats.put("totalActiveMembers", activeCount);
        overallStats.put("totalPresentSessionsCount", totalClosedSessionsForActive);
        overallStats.put("totalPossibleSessionsCount", totalPossibleDays);
        overallStats.put("overallAttendanceRate", overallAttendanceRate);

        result.put("overallStats", overallStats);

        return result;
    }

    // ─── Per-Person Range Report ────────────────────────────────────────────────
    public List<PersonAttendanceSummary> getPerPersonReport(LocalDate start, LocalDate end, String groupLabel) {

        // Calculate working days (Mon–Fri) in the range
        int workingDays = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            DayOfWeek dow = cur.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            cur = cur.plusDays(1);
        }
        final int finalWorkingDays = workingDays;

        // Fetch all sessions in range (single DB query)
        List<AttendanceSession> allSessions = attendanceSessionRepository.findByWorkDateBetween(start, end);

        // Group by person
        Map<Long, List<AttendanceSession>> byPerson = allSessions.stream()
                .collect(Collectors.groupingBy(s -> s.getPerson().getId()));

        // All active persons (include those with 0 sessions so absent days are correct)
        List<Person> activePeople = personRepository.findAll().stream()
                .filter(p -> p.getStatus() == PersonStatus.ACTIVE)
                .filter(p -> groupLabel == null || groupLabel.equalsIgnoreCase(p.getGroupLabel()))
                .collect(Collectors.toList());

        SystemConfiguration config = getConfiguration();
        int minimumHoursRequired = config.getMinimumHoursRequired();

        return activePeople.stream().map(person -> {
            List<AttendanceSession> sessions = byPerson.getOrDefault(person.getId(), Collections.emptyList());

            // Missed check-outs: sessions that are ABSENT (auto-closed overnight) OR
            // CLOSED by system with no checkOutAt (i.e. duration was set but tap-out never happened)
            int missedCheckOuts = (int) sessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.ABSENT
                            || (s.getStatus() == SessionStatus.CLOSED && s.getCheckOutAt() == null))
                    .count();

            // Days present: distinct work-dates with at least one CLOSED session
            int daysPresent = (int) sessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                    .map(AttendanceSession::getWorkDate)
                    .distinct()
                    .count();

            // Days met minimum hours: distinct work-dates where meetsMinimumHours = true
            int daysMetMinimumHours = (int) sessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.CLOSED && s.isMeetsMinimumHours())
                    .map(AttendanceSession::getWorkDate)
                    .distinct()
                    .count();

            // Total minutes across all CLOSED sessions
            int totalMinutes = sessions.stream()
                    .filter(s -> s.getStatus() == SessionStatus.CLOSED)
                    .mapToInt(s -> s.getDurationMinutes() != null ? s.getDurationMinutes() : 0)
                    .sum();
            
            double totalHours = Math.round(totalMinutes / 60.0 * 10.0) / 10.0;
            int expectedHours = finalWorkingDays * minimumHoursRequired;
            double attendancePct = expectedHours > 0 ? (totalHours / expectedHours) * 100 : 0.0;
            attendancePct = Math.min(attendancePct, 100.0);

            // Late count: distinct work-dates where the first session was marked late
            int lateCount = (int) sessions.stream()
                    .filter(AttendanceSession::isLate)
                    .map(AttendanceSession::getWorkDate)
                    .distinct()
                    .count();

            return PersonAttendanceSummary.builder()
                    .personId(person.getId())
                    .studentId(person.getExternalRef())
                    .fullName(person.getFullName())
                    .memberType(person.getMemberType().name())
                    .groupLabel(person.getGroupLabel())
                    .workingDaysInRange(finalWorkingDays)
                    .daysPresent(daysPresent)
                    .daysMetMinimumHours(daysMetMinimumHours)
                    .daysAbsent(Math.max(0, finalWorkingDays - daysPresent))
                    .totalMinutes(totalMinutes)
                    .totalHours(totalHours)
                    .expectedHours(expectedHours)
                    .attendancePct(Math.round(attendancePct * 10.0) / 10.0)
                    .lateCount(lateCount)
                    .missedCheckOuts(missedCheckOuts)
                    .build();
        }).collect(Collectors.toList());
    }
}
