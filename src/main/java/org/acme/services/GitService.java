package org.acme.services;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import static org.acme.services.ExecutionOutputBroadcaster.Channel;

import org.acme.dto.GitDto;
import org.acme.mapper.GitMapper;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.WorkspaceEntity;
import org.acme.services.devcontainer.DevcontainerConfigGenerator;
import org.acme.services.devcontainer.EnrichmentService;
import org.acme.services.git.GitManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
public class GitService {

    private static final Logger LOG = Logger.getLogger(GitService.class);

    @Inject
    GitMapper gitMapper;

    @Inject
    WorkspaceService workspaceService;

    @Inject
    TransactionManager transactionManager;

    @Inject
    ExecutionOutputBroadcaster broadcaster;

    @Inject
    GitManager gitManager;

    @Inject
    EnrichmentService enrichmentService;

    @Inject
    DevcontainerConfigGenerator devcontainerConfigGenerator;

    @ConfigProperty(name = "tsd-agent.git.base-dir")
    String baseDir;

    public GitEntity create(GitDto dto) {
        GitEntity entity = gitMapper.toEntity(dto);
        entity.credential = resolveCredential(dto);
        entity.configGit = resolveConfigGit(dto);
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, null);

        entity.isProvisioningInProgress = true;
        entity.persist();

        Long gitEntityId = entity.id;
        broadcaster.start(Channel.GIT, gitEntityId);
        try {
            transactionManager.getTransaction().registerSynchronization(new Synchronization() {
                @Override
                public void beforeCompletion() {}

                @Override
                public void afterCompletion(int status) {
                    if (status == jakarta.transaction.Status.STATUS_COMMITTED) {
                        Thread.startVirtualThread(() -> doProvision(gitEntityId));
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to register git provisioning callback", e);
        }

        return entity;
    }

    void doProvision(Long gitEntityId) {
        ManagedContext requestContext = Arc.container().requestContext();
        requestContext.activate();
        try {
            record GitContext(String url, String branch, String token, String forkUrl, String configGitUrl) {}

            GitContext context = QuarkusTransaction.requiringNew().call(() -> {
                GitEntity entity = GitEntity.findById(gitEntityId);
                if (entity == null) {
                    LOG.warnf("Git entity %d not found during provisioning", gitEntityId);
                    return null;
                }
                return new GitContext(
                        entity.url,
                        entity.branch,
                        entity.credential != null ? entity.credential.token : null,
                        entity.forkUrl,
                        entity.configGit != null ? entity.configGit.url : null
                );
            });

            if (context == null) {
                return;
            }

            String sanitized = GitManager.sanitizeUrl(context.url());
            String branchDir = GitManager.branchDir(context.branch());
            String cloneDir = GitManager.cloneDir(baseDir, context.url(), context.branch());

            if (!Files.isDirectory(Path.of(cloneDir))) {
                broadcaster.publish(Channel.GIT, gitEntityId, "[git] Cloning repository " + context.url() + " (" + branchDir + ")...");
                gitManager.cloneRepository(context.url(), context.branch(), cloneDir, context.token());
                if (context.forkUrl() != null && !context.forkUrl().isBlank()) {
                    broadcaster.publish(Channel.GIT, gitEntityId, "[git] Adding fork remote " + context.forkUrl());
                    gitManager.addForkRemote(cloneDir, context.forkUrl());
                }
            } else {
                broadcaster.publish(Channel.GIT, gitEntityId, "[git] Repository already cloned, pulling latest (" + branchDir + ")...");
                String branch = context.branch() != null && !context.branch().isBlank() ? context.branch() : null;
                gitManager.pullRepository(cloneDir, branch, context.token());
            }

            // Enrichment + base devcontainer.json generation (from the branch-specific clone)
            EnrichmentService.EnrichmentResult enrichment =
                    enrichmentService.enrich(Path.of(cloneDir), line -> broadcaster.publish(Channel.GIT, gitEntityId, line));

            devcontainerConfigGenerator.generateBaseConfig(sanitized, branchDir, Path.of(cloneDir), enrichment);
            broadcaster.publish(Channel.GIT, gitEntityId, "[git] Provisioning complete");

            QuarkusTransaction.requiringNew().run(() -> {
                GitEntity entity = GitEntity.findById(gitEntityId);
                if (entity != null) {
                    entity.isProvisioningInProgress = false;
                    entity.persist();
                }
            });
        } catch (Exception e) {
            LOG.errorf(e, "Failed to provision git %d", gitEntityId);
            try {
                QuarkusTransaction.requiringNew().run(() -> {
                    GitEntity entity = GitEntity.findById(gitEntityId);
                    if (entity != null) {
                        entity.provisioningError = e.getMessage();
                        entity.isProvisioningInProgress = false;
                        entity.persist();
                    }
                });
            } catch (Exception inner) {
                LOG.errorf(inner, "Failed to record provisioning error for git %d", gitEntityId);
            }
        } finally {
            broadcaster.complete(Channel.GIT, gitEntityId);
            requestContext.terminate();
        }
    }

    public GitEntity update(GitDto dto, GitEntity entity) {
        gitMapper.updateEntity(dto, entity);
        entity.credential = resolveCredential(dto);
        entity.configGit = resolveConfigGit(dto);
        normalizeBranch(entity);
        checkDuplicate(entity.url, entity.branch, entity.id);

        entity.persist();
        return entity;
    }

    public void delete(GitEntity entity) {
        List<WorkspaceEntity> workspaces = WorkspaceEntity.list("git", entity);
        for (WorkspaceEntity ws : workspaces) {
            workspaceService.delete(ws);
        }

        // Clean up clone directory
        String cloneDir = GitManager.cloneDir(baseDir, entity.url, entity.branch);
        try {
            gitManager.deleteClonedDirectory(cloneDir);
        } catch (Exception e) {
            LOG.warnf("Failed to clean clone directory %s: %s", cloneDir, e.getMessage());
        }

        entity.delete();
    }

    private GitEntity resolveConfigGit(GitDto dto) {
        if (dto.configGit == null || dto.configGit.id == null) {
            return null;
        }
        return (GitEntity) GitEntity.findByIdOptional(dto.configGit.id)
                .orElseThrow(() -> new NotFoundException("Config git repository not found"));
    }

    private CredentialEntity resolveCredential(GitDto dto) {
        if (dto.credential == null || dto.credential.id == null) {
            return null;
        }
        return (CredentialEntity) CredentialEntity.findByIdOptional(dto.credential.id)
                .orElseThrow(() -> new NotFoundException("Credential not found"));
    }

    private void normalizeBranch(GitEntity entity) {
        if (entity.branch == null || entity.branch.isBlank()) {
            entity.branch = "";
        }
    }

    private void checkDuplicate(String url, String branch, Long excludeId) {
        GitEntity existing = GitEntity.find("url = ?1 and branch = ?2", url, branch).firstResult();
        if (existing != null && !existing.id.equals(excludeId)) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "A git repository with this URL and branch already exists"))
                            .build()
            );
        }
    }
}
