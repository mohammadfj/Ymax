-- Ymax RLS DDL — ADR-001, applied only after PO sign-off + 0 active calls
-- Run as superuser (postgres) against the `callcenter` DB.
-- Never run the app's `callcenter` role through this file.
--
-- Backup before running:
--   /usr/local/pgsql16/bin/pg_dump -h 127.0.0.1 -U callcenter -d callcenter > /root/backups/pre-rls-$(date +%F).sql

\echo 'Applying RLS on tenant-scoped tables...'

-- 1. Enable RLS on all tenant-scoped tables
ALTER TABLE calls        ENABLE ROW LEVEL SECURITY;
ALTER TABLE visitors     ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE surveys      ENABLE ROW LEVEL SECURITY;
ALTER TABLE call_events  ENABLE ROW LEVEL SECURITY;
ALTER TABLE op_events    ENABLE ROW LEVEL SECURITY;
ALTER TABLE callbacks    ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_groups   ENABLE ROW LEVEL SECURITY;

-- 2. RLS policies (use app.tenant_id GUC)
DO $$
DECLARE
  t TEXT;
BEGIN
  FOREACH t IN ARRAY ARRAY['calls','visitors','chat_messages','surveys','call_events','op_events','callbacks','user_groups']
  LOOP
    EXECUTE format('
      DROP POLICY IF EXISTS tenant_isolation ON %I;
      CREATE POLICY tenant_isolation ON %I
        USING (tenant_id = current_setting(''app.tenant_id'')::int);
    ', t, t);
    RAISE NOTICE 'Created RLS policy on %', t;
  END LOOP;
END
$$;

-- 3. The app's callcenter role must NOT bypass RLS
ALTER ROLE callcenter NOBYPASSRLS;

-- 4. Verify RLS is active
SELECT relname, relrowsecurity
FROM pg_class
WHERE relname IN ('calls','visitors','chat_messages','surveys','call_events','op_events','callbacks','user_groups')
ORDER BY relname;

\echo 'RLS DDL applied. Rollback: DROP POLICY tenant_isolation ON <table>; then ALTER ROLE callcenter BYPASSRLS;'
