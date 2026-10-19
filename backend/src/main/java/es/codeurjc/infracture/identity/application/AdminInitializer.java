package es.codeurjc.infracture.identity.application;

import es.codeurjc.infracture.identity.domain.User;
import es.codeurjc.infracture.identity.domain.UserRole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {

    private final UserService userService;
    private final Environment environment;

    public AdminInitializer(UserService userService, Environment environment) {
        this.userService = userService;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        User admin = new User(
                environment.getProperty("infracture.admin.username"),
                environment.getProperty("infracture.admin.password"),
                environment.getProperty("infracture.admin.email"),
                environment.getProperty("infracture.admin.display-name"),
                UserRole.ADMIN);
        userService.initializeAdmin(admin);
    }
}
