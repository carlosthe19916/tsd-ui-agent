package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.acme.models.jpa.entity.DiscoveryStatus;
import org.acme.models.jpa.entity.TaskContextEntity;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.services.ai.RequirementAiService;
import org.acme.services.discovery.RequirementContext;
import org.acme.services.discovery.RequirementSource;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class RequirementDiscoveryService {

    private static final Logger LOG = Logger.getLogger(RequirementDiscoveryService.class);

    @Inject
    Instance<RequirementSource> sources;

    @Inject
    RequirementAiService aiService;

    public void triggerDiscovery(Long taskId) {
        Thread.startVirtualThread(() -> doDiscovery(taskId));
    }

    void doDiscovery(Long taskId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            // Phase 1: Collect data in a short transaction
            RequirementContext context = QuarkusTransaction.requiringNew().call(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task == null || task.plan == null) {
                    LOG.warnf("Task %d or plan not found during discovery", taskId);
                    return null;
                }

                List<RequirementSource> sortedSources = sources.stream()
                        .filter(s -> s.supports(task))
                        .sorted(Comparator.comparingInt(RequirementSource::priority).reversed())
                        .toList();

                List<RequirementContext.Comment> allComments = new ArrayList<>();
                for (RequirementSource source : sortedSources) {
                    LOG.infof("Fetching comments from source: %s (priority: %d)", source.name(), source.priority());
                    List<RequirementContext.Comment> comments = source.fetchComments(task);
                    allComments.addAll(comments);
                }

                List<String> additionalContexts = TaskContextEntity.<TaskContextEntity>list("task", task)
                        .stream()
                        .filter(ctx -> ctx.content != null && !ctx.content.isBlank())
                        .map(ctx -> ctx.name + ":\n" + ctx.content)
                        .toList();

                return new RequirementContext(
                        task.title,
                        task.description != null ? task.description : "No description provided.",
                        task.type.name(),
                        allComments,
                        additionalContexts
                );
            });

            if (context == null) {
                return;
            }

            // Phase 2: Call AI outside of any transaction
            String commentsText = context.comments().stream()
                    .map(c -> "- " + c.author() + ": " + c.body())
                    .collect(Collectors.joining("\n"));
            if (commentsText.isBlank()) {
                commentsText = "No comments available.";
            }

            String contextText = context.additionalContexts().isEmpty()
                    ? "No additional context."
                    : String.join("\n\n", context.additionalContexts());

            String result = aiService.discoverRequirement(
                    context.taskTitle(),
                    context.sourceType(),
                    context.taskDescription(),
                    commentsText,
                    contextText
            );

            // Phase 3: Store result in a short transaction
            QuarkusTransaction.requiringNew().run(() -> {
                TaskEntity task = TaskEntity.findById(taskId);
                if (task != null && task.plan != null) {
                    task.plan.requirement = result;
                    task.plan.discoveryStatus = DiscoveryStatus.COMPLETED;
                    task.plan.discoveryError = null;
                    task.plan.updatedAt = Instant.now();
                    task.plan.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Discovery failed for task %d", taskId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    TaskEntity task = TaskEntity.findById(taskId);
                    if (task != null && task.plan != null) {
                        task.plan.discoveryStatus = DiscoveryStatus.ERROR;
                        task.plan.discoveryError = e.getMessage();
                        task.plan.updatedAt = Instant.now();
                        task.plan.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to set ERROR status for task %d discovery", taskId);
            }
        } finally {
            requestContext.terminate();
        }
    }
}
