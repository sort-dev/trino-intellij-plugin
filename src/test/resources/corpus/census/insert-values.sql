-- @stmt docs/src/main/sphinx/admin/resource-groups.md:310
-- global properties
INSERT INTO resource_groups_global_properties (name, value) VALUES ('cpu_quota_period', '1h');

-- @stmt docs/src/main/sphinx/admin/resource-groups.md:311
-- Every row in resource_groups table indicates a resource group.
-- The enviroment name is 'test_environment', make sure it matches `node.environment` in your cluster.
-- The parent-child relationship is indicated by the ID in 'parent' column.

-- create a root group 'global' with NULL parent
INSERT INTO resource_groups (name, soft_memory_limit, hard_physical_data_scan_limit, hard_concurrency_limit, max_queued, scheduling_policy, jmx_export, environment) VALUES ('global', '80%', '50TB', 100, 1000, 'weighted', true, 'test_environment');

-- @stmt docs/src/main/sphinx/admin/resource-groups.md:321
-- 1
-- create two new groups with 'global' as parent
INSERT INTO resource_groups (name, soft_memory_limit, hard_concurrency_limit, max_queued, scheduling_weight, environment, parent) VALUES ('data_definition', '10%', 5, 100, 1, 'test_environment', 1);

-- @stmt docs/src/main/sphinx/admin/resource-groups.md:324
INSERT INTO resource_groups (name, soft_memory_limit, hard_concurrency_limit, max_queued, scheduling_weight, environment, parent) VALUES ('adhoc', '10%', 50, 1, 10, 'test_environment', 1);
