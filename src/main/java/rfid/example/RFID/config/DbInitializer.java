package rfid.example.RFID.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import rfid.example.RFID.model.SystemConfiguration;
import rfid.example.RFID.repository.SystemConfigurationRepository;
import rfid.example.RFID.service.UserService;

import java.time.LocalTime;

@Component
public class DbInitializer implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private SystemConfigurationRepository systemConfigurationRepository;

    @Override
    public void run(String... args) throws Exception {
        // Seed default Admin user
        userService.seedAdmin();

        // Seed default SystemConfiguration
        if (systemConfigurationRepository.count() == 0) {
            SystemConfiguration config = SystemConfiguration.builder()
                    .expectedStartTime(LocalTime.of(9, 30))
                    .lateGraceMinutes(0)
                    .autoCheckoutTime(LocalTime.of(20, 0)) // Retained for schema compatibility
                    .workingDays("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY")
                    .tapDebounceSeconds(10)
                    .minimumHoursRequired(8)
                    .build();
            systemConfigurationRepository.save(config);
        }
    }
}
