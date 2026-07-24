-- @stmt docs/src/main/sphinx/develop/supporting-merge.md:40
MERGE INTO accounts t USING monthly_accounts_update s
    ON (t.customer = s.customer)
    WHEN MATCHED AND s.address = 'Berkeley' THEN
        DELETE
    WHEN MATCHED AND s.customer = 'Joe Shmoe' THEN
        UPDATE SET purchases = purchases + 100.0
    WHEN MATCHED THEN
        UPDATE
            SET purchases = s.purchases + t.purchases, address = s.address
    WHEN NOT MATCHED THEN
        INSERT (customer, purchases, address)
            VALUES (s.customer, s.purchases, s.address);

-- @stmt docs/src/main/sphinx/sql/merge.md:64
MERGE INTO accounts t USING monthly_accounts_update s
    ON t.customer = s.customer
    WHEN MATCHED
        THEN DELETE;

-- @stmt docs/src/main/sphinx/sql/merge.md:74
MERGE INTO accounts t USING monthly_accounts_update s
    ON (t.customer = s.customer)
    WHEN MATCHED
        THEN UPDATE SET purchases = s.purchases + t.purchases
    WHEN NOT MATCHED
        THEN INSERT (customer, purchases, address)
              VALUES(s.customer, s.purchases, s.address);

-- @stmt docs/src/main/sphinx/sql/merge.md:89
MERGE INTO accounts t USING monthly_accounts_update s
    ON (t.customer = s.customer)
    WHEN MATCHED AND s.address = 'Centreville'
        THEN DELETE
    WHEN MATCHED
        THEN UPDATE
            SET purchases = s.purchases + t.purchases, address = s.address
    WHEN NOT MATCHED
        THEN INSERT (customer, purchases, address)
              VALUES(s.customer, s.purchases, s.address);
