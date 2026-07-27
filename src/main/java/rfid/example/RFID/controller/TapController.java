package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.TapRequest;
import rfid.example.RFID.model.AttendanceEvent;
import rfid.example.RFID.service.AttendanceService;

@RestController
@RequestMapping("/api/taps")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TapController {

    @Autowired
    private AttendanceService attendanceService;

    @Value("${application.security.device-key:devkey123}")
    private String configuredDeviceKey;

    @PostMapping
    public ResponseEntity<ApiResponse<AttendanceEvent>> processTap(
            @RequestHeader(value = "X-Device-Key", required = false) String deviceKeyHeader,
            @Valid @RequestBody TapRequest request) {

        // Validate device key / shared secret
        if (deviceKeyHeader == null || !deviceKeyHeader.equals(configuredDeviceKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Unauthorized: Invalid device key"));
        }

        // Process tap and return response
        AttendanceEvent event = attendanceService.processTap(request.getCardUid(), request.getDirection(), "DEVICE");
        return ResponseEntity.ok(ApiResponse.success(event));
    }
}
