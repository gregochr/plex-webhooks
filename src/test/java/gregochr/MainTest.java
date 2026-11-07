package gregochr;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

/** Tests that {@link Main#main} hands its own class and the arguments to Spring Boot. */
class MainTest {

    @Test
    @DisplayName("main() runs the Spring application for Main with the given arguments")
    void mainDelegatesToSpringApplication() {
        String[] args = {"--server.port=0"};

        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            Main.main(args);

            spring.verify(() -> SpringApplication.run(Main.class, args));
        }
    }
}
