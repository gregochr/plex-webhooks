package gregochr.controllers;

import gregochr.controllers.dto.LocationResponseDto;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class LocationController {
    private static final String POSTCODE_SERVICE_URL = "https://api.postcodes.io/postcodes/{postCode}";

    @Value("${postcode}")
    private String postCode;

    @Autowired
    private RestClient restClient;

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getPostCode() {
        return this.postCode;
    }

    public LocationResponseDto getLatitudeAndLogitude() {
        LogManager.getLogger(LocationController.class).info("[ Postcode : {} ]", this.postCode);

        LocationResponseDto locationResponse = restClient
                .get()
                .uri(POSTCODE_SERVICE_URL, getPostCodeFormattedForPostCodesAPI())
                .retrieve()
                .body(LocationResponseDto.class);

        LogManager.getLogger(LocationController.class).info("[ Location: {} ]", locationResponse);

        return locationResponse;
    }

    private String getPostCodeFormattedForPostCodesAPI() {
        return StringUtils.delete(this.postCode, " ");
    }
}