package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.CorrectionRequest;
import rfid.example.RFID.dto.PersonAttendanceSummary;
import rfid.example.RFID.model.AttendanceEvent;
import rfid.example.RFID.model.AttendanceSession;
import rfid.example.RFID.model.SessionStatus;
import rfid.example.RFID.repository.AttendanceSessionRepository;
import rfid.example.RFID.repository.AttendanceEventRepository;
import rfid.example.RFID.service.AttendanceService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AttendanceController {

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private AttendanceEventRepository attendanceEventRepository;

    @Autowired
    private AttendanceSessionRepository attendanceSessionRepository;

    private boolean isOperatorOnly() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR"))
                && auth.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
    }

    @GetMapping("/events")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<List<AttendanceEvent>> getEvents() {
        if (isOperatorOnly()) {
            // Operator sees today's events only
            LocalDateTime startOfToday = LocalDate.now(ZoneId.of("Asia/Kolkata")).atStartOfDay();
            LocalDateTime endOfToday = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).atStartOfDay();
            List<AttendanceEvent> todayEvents = attendanceEventRepository
                    .findByOccurredAtBetweenOrderByOccurredAtDesc(startOfToday, endOfToday);
            return ApiResponse.success(todayEvents);
        } else {
            // Managers and Admins see all events
            return ApiResponse.success(attendanceEventRepository.findAll());
        }
    }

    @GetMapping("/attendance/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<List<AttendanceSession>> getLiveBoard() {
        List<AttendanceSession> openSessions = attendanceSessionRepository.findByStatus(SessionStatus.OPEN);
        return ApiResponse.success(openSessions);
    }

    @PostMapping("/attendance/manual-tap")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<AttendanceEvent> manualTap(
            @RequestParam String cardUid,
            @RequestParam String direction) {
        AttendanceEvent event = attendanceService.processTap(cardUid, direction, "MANUAL_WEB");
        return ApiResponse.success(event);
    }

    @GetMapping("/attendance/report")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<List<AttendanceSession>> getAttendanceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) String groupLabel) {

        List<AttendanceSession> report = attendanceService.getAttendanceReport(startDate, endDate, personId, groupLabel);
        return ApiResponse.success(report);
    }

    @GetMapping("/attendance/report/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long personId,
            @RequestParam(required = false) String groupLabel) {

        byte[] csvBytes = attendanceService.exportCSV(startDate, endDate, personId, groupLabel);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }

    @PostMapping("/attendance/corrections")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<AttendanceSession> correctAttendanceSession(@Valid @RequestBody CorrectionRequest request) {
        AttendanceSession session = attendanceService.correctSession(request);
        return ApiResponse.success(session);
    }

    @GetMapping("/dashboard/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<Map<String, Object>> getAnalytics() {
        Map<String, Object> stats = attendanceService.getAnalytics();
        return ApiResponse.success(stats);
    }

    @GetMapping("/attendance/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Map<String, Object>> getAttendanceStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long personId) {
        Map<String, Object> stats = attendanceService.calculateStatistics(startDate, endDate, personId);
        return ApiResponse.success(stats);
    }

    @GetMapping("/attendance/per-person-report")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<List<PersonAttendanceSummary>> getPerPersonReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String groupLabel) {
        List<PersonAttendanceSummary> summary = attendanceService.getPerPersonReport(startDate, endDate, groupLabel);
        return ApiResponse.success(summary);
    }

    @GetMapping("/attendance/absentees")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OPERATOR')")
    public ApiResponse<List<rfid.example.RFID.model.Person>> getAbsentees(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<rfid.example.RFID.model.Person> absentees = attendanceService.getAbsentees(date);
        return ApiResponse.success(absentees);
    }
}
