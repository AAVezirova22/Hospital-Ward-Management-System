# Database recovery objectives

## Initial service targets

These are planning targets for the small managed PostgreSQL service in `render.yaml`, currently declared as `hospital-ward-demo-db` on Render plan `0.1c-256mb`. They are internal targets, not Render guarantees. The repository has no recorded restore drill, and its Blueprint does not provision an independent backup job. Treat the targets as unachieved until that job is running, monitored and restore-tested.

| Objective | Initial target | Basis |
| --- | --- | --- |
| Recovery point objective (RPO) | At most 24 hours of committed data | Keep a successful encrypted off-site logical export every day at 03:00 UTC. Use Render PITR for a closer point when it is available and the incident time is known. |
| Recovery time objective (RTO) | Restore service within 8 hours after incident declaration | Budget up to 30 minutes to assign the incident, 4 hours to create/restore the database, 3 hours to validate and switch services, and 30 minutes to check health after cutover. |
| Off-site copy retention | 30 days, subject to the organization's data-retention policy | This gives recovery copies beyond the provider's native window. Apply the approved retention policy if it requires a different period. |
| Restore exercise | At least quarterly and after a major schema change | Measure actual restore, verification and cutover time against the target. |

The RPO applies only while the daily export completes, passes integrity checks, reaches the independent encrypted store, and raises an alert when any step fails. A failed or missing export is an RPO breach; use the newest verified copy and record the exception. A backup that still contains corrupted data is not a valid recovery point, so use PITR before the corruption when possible.

The RTO clock starts when the service owner declares a recovery incident. Its budget is a manual-operator objective for this deployment, not a guaranteed database-provisioning time. Render documents the restore steps and recovery windows but publishes no numeric RTO guarantee. Name a recovery owner and ensure the response window is staffed; without that coverage, an eight-hour elapsed-time objective is not realistic. If a drill exceeds eight hours, record the measured result and change the target or remove the bottleneck before claiming compliance.

## Backup cadence and plan mapping

The Render Blueprint selects a paid database compute plan (`0.1c-256mb`). The PITR retention window is determined by the Render **workspace plan**, not that compute-plan identifier: Render documents three days for Hobby workspaces and seven days for Pro or higher. Paid Render Postgres uses continuous PITR; a restore creates a new instance for validation and cannot target a time within the last ten minutes. Render dashboard logical exports are on demand and retained for seven days. See [Render Postgres recovery and backups](https://render.com/docs/postgresql-backups).

Use these recovery layers:

| Layer | Cadence | Target retention | Recovery use |
| --- | --- | --- | --- |
| Render PITR | Continuous, provider-managed | 3-day or 7-day window for the actual workspace plan | Preferred for a recent bad write, deletion or corruption; restore to a point before the incident and validate the new instance before cutover. |
| Render dashboard export | Before risky migrations or credential changes; otherwise on demand | 7 days at Render | Short-term logical fallback. It is not a scheduled daily copy. |
| Independent encrypted logical export | Daily at 03:00 UTC | 30 days, subject to approved data policy | Recovery if PITR has expired or the Render database is deleted or unavailable. The last successful daily export bounds loss to at most 24 hours for a clean database failure. |

Provision the daily export as a monitored scheduled job (for example, an appropriately restricted Render Cron Job or an organization-managed scheduler). The current `render.yaml` does not declare that job or its independent storage destination. Alert by 04:00 UTC if the previous day's export is missing, failed integrity validation or did not reach storage. Encrypt the artifact with a customer-controlled key before or during upload; keep its key identifier and checksum with the operations record. Follow the database backup security and recovery runbook from issue #419 for key rotation and restore handling.

Run a logical export before every production migration that changes stored data, and retain that pre-change copy through the migration's verification window. Migrations remain versioned in Flyway, but a logical export gives an independent rollback source when an application change alters data irreversibly.

## Recovery measurement

During each drill, record the backup timestamp, recovered timestamp, actual data gap, database plan, workspace recovery window, PostgreSQL major version, restore duration, validation duration and cutover duration. Do not record patient details, tokens, passwords or connection strings. Compare the recovered schema with Flyway history, check aggregate counts and relationships, and run the application's health and read-only workflows with the runtime database role.

Use a private recovery instance and leave the current service pointed at its database until the restored instance passes validation. Render's PITR flow creates a separate instance specifically so operators can validate it before changing service connections. Remove temporary recovery resources after the service owner confirms cutover and stability.

## Dependencies to confirm for production

- Record whether the Render workspace is Hobby (3-day PITR) or Pro or higher (7-day PITR); the Blueprint alone does not set the workspace plan.
- Provision and monitor the daily encrypted export. Until that exists, the 24-hour RPO target is not active.
- Run at least two restore drills on the selected plan before treating the eight-hour RTO as demonstrated. Increase database capacity or simplify the manual cutover if measured restore time exceeds the budget.
- Review the 30-day off-site retention period against the organization's approved health-data retention and deletion requirements.
- Reassess the targets when database size, service plan, workspace plan, export cadence or recovery procedure changes.

Render documents its [automated recovery and export behavior](https://render.com/docs/postgresql-backups) and provides an example of scheduling [off-site Postgres backups](https://render.com/docs/backup-postgresql-to-s3). The example is a starting point; use storage, key ownership and access controls approved for the data in this deployment.
