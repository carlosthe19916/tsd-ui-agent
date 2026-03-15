package org.acme.resources;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
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
import org.acme.services.PlanService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
    TransactionManager transactionManager;

    @ConfigProperty(name = "tsd-agent.discovery.ai.enabled", defaultValue = "true")
    boolean aiDiscoveryEnabled;

    @GET
    public SearchResultDto<TaskDto> list(
            @QueryParam("filterText") String filterText,
            @QueryParam("projectId") List<Long> projectId,
            @QueryParam("status") String status,
            @QueryParam("offset") @DefaultValue("0") @Max(9_000) int offset,
            @QueryParam("limit") @DefaultValue("10") @Max(1_000) int limit,
            @QueryParam("sort_by") List<String> sortBy
    ) {
        StringBuilder query = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        if (filterText != null && !filterText.isBlank()) {
            query.append(" lower(title) like :filterText");
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

        // Auto-populate requirement from task description if not provided
        if (plan.requirement == null || plan.requirement.isBlank()) {
            plan.requirement = (task.description != null && !task.description.isBlank()) ? task.description : task.title;
        }

        // Set discovery status based on AI availability
        if (aiDiscoveryEnabled && task.description != null && !task.description.isBlank()) {
            plan.isRequirementInProgress = true;
        }

        plan.persist();
        task.plan = plan;

        // Trigger async AI enrichment after transaction commits
        if (plan.isRequirementInProgress) {
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
        }

        return Response.status(Response.Status.CREATED)
                .entity(planMapper.toDto(plan))
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
}
