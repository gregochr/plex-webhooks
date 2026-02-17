package gregochr.services.config;

import java.util.List;
import java.util.Map;

record PropertySection(String title, List<Map.Entry<String, String>> entries) {
}
