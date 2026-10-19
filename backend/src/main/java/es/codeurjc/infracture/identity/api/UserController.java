package es.codeurjc.infracture.identity.api;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import java.net.URI;
import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @PostMapping("/")
    public ResponseEntity<UserDTO> registerUser(@RequestBody RegisterUserDTO userDTO) {
        User registeredUser = userService.registerUser(userMapper.fromRegisterDTOtoDomain(userDTO));
        URI location = fromCurrentRequest().path("/{id}").buildAndExpand(registeredUser.getId()).toUri();

        return ResponseEntity.created(location).body(userMapper.toDTO(registeredUser));
    }

    @GetMapping("/me")
    public UserDTO getUserLogged(Principal principal) {
        User user = userService.getUserByEmail(principal.getName());
        return userMapper.toDTO(user);
    }

}
