package rfid.example.RFID;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rfid.example.RFID.dto.*;
import rfid.example.RFID.model.Role;
import rfid.example.RFID.model.User;
import rfid.example.RFID.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String testUserToken;
    private String testAdminToken;

    @Autowired
    private rfid.example.RFID.repository.AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();

        // Seed initial admin manually for testing clean slate
        User seedAdmin = User.builder()
                .email("admin@zencube.com")
                .password(passwordEncoder.encode("adminpassword"))
                .role(Role.ADMIN)
                .passwordChangeRequired(false)
                .active(true)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        userRepository.save(seedAdmin);

        // Get admin token
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setEmail("admin@zencube.com");
        adminLogin.setPassword("adminpassword");
        String adminLoginResponse = mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Since response is wrapped in ApiResponse: { success, data: AuthResponse, error }
        ApiResponse<?> apiResponse = objectMapper.readValue(adminLoginResponse, ApiResponse.class);
        AuthResponse adminAuth = objectMapper.convertValue(apiResponse.getData(), AuthResponse.class);
        testAdminToken = adminAuth.getToken();

        // Create standard manager via POST /api/users
        RegisterRequest managerReg = new RegisterRequest();
        managerReg.setEmail("manager1@zencube.com");
        managerReg.setPassword("password123");
        managerReg.setRole("MANAGER");
        
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + testAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(managerReg)))
                .andExpect(status().isOk());

        // Login as manager to get JWT
        LoginRequest managerLogin = new LoginRequest();
        managerLogin.setEmail("manager1@zencube.com");
        managerLogin.setPassword("password123");
        String managerLoginResponse = mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(managerLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ApiResponse<?> managerApiResponse = objectMapper.readValue(managerLoginResponse, ApiResponse.class);
        AuthResponse managerAuth = objectMapper.convertValue(managerApiResponse.getData(), AuthResponse.class);
        testUserToken = managerAuth.getToken();
    }

    @Test
    void testChangePasswordSuccess() throws Exception {
        ChangePasswordRequest changeRequest = ChangePasswordRequest.builder()
                .oldPassword("password123")
                .newPassword("newsecurepassword")
                .build();

        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + testUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isOk());

        // Verify login works with new password
        LoginRequest newLogin = new LoginRequest();
        newLogin.setEmail("manager1@zencube.com");
        newLogin.setPassword("newsecurepassword");
        mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk());
    }

    @Test
    void testChangePasswordInvalidOldPassword() throws Exception {
        ChangePasswordRequest changeRequest = ChangePasswordRequest.builder()
                .oldPassword("wrongpassword")
                .newPassword("newsecurepassword")
                .build();

        mockMvc.perform(post("/api/auth/change-password")
                .header("Authorization", "Bearer " + testUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testChangePasswordNoJwt() throws Exception {
        ChangePasswordRequest changeRequest = ChangePasswordRequest.builder()
                .oldPassword("password123")
                .newPassword("newsecurepassword")
                .build();

        mockMvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUpdateUserRoleSuccessByAdmin() throws Exception {
        User managerUser = userRepository.findByEmail("manager1@zencube.com").orElseThrow();
        StaffUserRequest updateRequest = new StaffUserRequest();
        updateRequest.setRole("ADMIN");

        mockMvc.perform(patch("/api/users/" + managerUser.getId())
                .header("Authorization", "Bearer " + testAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        // Verify the user now has ADMIN role in DB
        User manager = userRepository.findByEmail("manager1@zencube.com").orElseThrow();
        assertEquals(Role.ADMIN, manager.getRole());
    }

    @Test
    void testUpdateUserRoleFailureByEmployee() throws Exception {
        User managerUser = userRepository.findByEmail("manager1@zencube.com").orElseThrow();
        StaffUserRequest updateRequest = new StaffUserRequest();
        updateRequest.setRole("ADMIN");

        mockMvc.perform(patch("/api/users/" + managerUser.getId())
                .header("Authorization", "Bearer " + testUserToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }
}
