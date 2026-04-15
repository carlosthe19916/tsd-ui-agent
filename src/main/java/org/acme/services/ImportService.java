package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import org.acme.dto.CredentialDto;
import org.acme.dto.GitDto;
import org.acme.dto.ImportDto;
import org.acme.dto.ImportResultDto;
import org.acme.dto.ProjectDto;
import org.acme.dto.ProjectGitMappingDto;
import org.acme.mapper.CredentialMapper;
import org.acme.mapper.GitMapper;
import org.acme.mapper.ProjectGitMappingMapper;
import org.acme.mapper.ProjectMapper;
import org.acme.models.jpa.entity.CredentialEntity;
import org.acme.models.jpa.entity.GitEntity;
import org.acme.models.jpa.entity.ProjectEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Transactional
public class ImportService {

    @Inject
    GitService gitService;

    @Inject
    ProjectService projectService;

    @Inject
    CredentialMapper credentialMapper;

    @Inject
    GitMapper gitMapper;

    @Inject
    ProjectMapper projectMapper;

    @Inject
    ProjectGitMappingMapper mappingMapper;

    public ImportResult doImport(ImportDto importDto) {
        Map<String, CredentialEntity> credentialsByName = new HashMap<>();
        List<CredentialDto> createdCredentials = new ArrayList<>();
        List<GitDto> createdGits = new ArrayList<>();
        List<ProjectDto> createdProjects = new ArrayList<>();
        List<Long> projectIdsToSync = new ArrayList<>();

        // 1. Create credentials
        if (importDto.credentials != null) {
            for (ImportDto.CredentialImport ci : importDto.credentials) {
                long count = CredentialEntity.count("name", ci.name);
                if (count > 0) {
                    throw new BadRequestException("Credential with name '" + ci.name + "' already exists");
                }

                CredentialEntity entity = new CredentialEntity();
                entity.name = ci.name;
                entity.token = ci.token;
                entity.persist();

                credentialsByName.put(ci.name, entity);
                createdCredentials.add(credentialMapper.toDto(entity));
            }
        }

        // 2. Create gits
        Map<String, GitEntity> gitsByUrl = new HashMap<>();
        if (importDto.gits != null) {
            for (ImportDto.GitImport gi : importDto.gits) {
                CredentialEntity credential = resolveCredential(gi.credential, credentialsByName);

                GitDto gitDto = new GitDto();
                gitDto.url = gi.url;
                gitDto.branch = gi.branch;
                gitDto.forkUrl = gi.forkUrl;
                gitDto.vendorType = gi.vendorType;
                gitDto.credential = new CredentialDto();
                gitDto.credential.id = credential.id;

                GitEntity entity = gitService.create(gitDto);
                gitsByUrl.put(gi.url, entity);
                createdGits.add(gitMapper.toDto(entity));
            }
        }

        // 3. Create projects and git-mappings
        if (importDto.projects != null) {
            for (ImportDto.ProjectImport pi : importDto.projects) {
                CredentialEntity credential = resolveCredential(pi.credential, credentialsByName);

                ProjectDto projectDto = new ProjectDto();
                projectDto.name = pi.name;
                projectDto.type = pi.type;
                projectDto.apiUrl = pi.apiUrl;
                projectDto.query = pi.query;
                projectDto.credential = new CredentialDto();
                projectDto.credential.id = credential.id;

                ProjectEntity projectEntity = projectService.create(projectDto);
                createdProjects.add(projectMapper.toDto(projectEntity));

                // Create git-mappings for this project
                if (pi.gitMappings != null) {
                    for (ImportDto.GitMappingImport gmi : pi.gitMappings) {
                        GitEntity gitEntity = resolveGit(gmi.gitUrl, gitsByUrl);

                        ProjectGitMappingDto mappingDto = new ProjectGitMappingDto();
                        mappingDto.gitId = gitEntity.id;
                        mappingDto.space = gmi.space;
                        mappingDto.labels = gmi.labels;

                        mappingMapper.toEntity(mappingDto, projectEntity).persist();
                    }
                }

                if (pi.sync) {
                    projectIdsToSync.add(projectEntity.id);
                }
            }
        }

        ImportResultDto resultDto = new ImportResultDto();
        resultDto.credentials = createdCredentials;
        resultDto.gits = createdGits;
        resultDto.projects = createdProjects;

        return new ImportResult(resultDto, projectIdsToSync);
    }

    private CredentialEntity resolveCredential(String name, Map<String, CredentialEntity> importedCredentials) {
        CredentialEntity entity = importedCredentials.get(name);
        if (entity != null) {
            return entity;
        }
        entity = CredentialEntity.find("name", name).firstResult();
        if (entity == null) {
            throw new BadRequestException("Credential '" + name + "' not found");
        }
        return entity;
    }

    private GitEntity resolveGit(String url, Map<String, GitEntity> importedGits) {
        GitEntity entity = importedGits.get(url);
        if (entity != null) {
            return entity;
        }
        entity = GitEntity.find("url", url).firstResult();
        if (entity == null) {
            throw new BadRequestException("Git repository with URL '" + url + "' not found");
        }
        return entity;
    }

    public record ImportResult(ImportResultDto result, List<Long> projectIdsToSync) {}
}
