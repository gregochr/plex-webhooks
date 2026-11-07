package gregochr.controllers;

import gregochr.services.config.ConfigService;
import gregochr.services.config.ConfigurationViewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final ConfigService configService;
    private final ConfigurationViewService configurationViewService;
    private final boolean adminTabEnabled;

    public DashboardController(ConfigService configService,
                               ConfigurationViewService configurationViewService,
                               @Value("${dashboard.admin-tab-enabled:false}") boolean adminTabEnabled) {
        this.configService = configService;
        this.configurationViewService = configurationViewService;
        this.adminTabEnabled = adminTabEnabled;
    }

    @GetMapping("/classic")
    public String dashboard(Model model) {
        model.addAttribute("adminTabEnabled", adminTabEnabled);
        model.addAttribute("hueIntegrationEnabled", configService.isHueIntegrationEnabled());
        model.addAttribute("configSections", configurationViewService.getConfigurationSections());
        return "classic";
    }
}
