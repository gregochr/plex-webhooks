package gregochr.services.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("ConfigSectionsService Tests")
public class ConfigSectionsServiceTest {

    private static final String CANARY = "s3cr3t-VALUE-123";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static ConfigSectionsService serviceFor(String file, MockEnvironment env, boolean adminTab) {
        PropertiesRepository repository =
                new PropertiesRepository(env, new ByteArrayResource(file.getBytes(StandardCharsets.UTF_8)));
        ConfigurationViewService viewService = new ConfigurationViewService(repository,
                new ConfigurationViewFormatter(), mock(ApplicationEventPublisher.class));
        return new ConfigSectionsService(viewService, adminTab);
    }

    private static String json(ConfigSectionsService service) throws Exception {
        return MAPPER.writeValueAsString(service.view());
    }

    private static JsonNode item(JsonNode root, String key) {
        for (JsonNode section : root.get("sections")) {
            for (JsonNode item : section.get("items")) {
                if (key.equals(item.get("key").asString())) {
                    return item;
                }
            }
        }
        throw new AssertionError("no item " + key);
    }

    @DisplayName("a credential's value appears nowhere in the response, whichever kind of key it is")
    @ParameterizedTest
    @ValueSource(strings = {"hue.application-key", "app.admin.password", "dirigera.access-token",
            "foo.client-secret", "foo.api_key"})
    public void testCredentialValueNeverInJson(String key) throws Exception {
        ConfigSectionsService service = serviceFor("# Secrets\n" + key + "=x\n",
                new MockEnvironment().withProperty(key, CANARY), false);

        String body = json(service);

        assertFalse(body.contains(CANARY), body);
        JsonNode node = item(MAPPER.readTree(body), key);
        assertTrue(node.get("secret").asBoolean());
        assertTrue(node.get("set").asBoolean());
        assertFalse(node.has("value"), body);
        assertFalse(node.has("displayValue"), body);
    }

    @DisplayName("a blank credential reports set=false")
    @Test
    public void testBlankCredentialIsNotSet() throws Exception {
        ConfigSectionsService service = serviceFor("# Secrets\nhue.application-key=\n",
                new MockEnvironment().withProperty("hue.application-key", "   "), false);

        JsonNode node = item(MAPPER.readTree(json(service)), "hue.application-key");

        assertFalse(node.get("set").asBoolean());
        assertFalse(node.has("value"));
    }

    @DisplayName("a missing credential reports set=false")
    @Test
    public void testAbsentCredentialIsNotSet() throws Exception {
        ConfigSectionsService service = serviceFor("# Secrets\napp.admin.password=\n", new MockEnvironment(), false);

        assertFalse(item(MAPPER.readTree(json(service)), "app.admin.password").get("set").asBoolean());
    }

    @DisplayName("an interval is writable and a plain item has value, displayValue and no set field")
    @Test
    public void testIntervalIsWritableAndPlainItemShape() throws Exception {
        ConfigSectionsService service = serviceFor("# SSE\nsse.heartbeat-interval=10000\n",
                new MockEnvironment().withProperty("sse.heartbeat-interval", "10000"), false);

        JsonNode node = item(MAPPER.readTree(json(service)), "sse.heartbeat-interval");

        assertTrue(node.get("writable").asBoolean());
        assertTrue(node.get("duration").asBoolean());
        assertFalse(node.get("secret").asBoolean());
        assertEquals("number", node.get("type").asString());
        assertEquals("Heartbeat Interval", node.get("label").asString());
        assertEquals("10000", node.get("value").asString());
        assertEquals("10,000", node.get("displayValue").asString());
        assertFalse(node.has("set"));
    }

    @DisplayName("only the four whitelisted intervals are writable; a plex key is not")
    @Test
    public void testWritableOnlyForWhitelistedKeys() throws Exception {
        String file = "# Mixed\nplex.movie-play-enabled=true\nsse.heartbeat-interval=1000\n"
                + "hue.status-interval=2000\nhue.scene-catalog-interval=3000\ndirigera.status-interval=4000\n";
        JsonNode root = MAPPER.readTree(json(serviceFor(file, new MockEnvironment(), false)));

        assertFalse(item(root, "plex.movie-play-enabled").get("writable").asBoolean());
        for (String key : List.of("sse.heartbeat-interval", "hue.status-interval", "hue.scene-catalog-interval",
                "dirigera.status-interval")) {
            assertTrue(item(root, key).get("writable").asBoolean(), key);
        }
    }

    @DisplayName("section title and editable flag come through")
    @Test
    public void testSectionShape() throws Exception {
        JsonNode root = MAPPER.readTree(json(serviceFor("# Plex Movie\nplex.movie-play-enabled=true\n",
                new MockEnvironment().withProperty("plex.movie-play-enabled", "true"), false)));

        JsonNode section = root.get("sections").get(0);
        assertEquals("Plex Movie", section.get("title").asString());
        assertTrue(section.get("editable").asBoolean());
        assertEquals("boolean", item(root, "plex.movie-play-enabled").get("type").asString());
    }

    @DisplayName("adminTabEnabled reflects the flag both ways")
    @Test
    public void testAdminTabBothWays() {
        assertTrue(serviceFor("", new MockEnvironment(), true).view().adminTabEnabled());
        assertFalse(serviceFor("", new MockEnvironment(), false).view().adminTabEnabled());
    }

    @DisplayName("a credential whose formatted item carries a raw value still serialises without it")
    @Test
    public void testServiceDropsValueEvenIfFormatterLeaksIt() throws Exception {
        PropertiesRepository repository = new PropertiesRepository(new MockEnvironment(),
                new ByteArrayResource("# S\nhue.application-key=x\n".getBytes(StandardCharsets.UTF_8)));
        ConfigurationViewFormatter leaky = new ConfigurationViewFormatter() {
            @Override
            ConfigurationViewItem buildItem(String key, String value) {
                return new ConfigurationViewItem(key, "L", CANARY, CANARY, "text", false, false);
            }
        };
        ConfigSectionsService service = new ConfigSectionsService(
                new ConfigurationViewService(repository, leaky, mock(ApplicationEventPublisher.class)), false);

        String body = json(service);

        assertFalse(body.contains(CANARY), body);
        assertFalse(item(MAPPER.readTree(body), "hue.application-key").get("set").asBoolean());
    }
}
