package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HueResourceDto {
    private String id;
    @JsonProperty("id_v1")
    private String idV1;
    private String type;
    private HueResourceReferenceDto owner;
    private HueResourceMetadataDto metadata;
    private List<HueResourceReferenceDto> services;
    private List<HueResourceReferenceDto> children;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdV1() { return idV1; }
    public void setIdV1(String idV1) { this.idV1 = idV1; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public HueResourceReferenceDto getOwner() { return owner; }
    public void setOwner(HueResourceReferenceDto owner) { this.owner = owner; }

    public HueResourceMetadataDto getMetadata() { return metadata; }
    public void setMetadata(HueResourceMetadataDto metadata) { this.metadata = metadata; }

    public List<HueResourceReferenceDto> getServices() { return services; }
    public void setServices(List<HueResourceReferenceDto> services) { this.services = services; }

    public List<HueResourceReferenceDto> getChildren() { return children; }
    public void setChildren(List<HueResourceReferenceDto> children) { this.children = children; }
}
