package rfid.example.RFID;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import rfid.example.RFID.dto.*;
import rfid.example.RFID.model.Role;
import rfid.example.RFID.model.User;
import rfid.example.RFID.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ModuleAccessIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String managerToken;
    private String operatorToken;

    @Autowired
    private rfid.example.RFID.repository.AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() throws Exception {
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.flush();

        // 1. Save Admin directly to setup testing environment
        User seedAdmin = User.builder()
                .email("admin@zencube.com")
                .password(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("password123"))
                .role(Role.ADMIN)
                .passwordChangeRequired(false)
                .active(true)
                .createdAt(java.time.LocalDateTime.now())
                .build();
        userRepository.save(seedAdmin);

        // Get admin JWT token
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setEmail("admin@zencube.com");
        adminLogin.setPassword("password123");
        String adminLoginResponse = mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ApiResponse<?> adminApiResponse = objectMapper.readValue(adminLoginResponse, ApiResponse.class);
        AuthResponse adminAuth = objectMapper.convertValue(adminApiResponse.getData(), AuthResponse.class);
        adminToken = adminAuth.getToken();

        // 2. Create and log in Manager using POST /api/users
        RegisterRequest managerReg = new RegisterRequest();
        managerReg.setEmail("manager@zencube.com");
        managerReg.setPassword("password123");
        managerReg.setRole("MANAGER");
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(managerReg)))
                .andExpect(status().isOk());

        LoginRequest managerLogin = new LoginRequest();
        managerLogin.setEmail("manager@zencube.com");
        managerLogin.setPassword("password123");
        String managerLoginResponse = mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(managerLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ApiResponse<?> managerApiResponse = objectMapper.readValue(managerLoginResponse, ApiResponse.class);
        AuthResponse managerAuth = objectMapper.convertValue(managerApiResponse.getData(), AuthResponse.class);
        managerToken = managerAuth.getToken();

        // 3. Create and log in Operator using POST /api/users
        RegisterRequest operatorReg = new RegisterRequest();
        operatorReg.setEmail("operator@zencube.com");
        operatorReg.setPassword("password123");
        operatorReg.setRole("OPERATOR");
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(operatorReg)))
                .andExpect(status().isOk());

        LoginRequest operatorLogin = new LoginRequest();
        operatorLogin.setEmail("operator@zencube.com");
        operatorLogin.setPassword("password123");
        String operatorLoginResponse = mockMvc.perform(post("/api/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(operatorLogin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ApiResponse<?> operatorApiResponse = objectMapper.readValue(operatorLoginResponse, ApiResponse.class);
        AuthResponse operatorAuth = objectMapper.convertValue(operatorApiResponse.getData(), AuthResponse.class);
        operatorToken = operatorAuth.getToken();
    }

    @Test
    void testAdminAccess() throws Exception {
        // Admin should be able to access everything
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/people")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attendance/live")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/audit-log")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testManagerAccess() throws Exception {
        // Manager should be blocked from Admin-only endpoints like audit-log
        mockMvc.perform(get("/api/audit-log")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isForbidden());

        // Manager should be allowed access to staff lists, people, live-board
        mockMvc.perform(get("/api/users")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/people")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attendance/live")
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    @Test
    void testOperatorAccess() throws Exception {
        // Operator should be blocked from Admin-only and Manager-only endpoints like audit-log and analytics
        mockMvc.perform(get("/api/audit-log")
                .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/dashboard/analytics")
                .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());

        // Operator should be allowed access to people, cards list, live board, events list
        mockMvc.perform(get("/api/people")
                .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attendance/live")
                .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk());
    }

    @Test
    void testUnauthenticatedAccess() throws Exception {
        // Unauthenticated users should be blocked from everything except login and taps
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/people"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/attendance/live"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testManagerCreateOperator() throws Exception {
        RegisterRequest newOperator = new RegisterRequest();
        newOperator.setEmail("new_op@zencube.com");
        newOperator.setPassword("password123");
        newOperator.setRole("OPERATOR");

        // Manager can create operator
        mockMvc.perform(post("/api/users")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newOperator)))
                .andExpect(status().isOk());

        // Verify user exists and has ROLE_OPERATOR
        User createdOp = userRepository.findByEmail("new_op@zencube.com").orElseThrow();
        assertEquals(Role.OPERATOR, createdOp.getRole());
    }
}
