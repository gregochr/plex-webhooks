package gregochr.controllers;

import gregochr.controllers.beans.LocationRootBean;
import gregochr.services.plex.PlexService;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Component
public class LocationController {

    @Value("${postcode}")
    private String postCode;

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public String getPostCode() {
        return this.postCode;
    }

    public LocationRootBean getLatitudeAndLogitude() throws IOException {
        RestClient restClient = RestClient.create();
        LogManager.getLogger(PlexService.class).info("[ Postcode : " + this.postCode + " ]");
        String postCodeServiceURL = "https://api.postcodes.io/postcodes/";
        
        LocationRootBean locationRootBean = restClient
                .get()
                .uri(postCodeServiceURL + "{postCode}", getPostCodeFormattedForPostCodesAPI())
                .retrieve()
                .body(LocationRootBean.class);

        restClient.delete();
        LogManager.getLogger(LocationController.class).info("[ Location: " + locationRootBean + " ]");

        return locationRootBean;
    }

    private String getPostCodeFormattedForPostCodesAPI() throws IOException {
        return StringUtils.delete(this.postCode, " ");
    }
}
