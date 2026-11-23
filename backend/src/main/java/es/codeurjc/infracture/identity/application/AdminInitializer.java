package es.codeurjc.infracture.identity.application;

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
        userService.initializeAdmin(
                environment.getProperty("infracture.admin.username"),
                environment.getProperty("infracture.admin.display-name"),
                environment.getProperty("infracture.admin.email"),
                environment.getProperty("infracture.admin.password"));
    }
}
