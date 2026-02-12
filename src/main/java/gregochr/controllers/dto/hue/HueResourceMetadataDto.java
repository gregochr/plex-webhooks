package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HueResourceMetadataDto {
    private String name;
    private String archetype;
    private String category;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getArchetype() { return archetype; }
    public void setArchetype(String archetype) { this.archetype = archetype; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
