package org.acme.services.devcontainer;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DevcontainerSpec {

    public String image;
    public String remoteUser;
    public String workspaceFolder;
    public List<String> overrideFeatureInstallOrder;
    public Map<String, Map<String, String>> features;
    public Customizations customizations;
    public List<String> runArgs;
    public Map<String, String> containerEnv;
    public List<String> mounts;
    public String postCreateCommand;
    public String postStartCommand;
    public String waitFor;
    public List<Integer> appPort;

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Customizations {
        public VsCode vscode;
    }

    @RegisterForReflection
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VsCode {
        public List<String> extensions;
    }

    public void setContainerEnvFromEntries(List<Map.Entry<String, String>> entries) {
        containerEnv = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            containerEnv.put(entry.getKey(), entry.getValue());
        }
    }

    public void setVscodeExtensions(List<String> extensions) {
        if (extensions != null && !extensions.isEmpty()) {
            customizations = new Customizations();
            customizations.vscode = new VsCode();
            customizations.vscode.extensions = extensions;
        }
    }
}
