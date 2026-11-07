package gregochr;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RestClientConfig {
    @Bean
    public RestClient restClient() {
        return restClientBuilder().build();
    }

    /**
     * Builds the client with a Jackson 3 mapper that keeps Jackson 2's behaviour: a primitive the provider omits
     * or sends as null (postcodes.io does this for latitude and longitude) reads as 0, not an error, and
     * properties keep declaration order.
     */
    static RestClient.Builder restClientBuilder() {
        JsonMapper mapper = JsonMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        return RestClient.builder()
                .configureMessageConverters(converters ->
                        converters.withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)));
    }
}
