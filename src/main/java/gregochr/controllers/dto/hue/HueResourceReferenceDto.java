package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HueResourceReferenceDto {
    private String rid;
    private String rtype;

    public String getRid() { return rid; }
    public void setRid(String rid) { this.rid = rid; }

    public String getRtype() { return rtype; }
    public void setRtype(String rtype) { this.rtype = rtype; }
}
