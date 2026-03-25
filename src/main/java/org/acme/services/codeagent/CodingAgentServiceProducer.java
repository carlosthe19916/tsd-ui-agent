package org.acme.services.codeagent;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CodingAgentServiceProducer {

    @Inject
    @Any
    Instance<CodingAgentService> codingAgentServices;

    @ConfigProperty(name = "tsd-agent.coding-agent")
    CodingAgentType codingAgentType;

    @Produces
    @ApplicationScoped
    public CodingAgentService codingAgentService() {
        Instance<CodingAgentService> selected = codingAgentServices.select(new CodingAgentQualifierLiteral(codingAgentType));
        if (selected.isUnsatisfied()) {
            throw new UnsupportedOperationException(codingAgentType + " coding agent has no CodingAgentService implementation");
        }
        return selected.get();
    }
}
