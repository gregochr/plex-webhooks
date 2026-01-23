package gregochr.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gregochr.beans.LocationRootBean;
import org.apache.logging.log4j.LogManager;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
public class LocationController {
    private final String postCodeServiceURL = "https://api.postcodes.io/postcodes/";

    public LocationRootBean getLatitudeAndLogitude(String postCode) {
        final String url = postCodeServiceURL + postCode;
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            LogManager.getLogger(LocationController.class).error("Error creating URL for: " + url);
            throw new RuntimeException(e);
        }
        return getLocationRootBean(uri, postCode);
    }

    private LocationRootBean getLocationRootBean(URI uri, String postCode) {
        LocationRootBean locationRootBean = new LocationRootBean();

        try {
            RestTemplate template = new RestTemplate();
            ResponseEntity<String> result = template.getForEntity(uri, String.class);
            HttpStatusCode statusCode = result.getStatusCode();
            if (statusCode.value() == 200) {
                ObjectMapper objectMapper = new ObjectMapper();
                try {
                    locationRootBean = new ObjectMapper().readerFor(LocationRootBean.class).readValue(result.getBody());
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            } else {
                LogManager.getLogger(LocationController.class).error("HTTP Error fetching longitude / latitude from postcode: " + postCode + ", HTTP Status: " + statusCode.toString());
            }
        } catch (HttpClientErrorException e) {
            LogManager.getLogger(LocationController.class).error("HTTP Error fetching longitude / latitude from postcode: " + postCode    );
        }
        return locationRootBean;
    }
}
