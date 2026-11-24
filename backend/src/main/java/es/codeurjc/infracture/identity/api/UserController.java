package es.codeurjc.infracture.identity.api;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

import es.codeurjc.infracture.identity.application.AvatarService;
import es.codeurjc.infracture.identity.application.AvatarService.Avatar;
import es.codeurjc.infracture.identity.application.UserService;
import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.security.AuthenticatedUser;
import es.codeurjc.infracture.security.jwt.UserLoginService;
import es.codeurjc.infracture.shared.NotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final UserLoginService userLoginService;
    private final AvatarService avatarService;

    public UserController(UserService userService, UserMapper userMapper, UserLoginService userLoginService,
            AvatarService avatarService) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.userLoginService = userLoginService;
        this.avatarService = avatarService;
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

    @PutMapping(path = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserDTO replaceAvatar(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("file") MultipartFile file) throws IOException {
        return userMapper.toDTO(avatarService.replace(principal.getId(), file.getBytes()));
    }

    @DeleteMapping("/me/avatar")
    public UserDTO removeAvatar(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userMapper.toDTO(avatarService.remove(principal.getId()));
    }

    // The avatarUrl of UserDTO carries the image version: a request for the current version never changes,
    // so the browser may keep it; any other request is checked again.
    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> getAvatar(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(name = "v", required = false) String version) {
        Avatar avatar = avatarService.read(principal.getId())
                .orElseThrow(() -> new NotFoundException("The account has no avatar"));
        CacheControl cache = avatar.version().equals(version)
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable()
                : CacheControl.noCache().cachePrivate();
        return ResponseEntity.ok().cacheControl(cache).contentType(MediaType.parseMediaType(avatar.contentType()))
                .body(avatar.content());
    }
}
