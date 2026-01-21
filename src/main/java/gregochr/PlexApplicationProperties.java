package gregochr;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.apache.logging.log4j.LogManager;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Validated
@ConfigurationProperties(prefix = "plexwebhooks")
public class PlexApplicationProperties {
    @Pattern(regexp = "^([a-zA-Z]{1,2}\\d{1,2}[a-zA-Z]{0,2})\\s*?(\\d[a-zA-Z]{2})$")
    @NotBlank
    private String postCode;

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getPostCode() {
        return this.postCode;
    }
}
