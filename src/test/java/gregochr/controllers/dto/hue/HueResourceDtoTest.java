package gregochr.controllers.dto.hue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Hue Resource DTO Tests")
public class HueResourceDtoTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String HUE_RESOURCE_JSON = """
            {
              "errors": [],
              "data": [
                {
                  "id": "f4204bba-a862-4766-8e98-c967fa5aabd2",
                  "id_v1": "/lights/4",
                  "owner": { "rid": "aab1a0e3-75a9-4e6a-8341-39065a9e8de7", "rtype": "device" },
                  "metadata": { "name": "Standing Lamp", "archetype": "floor_lantern" },
                  "type": "light",
                  "extra_field_that_should_be_ignored": true
                },
                {
                  "id": "99cfbe04-4fc5-4639-ab83-76ba31d44b2e",
                  "type": "device",
                  "metadata": { "name": "Hue Play 1", "archetype": "hue_play", "category": "primary" },
                  "services": [
                    { "rid": "f4204bba-a862-4766-8e98-c967fa5aabd2", "rtype": "light" },
                    { "rid": "abc12345-1111-2222-3333-444455556666", "rtype": "zigbee_connectivity" }
                  ]
                },
                {
                  "id": "dc65eea1-bb29-4899-b4f3-de6aeff9ac01",
                  "type": "room",
                  "metadata": { "name": "Living Room", "archetype": "living_room" },
                  "children": [
                    { "rid": "99cfbe04-4fc5-4639-ab83-76ba31d44b2e", "rtype": "device" },
                    { "rid": "aab1a0e3-75a9-4e6a-8341-39065a9e8de7", "rtype": "device" }
                  ],
                  "services": [
                    { "rid": "bbb22222-3333-4444-5555-666677778888", "rtype": "grouped_light" }
                  ]
                },
                {
                  "id": "aaaa1111-2222-3333-4444-555566667777",
                  "type": "bridge",
                  "id_v1": "",
                  "owner": { "rid": "bbbb2222-3333-4444-5555-666677778888", "rtype": "bridge" }
                },
                {
                  "id": "e1b2c3d4-5678-9abc-def0-111122223333",
                  "id_v1": "/scenes/ABcdEf012",
                  "type": "scene",
                  "metadata": { "name": "Movie Time" },
                  "group": { "rid": "dc65eea1-bb29-4899-b4f3-de6aeff9ac01", "rtype": "room" },
                  "actions": [
                    {
                      "target": { "rid": "f4204bba-a862-4766-8e98-c967fa5aabd2", "rtype": "light" },
                      "action": { "on": { "on": true }, "dimming": { "brightness": 40.0 }, "color_temperature": { "mirek": 346 } }
                    }
                  ],
                  "palette": {
                    "color": [],
                    "dimming": [{ "brightness": 40.0 }],
                    "color_temperature": [{ "color_temperature": { "mirek": 346 }, "dimming": { "brightness": 40.0 } }]
                  },
                  "speed": 0.5,
                  "auto_dynamic": false,
                  "status": { "active": "inactive" }
                }
              ]
            }
            """;

    @DisplayName("Deserializes top-level response with errors and data")
    @Test
    public void testDeserializesResponseStructure() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);

        assertNotNull(response);
        assertNotNull(response.errors());
        assertTrue(response.errors().isEmpty());
        assertNotNull(response.data());
        assertEquals(5, response.data().size());
    }

    @DisplayName("Deserializes light resource with id, id_v1, type, and owner")
    @Test
    public void testDeserializesLightResource() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto light = response.data().get(0);

        assertEquals("f4204bba-a862-4766-8e98-c967fa5aabd2", light.id());
        assertEquals("/lights/4", light.idV1());
        assertEquals("light", light.type());
        assertNotNull(light.owner());
        assertEquals("aab1a0e3-75a9-4e6a-8341-39065a9e8de7", light.owner().rid());
        assertEquals("device", light.owner().rtype());
    }

    @DisplayName("Deserializes metadata with name and archetype")
    @Test
    public void testDeserializesMetadata() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto light = response.data().get(0);

        assertNotNull(light.metadata());
        assertEquals("Standing Lamp", light.metadata().name());
        assertEquals("floor_lantern", light.metadata().archetype());
    }

    @DisplayName("Deserializes metadata with category field")
    @Test
    public void testDeserializesMetadataCategory() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto device = response.data().get(1);

        assertEquals("primary", device.metadata().category());
    }

    @DisplayName("Deserializes device services list")
    @Test
    public void testDeserializesServices() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto device = response.data().get(1);

        assertNotNull(device.services());
        assertEquals(2, device.services().size());
        assertEquals("f4204bba-a862-4766-8e98-c967fa5aabd2", device.services().get(0).rid());
        assertEquals("light", device.services().get(0).rtype());
    }

    @DisplayName("Deserializes room children list")
    @Test
    public void testDeserializesChildren() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto room = response.data().get(2);

        assertEquals("room", room.type());
        assertNotNull(room.children());
        assertEquals(2, room.children().size());
        assertEquals("device", room.children().get(0).rtype());
    }

    @DisplayName("Ignores unknown fields in resource JSON")
    @Test
    public void testIgnoresUnknownFields() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto light = response.data().get(0);

        // The light resource has "extra_field_that_should_be_ignored" — parsing should not fail
        assertEquals("light", light.type());
    }

    @DisplayName("Deserializes scene resource ignoring actions, palette, speed, and status")
    @Test
    public void testDeserializesSceneResource() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto scene = response.data().get(4);

        assertEquals("scene", scene.type());
        assertEquals("e1b2c3d4-5678-9abc-def0-111122223333", scene.id());
        assertEquals("/scenes/ABcdEf012", scene.idV1());
        assertNotNull(scene.metadata());
        assertEquals("Movie Time", scene.metadata().name());
        // Scene-specific fields (group, actions, palette, speed, auto_dynamic, status) are ignored
        assertNull(scene.owner());
        assertNull(scene.services());
        assertNull(scene.children());
    }

    @DisplayName("Nullable fields are null when absent from JSON")
    @Test
    public void testNullableFieldsWhenAbsent() throws JsonProcessingException {
        HueResourceResponseDto response = OBJECT_MAPPER.readValue(HUE_RESOURCE_JSON, HueResourceResponseDto.class);
        HueResourceDto room = response.data().get(2);

        // Room has no owner or id_v1
        assertNull(room.owner());
        assertNull(room.idV1());
    }
}
