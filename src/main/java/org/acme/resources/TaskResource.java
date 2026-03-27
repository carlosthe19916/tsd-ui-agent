package org.acme.resources;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.transaction.Transactional.TxType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.dto.PlanDto;
import org.acme.dto.SearchResultDto;
import org.acme.dto.TaskDto;
import org.acme.mapper.PlanMapper;
import org.acme.mapper.TaskMapper;
import org.acme.models.jpa.entity.PlanEntity;
import org.acme.models.jpa.entity.TaskEntity;
import org.acme.models.jpa.entity.TaskStatus;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.ChangeRequestService;
import static org.acme.services.ExecutionOutputBroadcaster.Channel;

import org.acme.services.ExecutionOutputBroadcaster;
import org.acme.services.PlanService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Transactional
@ApplicationScoped
@Path("/tasks")
@Produces(MediaType.APPLICATION_JSON)
public class TaskResource {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "title", "status", "createdAt", "updatedAt", "project.name"
    );

    @Inject
    TaskMapper taskMapper;

    @Inject
    PlanMapper planMapper;

    @Inject
    PlanService planService;

    @Inject
    ChangeRequestService changeRequestService;

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @Inject
    TransactionManager transactionManager;

    @ConfigProperty(name = "tsd-agent.discovery.ai.enabled")
    boolean aiDiscoveryEnabled;

    @GET
    public SearchResultDto<TaskDto> list(
            @QueryParam("filterText") String filterText,
            @QueryParam("projectId") List<Long> projectId,
            @QueryParam("status") String status,
            @QueryParam("offset") @DefaultValue("0") @Max(9_000) int offset,
            @QueryParam("limit") @DefaultValue("10") @Max(1_000) int limit,
            @QueryParam("sort_by") List<String> sortBy,
            @QueryParam("hasWorkspace") Boolean hasWorkspace
    ) {
        StringBuilder query = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        if (filterText != null && !filterText.isBlank()) {
            query.append(" (lower(title) like :filterText or lower(externalId) like :filterText)");
            params.put("filterText", "%" + filterText.toLowerCase() + "%");
        }

        if (projectId != null && !projectId.isEmpty()) {
            if (!query.isEmpty()) query.append(" and");
            query.append(" project.id in :projectId");
            params.put("projectId", projectId);
        }

        if (status != null && !status.isBlank()) {
            if (!query.isEmpty()) query.append(" and");
            query.append(" status = :status");
            params.put("status", TaskStatus.valueOf(status));
        }

        if (hasWorkspace != null) {
            if (!query.isEmpty()) query.append(" and");
            if (hasWorkspace) {
                query.append(" workspace is not null");
            } else {
                query.append(" workspace is null");
            }
        }

        Sort sort = buildSort(sortBy);
        String jpql = query.toString();

        long count = TaskEntity.count(jpql, params);

        List<TaskDto> data;
        if (sort != null) {
            data = TaskEntity.<TaskEntity>find(jpql, sort, params)
                    .range(offset, offset + limit - 1)
                    .stream()
                    .map(taskMapper::toDto)
                    .collect(Collectors.toList());
        } else {
            data = TaskEntity.<TaskEntity>find(jpql, params)
                    .range(offset, offset + limit - 1)
                    .stream()
                    .map(taskMapper::toDto)
                    .collect(Collectors.toList());
        }

        SearchResultDto<TaskDto> result = new SearchResultDto<>();
        result.meta = new SearchResultDto.Meta();
        result.meta.offset = offset;
        result.meta.limit = limit;
        result.meta.count = count;
        result.data = data;

        return result;
    }

    private Sort buildSort(List<String> sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return null;
        }

        Sort sort = null;
        for (String s : sortBy) {
            String[] parts = s.split(":");
            String field = parts[0];
            if (!SORTABLE_FIELDS.contains(field)) {
                continue;
            }
            Sort.Direction direction = Sort.Direction.Ascending;
            if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1])) {
                direction = Sort.Direction.Descending;
            }
            if (sort == null) {
                sort = Sort.by(field, direction);
            } else {
                sort.and(field, direction);
            }
        }
        return sort;
    }

    @GET
    @Path("/{taskId}")
    public TaskDto getTask(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        return taskMapper.toDto(task);
    }

    @PATCH
    @Path("/{taskId}")
    public TaskDto patchTask(@PathParam("taskId") Long taskId, TaskDto dto) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (dto.workspace != null) {
            if (dto.workspace.id != null) {
                task.workspace = WorkspaceEntity.findById(dto.workspace.id);
            } else {
                task.workspace = null;
            }
        }
        return taskMapper.toDto(task);
    }

    // Plan sub-resource endpoints

    @GET
    @Path("/{taskId}/plan")
    public PlanDto getPlan(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException();
        }
        return planMapper.toDto(task.plan);
    }

    @POST
    @Path("/{taskId}/plan")
    public Response createPlan(@PathParam("taskId") Long taskId, @Valid PlanDto dto) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan != null) {
            return Response.status(Response.Status.CONFLICT).build();
        }
        PlanEntity plan = planMapper.toEntity(dto);

        // Auto-populate requirement from task title and description if not provided
        if (plan.requirement == null || plan.requirement.isBlank()) {
            plan.requirement = buildInitialRequirement(task);
        }

        plan.persist();
        task.plan = plan;

        return Response.status(Response.Status.CREATED)
                .entity(planMapper.toDto(plan))
                .build();
    }

    @POST
    @Path("/{taskId}/plan/run-all")
    public Response runAllPhases(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (!aiDiscoveryEnabled) {
            throw new BadRequestException("AI discovery is not enabled");
        }
        if (task.workspace == null) {
            throw new BadRequestException("Task has no workspace configuration");
        }
        if (task.workspace.isProvisioningInProgress) {
            throw new BadRequestException("Workspace provisioning is still in progress");
        }
        if (task.workspace.provisioningError != null) {
            throw new BadRequestException("Workspace provisioning failed: " + task.workspace.provisioningError);
        }
        if (task.workspace.workspaceId == null) {
            throw new BadRequestException("Workspace has not been provisioned");
        }

        // Create plan if it does not exist
        if (task.plan == null) {
            PlanEntity plan = new PlanEntity();
            plan.requirement = buildInitialRequirement(task);
            plan.createdAt = java.time.Instant.now();
            plan.updatedAt = plan.createdAt;
            plan.persist();
            task.plan = plan;
        }

        // Concurrency guard: if any phase is already in progress, return current state
        if (task.plan.isRequirementInProgress || task.plan.isPlanGenerationInProgress
                || task.plan.isExecutionPlanInProgress || task.plan.isChangeRequestInProgress) {
            return Response.status(Response.Status.ACCEPTED)
                    .entity(planMapper.toDto(task.plan))
                    .build();
        }

        task.plan.isRequirementInProgress = true;
        task.plan.requirementError = null;

        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        planService.triggerFullPipeline(taskId);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register full pipeline", e);
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(planMapper.toDto(task.plan))
                .build();
    }

    @POST
    @Path("/{taskId}/plan/enrich-requirement")
    public Response enrichRequirement(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException("Task has no plan");
        }
        if (!aiDiscoveryEnabled) {
            throw new BadRequestException("AI discovery is not enabled");
        }

        // Concurrency guard
        if (task.plan.isRequirementInProgress) {
            return Response.status(Response.Status.ACCEPTED)
                    .entity(planMapper.toDto(task.plan))
                    .build();
        }

        task.plan.isRequirementInProgress = true;
        task.plan.requirementError = null;

        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        planService.triggerRequirementEnrichment(taskId);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register requirement AI enrichment", e);
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(planMapper.toDto(task.plan))
                .build();
    }

    @PUT
    @Path("/{taskId}/plan")
    public PlanDto updatePlan(@PathParam("taskId") Long taskId, @Valid PlanDto dto) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException();
        }
        planMapper.updateEntity(dto, task.plan);
        return planMapper.toDto(task.plan);
    }

    @PATCH
    @Path("/{taskId}/plan")
    public PlanDto patchPlan(@PathParam("taskId") Long taskId, PlanDto dto) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException();
        }
        planMapper.patchEntity(dto, task.plan);
        return planMapper.toDto(task.plan);
    }

    @DELETE
    @Path("/{taskId}/plan")
    public Response deletePlan(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException();
        }
        PlanEntity plan = task.plan;
        task.plan = null;
        plan.delete();
        return Response.noContent().build();
    }

    @POST
    @Path("/{taskId}/plan/generate-plan")
    public Response generatePlan(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException("Task has no plan");
        }
        if (!aiDiscoveryEnabled) {
            throw new BadRequestException("AI discovery is not enabled");
        }
        if (task.workspace == null) {
            throw new BadRequestException("Task has no workspace configuration");
        }
        if (task.workspace.isProvisioningInProgress) {
            throw new BadRequestException("Workspace provisioning is still in progress");
        }
        if (task.workspace.provisioningError != null) {
            throw new BadRequestException("Workspace provisioning failed: " + task.workspace.provisioningError);
        }
        if (task.workspace.workspaceId == null) {
            throw new BadRequestException("Workspace has not been provisioned");
        }
        if (task.plan.requirement == null || task.plan.requirement.isBlank()) {
            throw new BadRequestException("Plan has no requirement");
        }

        // Concurrency guard
        if (task.plan.isPlanGenerationInProgress) {
            return Response.status(Response.Status.ACCEPTED)
                    .entity(planMapper.toDto(task.plan))
                    .build();
        }

        task.plan.isPlanGenerationInProgress = true;
        task.plan.planGenerationError = null;

        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        planService.triggerPlanGeneration(taskId);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register plan generation", e);
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(planMapper.toDto(task.plan))
                .build();
    }

    @POST
    @Path("/{taskId}/plan/execute")
    public Response executePlan(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException("Task has no plan");
        }
        if (task.workspace == null) {
            throw new BadRequestException("Task has no workspace configuration");
        }
        if (task.plan.plan == null || task.plan.plan.isBlank()) {
            throw new BadRequestException("Plan has no execution plan text");
        }

        // Concurrency guard: if already in progress, return current state
        if (task.plan.isExecutionPlanInProgress) {
            return Response.status(Response.Status.ACCEPTED)
                    .entity(planMapper.toDto(task.plan))
                    .build();
        }

        task.plan.isExecutionPlanInProgress = true;
        task.plan.executionPlanError = null;

        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        planService.triggerPlanExecution(taskId);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register plan execution", e);
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(planMapper.toDto(task.plan))
                .build();
    }

    @POST
    @Path("/{taskId}/plan/change-request")
    public Response createChangeRequest(@PathParam("taskId") Long taskId) {
        TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                .orElseThrow(NotFoundException::new);
        if (task.plan == null) {
            throw new NotFoundException("Task has no plan");
        }
        if (task.workspace == null) {
            throw new BadRequestException("Task has no workspace configuration");
        }
        if (task.workspace.git == null) {
            throw new BadRequestException("Workspace has no git configuration");
        }
        if (task.workspace.git.credential == null) {
            throw new BadRequestException("Git configuration has no credential for PR/MR creation");
        }
        if (task.plan.executionPlanCompletedAt == null) {
            throw new BadRequestException("Plan execution has not completed");
        }

        // If already has a URL, return it
        if (task.plan.changeRequestUrl != null) {
            return Response.ok(planMapper.toDto(task.plan)).build();
        }

        // Concurrency guard
        if (task.plan.isChangeRequestInProgress) {
            return Response.status(Response.Status.ACCEPTED)
                    .entity(planMapper.toDto(task.plan))
                    .build();
        }

        task.plan.isChangeRequestInProgress = true;
        task.plan.changeRequestError = null;

        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        changeRequestService.triggerChangeRequest(taskId);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register change request", e);
        }

        return Response.status(Response.Status.ACCEPTED)
                .entity(planMapper.toDto(task.plan))
                .build();
    }

    private static String buildInitialRequirement(TaskEntity task) {
        boolean hasDescription = task.description != null && !task.description.isBlank();
        if (hasDescription) {
            return task.title + "\n\n" + task.description;
        }
        return task.title;
    }

    @GET
    @Path("/{taskId}/plan/output")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Transactional(TxType.NOT_SUPPORTED)
    public Multi<String> streamOutput(@PathParam("taskId") Long taskId) {
        return Uni.createFrom().item(() -> {
                    TaskEntity task = (TaskEntity) TaskEntity.findByIdOptional(taskId)
                            .orElseThrow(NotFoundException::new);
                    if (task.plan == null) {
                        throw new NotFoundException("Task has no plan");
                    }
                    return taskId;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(id -> broadcaster.subscribe(Channel.TASK, id));
    }

}
