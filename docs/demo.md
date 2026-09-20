# End-to-end demonstration

Use only the bundled synthetic data or newly created test records.

1. Sign in as `admin`. Inspect the overview, capacity, admissions and independent assistant briefing.
2. Create a patient in Patients. Open the patient dossier.
3. Choose **Admit patient**, select an active doctor and available room, review and confirm. Verify an active admission and occupied bed.
4. Record a catalogue procedure with a timestamp within the admission and an optional note. Verify the timeline and recorded price.
5. Change the catalogue price as administrator. The original recorded price and patient total must remain unchanged.
6. Transfer the patient. Verify both room history entries and released source capacity.
7. Press Ctrl+K / Cmd+K. Ask `summary`, `Rooms with two free beds`, and `Procedures today`. Open structured results.
8. Ask `Move him to room 307`. Inspect the proposal before confirming. The patient has not moved until confirmation. Cancel one proposal and verify no change; prepare another and confirm it.
9. Open Reports. Filter procedures by patient, period and doctor. Inspect exact totals; download CSV. Check hospitalized-patient census by room/doctor and capacity report.
10. Ask `discharge him`, then confirm the discharge proposal. Verify admission closure, released bed and preserved history. Reopening/refreshing the dossier retains the recorded state.
11. Sign out. Sign in as `doctor`. Only assigned patient admissions are available; direct navigation to `/app/users` displays access restricted, and direct user APIs reject access.
12. Sign in as `admin`. Review the audit events. Add, edit and deactivate a test staff account. A disabled account cannot continue using an existing session.

Automated integration tests cover stale proposals, expired proposals, confirmation ownership, replay, prompt-injection text, malformed/forbidden tools, concurrent final-bed admission requests, duplicate patient identifiers, account restrictions and provider failures. Playwright covers the principal UI workflows plus desktop/mobile rendering and reduced motion.
