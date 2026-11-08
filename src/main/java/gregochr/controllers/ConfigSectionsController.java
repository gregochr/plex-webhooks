package gregochr.controllers;

import gregochr.services.config.ConfigSectionsService;
import gregochr.services.config.ConfigSectionsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The public, credential-free settings view the React dashboard renders. */
@RestController
@RequestMapping("/api/config/sections")
public class ConfigSectionsController {

    private final ConfigSectionsService service;

    public ConfigSectionsController(ConfigSectionsService service) {
        this.service = service;
    }

    /**
     * The settings sections, with credentials reduced to set / not set.
     *
     * @return the settings view
     */
    @GetMapping
    public ConfigSectionsView getSections() {
        return service.view();
    }
}
