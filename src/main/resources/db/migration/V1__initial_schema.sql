-- V1: Initial schema matching JPA entities
-- Uses IF NOT EXISTS guards for baseline-on-migrate compatibility with existing databases

-- Sequences
CREATE SEQUENCE IF NOT EXISTS credential_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE IF NOT EXISTS project_sequence START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS git_sequence START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS plan_sequence START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS workspace_sequence START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS task_sequence START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS project_git_mapping_sequence START WITH 1 INCREMENT BY 50;

-- credential (no FK dependencies)
CREATE TABLE IF NOT EXISTS credential (
    id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_credential_name UNIQUE (name)
);

-- project (FK to credential)
CREATE TABLE IF NOT EXISTS project (
    id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    api_url VARCHAR(255) NOT NULL,
    query VARCHAR(255),
    type VARCHAR(255) NOT NULL,
    credential_id BIGINT NOT NULL,
    sync_status VARCHAR(255),
    last_sync_at TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_project_credential FOREIGN KEY (credential_id) REFERENCES credential(id)
);

-- git (FK to credential)
CREATE TABLE IF NOT EXISTS git (
    id BIGINT NOT NULL,
    url VARCHAR(255) NOT NULL,
    branch VARCHAR(255) NOT NULL DEFAULT '',
    fork_url VARCHAR(255),
    vendor_type VARCHAR(255),
    credential_id BIGINT,
    is_provisioning_in_progress BOOLEAN,
    provisioning_error TEXT,
    version INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_git_credential FOREIGN KEY (credential_id) REFERENCES credential(id),
    CONSTRAINT uk_git_url_branch UNIQUE (url, branch)
);

-- plan (no FK dependencies)
CREATE TABLE IF NOT EXISTS plan (
    id BIGINT NOT NULL,
    execution_plan TEXT,
    requirement TEXT,
    is_requirement_in_progress BOOLEAN,
    requirement_error TEXT,
    is_execution_plan_in_progress BOOLEAN,
    execution_plan_error TEXT,
    execution_plan_completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    is_plan_generation_in_progress BOOLEAN,
    plan_generation_error TEXT,
    is_change_request_in_progress BOOLEAN,
    change_request_error TEXT,
    change_request_url VARCHAR(255),
    version INTEGER NOT NULL,
    PRIMARY KEY (id)
);

-- workspace (FK to git)
CREATE TABLE IF NOT EXISTS workspace (
    id BIGINT NOT NULL,
    git_id BIGINT,
    is_provisioning_in_progress BOOLEAN,
    provisioning_error TEXT,
    workspace_id VARCHAR(255),
    execution_mode VARCHAR(255),
    version INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_workspace_git FOREIGN KEY (git_id) REFERENCES git(id)
);

-- task (FK to project, plan, workspace)
CREATE TABLE IF NOT EXISTS task (
    id BIGINT NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    url VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(255) NOT NULL,
    external_status VARCHAR(255),
    type VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE,
    project_id BIGINT,
    labels VARCHAR(255),
    plan_id BIGINT,
    workspace_id BIGINT,
    version INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_task_plan FOREIGN KEY (plan_id) REFERENCES plan(id),
    CONSTRAINT fk_task_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT uk_task_external_id_project UNIQUE (external_id, project_id),
    CONSTRAINT uk_task_plan_id UNIQUE (plan_id)
);

-- project_git_mapping (FK to project, git)
CREATE TABLE IF NOT EXISTS project_git_mapping (
    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    git_id BIGINT NOT NULL,
    space VARCHAR(255) NOT NULL,
    labels VARCHAR(255),
    version INTEGER NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pgm_project FOREIGN KEY (project_id) REFERENCES project(id),
    CONSTRAINT fk_pgm_git FOREIGN KEY (git_id) REFERENCES git(id),
    CONSTRAINT uk_pgm_project_git_space UNIQUE (project_id, git_id, space)
);
