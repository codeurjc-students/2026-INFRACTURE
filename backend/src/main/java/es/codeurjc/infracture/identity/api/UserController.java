package es.codeurjc.infracture.identity.api;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

import es.codeurjc.infracture.identity.application.UserAlreadyExistsException;
import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.security.Principal;

import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> registerUser(@RequestBody RegisterUserDTO userDTO) {
        try {
            User newUser = userMapper.fromRegisterDTOtoDomain(userDTO);
            User registeredUser = userService.registerUser(newUser);

            URI location = fromCurrentRequest().path("/{id}").buildAndExpand(registeredUser.getId()).toUri();

            return ResponseEntity.created(location).body(userMapper.toDTO(registeredUser));
        } catch (UserAlreadyExistsException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
        } catch (ConstraintViolationException | IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(exception.getMessage());
        }

    }

    @GetMapping("/me")
    public UserDTO getUserLogged(Principal principal) {
        User user = userService.getUserByEmail(principal.getName());
        return userMapper.toDTO(user);
    }

}
