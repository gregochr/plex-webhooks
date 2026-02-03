package gregochr.controllers;

import gregochr.controllers.beans.LocationRootBean;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class LocationController {
    private static final String POSTCODE_SERVICE_URL = "https://api.postcodes.io/postcodes/{postCode}";

    @Value("${postcode}")
    private String postCode;

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getPostCode() {
        return this.postCode;
    }

    public LocationRootBean getLatitudeAndLogitude() {
        RestClient restClient = RestClient.create();
        LogManager.getLogger(LocationController.class).info("[ Postcode : {} ]", this.postCode);

        LocationRootBean locationRootBean = restClient
                .get()
                .uri(POSTCODE_SERVICE_URL, getPostCodeFormattedForPostCodesAPI())
                .retrieve()
                .body(LocationRootBean.class);

        LogManager.getLogger(LocationController.class).info("[ Location: {} ]", locationRootBean);

        return locationRootBean;
    }

    private String getPostCodeFormattedForPostCodesAPI() {
        return StringUtils.delete(this.postCode, " ");
    }
}