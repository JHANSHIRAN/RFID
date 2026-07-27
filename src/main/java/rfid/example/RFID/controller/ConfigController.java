package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.SystemConfigDto;
import rfid.example.RFID.model.SystemConfiguration;
import rfid.example.RFID.service.AttendanceService;

@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ConfigController {

    @Autowired
    private AttendanceService attendanceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<SystemConfiguration> getConfig() {
        SystemConfiguration config = attendanceService.getSettings();
        return ApiResponse.success(config);
    }

    @PatchMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SystemConfiguration> updateConfig(@Valid @RequestBody SystemConfigDto request) {
        SystemConfiguration config = attendanceService.updateSettings(request);
        return ApiResponse.success(config);
    }
}
