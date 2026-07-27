package rfid.example.RFID.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rfid.example.RFID.dto.ApiResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*", maxAge = 3600)
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, String>> checkHealth() {
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
