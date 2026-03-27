package org.acme.services.ai;

import dev.langchain4j.agent.tool.Tool;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.ChangeRequestService;
import org.acme.services.PlanService;
import org.acme.services.codeagent.CodingAgentService;
import org.jboss.logging.Logger;

@ApplicationScoped
public class CodingAgentTool {

    private static final Logger LOG = Logger.getLogger(CodingAgentTool.class);

    @Inject
    TaskChatContext chatContext;

    @Inject
    CodingAgentService codingAgentService;

    @Inject
    PlanService planService;

    @Inject
    ChangeRequestService changeRequestService;

    @Tool("Execute a code change in the workspace. Use this when the user asks to modify, create, fix, or refactor code. The instruction should describe what to change.")
    @Blocking
    String executeCodeChange(String instruction) {
        if (chatContext.workspace == null) {
            return "Error: No workspace is provisioned for this task. Please create and start a workspace first.";
        }
        try {
            return codingAgentService.chat(chatContext.workspace, instruction, chatContext.taskId);
        } catch (Exception e) {
            LOG.errorf(e, "executeCodeChange failed for task %d", chatContext.taskId);
            return "Error executing code change: " + e.getMessage();
        }
    }

    @Tool("Ask a question about the codebase without making changes. Use this when the user wants to understand code, find files, or get explanations about the project.")
    @Blocking
    String askAboutCode(String question) {
        if (chatContext.workspace == null) {
            return "Error: No workspace is provisioned for this task. Please create and start a workspace first.";
        }
        try {
            return codingAgentService.chatReadOnly(chatContext.workspace, question, chatContext.taskId);
        } catch (Exception e) {
            LOG.errorf(e, "askAboutCode failed for task %d", chatContext.taskId);
            return "Error querying codebase: " + e.getMessage();
        }
    }

    @Tool("Get the current requirement text for this task")
    String getRequirement() {
        TaskEntity task = QuarkusTransaction.requiringNew()
                .call(() -> TaskEntity.findById(chatContext.taskId));
        if (task == null || task.plan == null || task.plan.requirement == null) {
            return "No requirement defined yet.";
        }
        return task.plan.requirement;
    }

    @Tool("Get the current implementation plan text for this task")
    String getPlan() {
        TaskEntity task = QuarkusTransaction.requiringNew()
                .call(() -> TaskEntity.findById(chatContext.taskId));
        if (task == null || task.plan == null || task.plan.plan == null) {
            return "No plan generated yet.";
        }
        return task.plan.plan;
    }

    @Tool("Enrich the requirement with AI analysis of the issue, comments, and labels from the external tracker")
    @Blocking
    String enrichRequirement() {
        try {
            planService.doRequirementEnrichment(chatContext.taskId);
            TaskEntity task = QuarkusTransaction.requiringNew()
                    .call(() -> TaskEntity.findById(chatContext.taskId));
            if (task != null && task.plan != null && task.plan.requirement != null) {
                return "Requirement enriched successfully:\n\n" + task.plan.requirement;
            }
            return "Requirement enrichment completed but no requirement text was produced.";
        } catch (Exception e) {
            LOG.errorf(e, "enrichRequirement failed for task %d", chatContext.taskId);
            return "Error enriching requirement: " + e.getMessage();
        }
    }

    @Tool("Generate an implementation plan based on the current requirement by analyzing the codebase")
    @Blocking
    String generatePlan() {
        if (chatContext.workspace == null) {
            return "Error: No workspace is provisioned for this task. Please create and start a workspace first.";
        }
        try {
            planService.doPlanGeneration(chatContext.taskId);
            TaskEntity task = QuarkusTransaction.requiringNew()
                    .call(() -> TaskEntity.findById(chatContext.taskId));
            if (task != null && task.plan != null && task.plan.plan != null) {
                return "Plan generated successfully:\n\n" + task.plan.plan;
            }
            return "Plan generation completed but no plan text was produced.";
        } catch (Exception e) {
            LOG.errorf(e, "generatePlan failed for task %d", chatContext.taskId);
            return "Error generating plan: " + e.getMessage();
        }
    }

    @Tool("Execute the implementation plan - the coding agent will make the actual code changes in the workspace")
    @Blocking
    String executePlan() {
        if (chatContext.workspace == null) {
            return "Error: No workspace is provisioned for this task. Please create and start a workspace first.";
        }
        try {
            planService.doPlanExecution(chatContext.taskId);
            return "Plan execution completed successfully. The code changes have been applied in the workspace.";
        } catch (Exception e) {
            LOG.errorf(e, "executePlan failed for task %d", chatContext.taskId);
            return "Error executing plan: " + e.getMessage();
        }
    }

    @Tool("Create a pull request or change request from the changes made in the workspace")
    @Blocking
    String createChangeRequest() {
        try {
            changeRequestService.doChangeRequest(chatContext.taskId);
            TaskEntity task = QuarkusTransaction.requiringNew()
                    .call(() -> TaskEntity.findById(chatContext.taskId));
            if (task != null && task.plan != null && task.plan.changeRequestUrl != null) {
                return "Pull request created: " + task.plan.changeRequestUrl;
            }
            return "Change request process completed.";
        } catch (Exception e) {
            LOG.errorf(e, "createChangeRequest failed for task %d", chatContext.taskId);
            return "Error creating change request: " + e.getMessage();
        }
    }
}
