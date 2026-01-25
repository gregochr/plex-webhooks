package gregochr.controllers;

import gregochr.beans.LocationRootBean;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Configuration
@Component
public class LocationController {

    @Value("postCode")
    private String postCode = "DH1 3QL";

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public LocationRootBean getLatitudeAndLogitude() throws IOException {
        RestClient restClient = RestClient.create();
        LogManager.getLogger(PlexController.class).info("[ Postcode from config : " + this.postCode + " ]");
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
