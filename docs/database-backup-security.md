# Database backup security and recovery

This runbook describes controls for the PostgreSQL database declared in `render.yaml`. That Blueprint is configured as a seeded synthetic demo. A real deployment must keep demo reset and public demo login off, and use its organization-approved data handling and retention policy.

## Current safeguards and gaps

Render states that paid Postgres instances and their backups are encrypted at rest with AES-256, and external database connections use Render-managed TLS. Paid instances also have continuous point-in-time recovery (PITR); its window depends on the Render workspace plan. Dashboard logical exports are retained for seven days. A PITR recovery creates a separate database so it can be checked before services are switched over. See [Render encryption](https://render.com/docs/postgresql-creating-connecting#encryption) and [Render recovery and backups](https://render.com/docs/postgresql-backups).

Provider encryption protects the managed service. A downloaded export or a separately stored dump needs its own protection. Treat every backup as containing the database's full sensitive contents, including pending one-time confirmation tokens when the email outbox is enabled. Do not copy plaintext exports to a workstation, shared drive, repository, or ticket.

The Blueprint currently supplies one Render-managed default database credential to the backend as `DATABASE_USER` and `DATABASE_PASSWORD`. The backend uses that datasource for both application queries and Flyway. This is not a separated runtime/migration identity. Keep the credential private and plan the role split below before using this Blueprint with real data.

## Encrypt and rotate independent copies

Keep Render's managed PITR as the fast recovery path. For copies kept outside Render's seven-day export window, use an organization-approved private object store with a customer-controlled KMS key. Encrypt before upload or use the storage service's customer-managed-key encryption with TLS in transit. Deny public access, enable access logging, limit download/decrypt permissions, and set lifecycle deletion to the approved retention period. Keep a copy outside the database service's failure and deletion boundary.

Use a separate key per environment. Restrict key administration and decrypt access to the small on-call/backup group, require MFA for key administration, and audit key use. Keep the key identifier, creation time, PostgreSQL major version, Flyway schema version and a checksum with each encrypted artifact; do not put patient data or credentials in object names or metadata. Keep temporary working storage encrypted and remove plaintext staging files after encryption or restore.

Enable the key service's annual automatic key rotation where available, and rotate immediately after suspected compromise or an access-control change that requires it. New backups should use the new key version. Re-encrypt retained copies when policy requires a single active key; otherwise retain every old key version needed to decrypt backups still inside retention. Do not disable or delete an old key until all copies encrypted with it have expired or been re-encrypted and a restore test has proved the new key works. Back up key-recovery instructions separately from the encrypted database files.

Render-managed encryption keys and customer-managed keys for independent copies are separate controls. Rotating a PostgreSQL password does not rotate a backup encryption key, and rotating a KMS key does not change a database credential.

## Database roles

Use separate identities and store their credentials only in the hosting secret store:

| Identity | Needed access | Keep away from |
| --- | --- | --- |
| Runtime | Connect; schema usage; required `SELECT`, `INSERT`, `UPDATE`, `DELETE` on application tables; required sequence usage | DDL, ownership, role creation, database creation, superuser |
| Migrator | Create and alter application schema objects during a controlled release | Normal API runtime and scheduled backup job |
| Backup | Connect and read the tables and sequences required by `pg_dump` | Writes, DDL, server file access and application runtime |
| Recovery | Temporary ownership/restore rights on an isolated recovery database | The live database after the drill ends |
| Break-glass administrator | Time-limited administration for approved incidents | Routine app and backup processes |

Grant runtime DML and backup read access on existing objects and set matching default privileges for objects created by the migration identity. PostgreSQL grants default privileges per object-creating role, so verify the grants after migrations. A backup role should have no writes; avoid broad server-file or program-execution roles. See PostgreSQL's [`GRANT`](https://www.postgresql.org/docs/18/sql-grant.html), [default privileges](https://www.postgresql.org/docs/18/sql-alterdefaultprivileges.html), and [backup tool requirements](https://www.postgresql.org/docs/18/app-pgdump.html).

The current Spring configuration does not set separate Flyway credentials. Implement the role split by giving Flyway a controlled migration identity (or running migrations in a release step) and giving the deployed API only the runtime identity. Do not replace the current datasource user with a read-only backup role or runtime role until migrations have been separated and tested.

Render-managed credential rotation creates a new database user as the default user. Blueprint references to the database connection values update on the next Blueprint sync. Check the new user's effective grants first, sync the Blueprint, redeploy every service that connects, verify new connections, then revoke the old login. Keep the old login available until the rollout is healthy. Users created directly with SQL are not shown in Render's credential management. See [Render Postgres credential rotation](https://render.com/docs/postgresql-credentials).

## Verify a recovery

Run a restore drill at least quarterly and after a major database or migration change. Use a private, isolated target with the same PostgreSQL major version as the backup. Never restore over the live database. For a Render PITR, use the newly created recovery instance. For a logical export, restore into an empty database; Render warns that its restore commands can drop objects in the target schema. See the [Render restore steps](https://render.com/docs/postgresql-backups#perform-a-recovery).

1. Record the source backup/export identifier, its timestamp, checksum and encryption key version. Retrieve and decrypt it using the restricted recovery identity. Confirm the checksum before restore.
2. Restore to the isolated target. Keep it private and do not copy patient records into a less-protected development environment.
3. Confirm the database accepts connections, the expected PostgreSQL version is running, and the newest rows in `flyway_schema_history` report successful migrations.
4. Compare aggregate counts for core tables with the recorded source or expected restore point. Check representative foreign-key relationships and run the application's health endpoint and read-only workflows using the runtime credential.
5. Confirm the application can connect with its runtime identity, while that identity cannot create or alter schema objects. Confirm the recovery identity can be removed after the drill.
6. Record elapsed restore time, recovered timestamp, checksum result, migration version, aggregate checks, access checks and any failures in the operations record. Do not include credentials, tokens, or patient details.
7. Keep the original service pointed at its current database until checks pass and the service owner approves a cutover. Then update the private connection setting, redeploy, check health and database connections, and keep the source available until the new primary is stable.

A successful backup job is not a restore test. The drill must prove that the artifact can be decrypted, restored, and read by the application under the intended least-privilege role.
