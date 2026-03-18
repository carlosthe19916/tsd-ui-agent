package org.acme.services.agent;

public interface CodingAgentService {
    String generatePlan(String workdir, String requirement, Long taskId);
}
