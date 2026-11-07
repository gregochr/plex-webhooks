package gregochr.controllers.hue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Hue Scene Catalog Controller Tests")
public class HueSceneCatalogControllerTest {

    @DisplayName("GET /api/hue/scenes returns the scenes and the start and stop selections, summary null if unknown")
    @Test
    public void scenesJson() throws Exception {
        HueSceneCatalog catalog = mock(HueSceneCatalog.class);
        HueSceneSummary movie = new HueSceneSummary("scene-movie", "Movie Night", "room-living", "Living Room",
                "room", 23);
        when(catalog.view()).thenReturn(new HueSceneCatalog.View(List.of(movie),
                new HueSceneCatalog.Selection("scene-movie", movie),
                new HueSceneCatalog.Selection("scene-gone", null)));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HueSceneCatalogController(catalog)).build();

        mockMvc.perform(get("/api/hue/scenes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenes.length()").value(1))
                .andExpect(jsonPath("$.scenes[0].id").value("scene-movie"))
                .andExpect(jsonPath("$.scenes[0].name").value("Movie Night"))
                .andExpect(jsonPath("$.scenes[0].groupId").value("room-living"))
                .andExpect(jsonPath("$.scenes[0].groupName").value("Living Room"))
                .andExpect(jsonPath("$.scenes[0].groupType").value("room"))
                .andExpect(jsonPath("$.scenes[0].level").value(23))
                .andExpect(jsonPath("$.start.id").value("scene-movie"))
                .andExpect(jsonPath("$.start.summary.name").value("Movie Night"))
                .andExpect(jsonPath("$.stop.id").value("scene-gone"))
                .andExpect(jsonPath("$.stop.summary").isEmpty());
    }
}
