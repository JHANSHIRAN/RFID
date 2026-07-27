package rfid.example.RFID.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import rfid.example.RFID.dto.ApiResponse;
import rfid.example.RFID.dto.RegisterRequest;
import rfid.example.RFID.dto.StaffUserRequest;
import rfid.example.RFID.model.Role;
import rfid.example.RFID.model.User;
import rfid.example.RFID.service.UserService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*", maxAge = 3600)
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<List<User>> listStaffUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = userService.getStaffUserById(
            userService.getAllStaffUsers().stream()
                .filter(u -> u.getEmail().equals(auth.getName()))
                .findFirst()
                .orElseThrow()
                .getId()
        );

        List<User> list = userService.getAllStaffUsers();

        // Managers can only list Operator users
        if (currentUser.getRole() == Role.MANAGER) {
            list = list.stream()
                    .filter(u -> u.getRole() == Role.OPERATOR)
                    .collect(Collectors.toList());
        }

        return ApiResponse.success(list);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<Map<String, String>> createStaffUser(@Valid @RequestBody RegisterRequest request) {
        String msg = userService.registerUser(request);
        return ApiResponse.success(Map.of("message", msg));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ApiResponse<User> updateStaffUser(@PathVariable Long id, @RequestBody StaffUserRequest request) {
        User updated = userService.updateStaffUser(id, request);
        return ApiResponse.success(updated);
    }
}
