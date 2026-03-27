package org.acme.services.devcontainer;

import java.util.List;
import java.util.Map;

public record DiscoveryResult(
        List<String> languages,
        List<String> tools,
        Map<String, String> versions) {
}
