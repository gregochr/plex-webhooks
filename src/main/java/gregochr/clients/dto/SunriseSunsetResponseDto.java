package gregochr.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SunriseSunsetResponseDto(
        String status,
        String tzid,
        @JsonProperty("results")
        @JsonDeserialize(using = SunriseSunsetResponseDto.ResultsDeserializer.class)
        SunriseSunsetResultDto sunriseSunsetResultDto) {

    @Override
    public String toString() {
        return "[ Status: %s, tzid: %s, %s ]".formatted(status, tzid, sunriseSunsetResultDto);
    }

    /**
     * sunrise-sunset.org answers an error with {@code "results": ""} instead of an object. That reads as a null
     * result, so the response still carries the provider's status.
     */
    static class ResultsDeserializer extends JsonDeserializer<SunriseSunsetResultDto> {

        @Override
        public SunriseSunsetResultDto deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (parser.currentToken() == JsonToken.VALUE_STRING && parser.getText().isBlank()) {
                return null;
            }
            // Anything else is read as usual, so a non-empty string is still rejected by Jackson.
            return context.readValue(parser, SunriseSunsetResultDto.class);
        }
    }
}
