package gregochr.controllers.hue;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only view of the scene catalog. It serves the cache and never calls the bridge. */
@RestController
@RequestMapping("/api/hue")
public class HueSceneCatalogController {
    private final HueSceneCatalog hueSceneCatalog;

    public HueSceneCatalogController(HueSceneCatalog hueSceneCatalog) {
        this.hueSceneCatalog = hueSceneCatalog;
    }

    @GetMapping("/scenes")
    public HueSceneCatalog.View getScenes() {
        return hueSceneCatalog.view();
    }
}
