package gregochr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
public class LocationController {
    public LocationController() {

    }

    public LocationRootBean getLatitudeAndLogitude() {
        final String url = "https://api.postcodes.io/postcodes/DH14HU";
        LocationRootBean locationRootBean = null;
        RestTemplate template = new RestTemplate();
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        ResponseEntity<String> result = template.getForEntity(uri, String.class);
        HttpStatusCode statusCode = result.getStatusCode();
        if (statusCode.value() == 200) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                locationRootBean = new ObjectMapper().readerFor(LocationRootBean.class).readValue(result.getBody());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return locationRootBean;
    }
}
