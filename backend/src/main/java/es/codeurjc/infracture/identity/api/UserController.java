package es.codeurjc.infracture.identity.api;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.security.AuthenticatedUser;
import es.codeurjc.infracture.security.jwt.UserLoginService;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final UserLoginService userLoginService;

    public UserController(UserService userService, UserMapper userMapper, UserLoginService userLoginService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.userLoginService = userLoginService;
    }

    @PostMapping
    public ResponseEntity<UserDTO> registerUser(@RequestBody RegisterUserDTO userDTO) {
        User registeredUser = userService.registerUser(userDTO.username(), userDTO.displayName(),
                userDTO.email(), userDTO.password());
        URI location = fromCurrentRequest().path("/{id}").buildAndExpand(registeredUser.getId()).toUri();

        return ResponseEntity.created(location).body(userMapper.toDTO(registeredUser));
    }

    @GetMapping("/me")
    public UserDTO getUserLogged(Principal principal) {
        User user = userService.getUserByEmail(principal.getName());
        return userMapper.toDTO(user);
    }

    @PatchMapping("/me")
    public UserDTO updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody UpdateProfileDTO profile) {
        return userMapper.toDTO(userService.updateProfile(principal.getId(), profile.displayName(),
                profile.username()));
    }

    // The change ends every other session; this one continues with the new cookies.
    @PutMapping("/me/email")
    public UserDTO changeEmail(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody ChangeEmailDTO change, HttpServletResponse response) {
        User user = userService.changeEmail(principal.getId(), change.email(), change.currentPassword());
        userLoginService.issueSession(response, AuthenticatedUser.from(user));
        return userMapper.toDTO(user);
    }

    @PutMapping("/me/password")
    public UserDTO changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody ChangePasswordDTO change, HttpServletResponse response) {
        User user = userService.changePassword(principal.getId(), change.currentPassword(), change.newPassword());
        userLoginService.issueSession(response, AuthenticatedUser.from(user));
        return userMapper.toDTO(user);
    }
}
