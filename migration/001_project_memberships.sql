-- MySQL 8. Run once with the application stopped, after a verified backup.
-- DDL commits implicitly in MySQL: this entire file is NOT atomic.
CREATE TABLE IF NOT EXISTS project_memberships (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL,
 member_id BIGINT NOT NULL,
 role VARCHAR(20) NOT NULL,
 joined_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_project_member UNIQUE (project_id, member_id),
 CONSTRAINT fk_membership_project FOREIGN KEY (project_id) REFERENCES projects(id),
 CONSTRAINT fk_membership_member FOREIGN KEY (member_id) REFERENCES members(id),
 CONSTRAINT ck_membership_role CHECK (role IN ('LEAD','CO_MANAGER','MEMBER'))
) ENGINE=InnoDB;

START TRANSACTION;
-- Only bootstrap projects that have no memberships. Never restore privileges
-- to a creator who was intentionally removed/demoted after migration.
INSERT INTO project_memberships(project_id, member_id, role, joined_at)
SELECT p.id, p.creator_id, 'LEAD', COALESCE(p.created_at, CURRENT_TIMESTAMP)
FROM projects p JOIN members m ON m.id = p.creator_id
WHERE NOT EXISTS (SELECT 1 FROM project_memberships pm WHERE pm.project_id = p.id);
COMMIT;

-- These preserved rows remain inaccessible. Investigate; do not assign arbitrarily.
SELECT p.id AS unresolved_project_id, p.creator_id
FROM projects p LEFT JOIN members m ON m.id = p.creator_id
WHERE m.id IS NULL;

-- Must be empty for all migrated/active projects.
SELECT project_id FROM project_memberships
GROUP BY project_id HAVING SUM(CASE WHEN role = 'LEAD' THEN 1 ELSE 0 END) = 0;
