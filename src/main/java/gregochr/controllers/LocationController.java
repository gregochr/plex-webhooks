package gregochr.controllers;

import gregochr.controllers.dto.LocationResponseDto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class LocationController {
    private static final Logger LOGGER = LogManager.getLogger(LocationController.class);
    private static final String POSTCODE_SERVICE_URL = "https://api.postcodes.io/postcodes/{postCode}";
    private static final RestClient REST_CLIENT = RestClient.create();

    @Value("${postcode}")
    private String postCode;

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getPostCode() {
        return this.postCode;
    }

    public LocationResponseDto getLatitudeAndLogitude() {
        LOGGER.info("[ Postcode : {} ]", this.postCode);

        LocationResponseDto locationResponse = REST_CLIENT
                .get()
                .uri(POSTCODE_SERVICE_URL, getPostCodeFormattedForPostCodesAPI())
                .retrieve()
                .body(LocationResponseDto.class);

        LOGGER.info("[ Location: {} ]", locationResponse);

        return locationResponse;
    }

    private String getPostCodeFormattedForPostCodesAPI() {
        return StringUtils.delete(this.postCode, " ");
    }
}