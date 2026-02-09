package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HueResourceResponseDto {
    private List<Object> errors;
    private List<HueResourceDto> data;

    public List<Object> getErrors() { return errors; }
    public void setErrors(List<Object> errors) { this.errors = errors; }

    public List<HueResourceDto> getData() { return data; }
    public void setData(List<HueResourceDto> data) { this.data = data; }
}
