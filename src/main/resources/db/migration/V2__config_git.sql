ALTER TABLE git ADD COLUMN IF NOT EXISTS config_git_id BIGINT;
ALTER TABLE git ADD CONSTRAINT fk_git_config_git FOREIGN KEY (config_git_id) REFERENCES git(id);
