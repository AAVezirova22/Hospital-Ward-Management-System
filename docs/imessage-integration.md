# iMessage access to the operations assistant

Status: the owner's Mac has an existing **OpenClaw iMessage gateway** and an approved sender. Its authenticated browser can use the local Docker application, including [doctor appointment booking](appointments.md). This configuration lives outside the repository. Medcore has no server-side messaging bridge or sender-to-account linking flow; the local demo browser acts under its signed-in staff account.

## Existing integration options

| Option | What it provides | What is needed |
| --- | --- | --- |
| [OpenClaw official iMessage plugin](https://docs.openclaw.ai/imessage) | Existing iMessage transport through `imsg`, locally or over SSH | A Mac signed into Messages, an OpenClaw gateway, and a dedicated integration identity |
| [Sendblue](https://docs.sendblue.com/) | Hosted send-message API and inbound message webhooks | A Sendblue account, provisioned number, credentials, and a public HTTPS callback |

OpenClaw's current documentation directs new installations to `@openclaw/imessage`, not the removed BlueBubbles channel. Do not use old BlueBubbles setup instructions without checking the current [migration documentation](https://docs.openclaw.ai/channels/imessage-from-bluebubbles).

Sendblue documents [inbound webhooks](https://docs.sendblue.com/getting-started/webhooks/) and [outbound messages](https://docs.sendblue.com/api/resources/messages/methods/send). Verify plan limits and iMessage/SMS fallback before selecting a plan. No account, subscription, or messaging number was created during this implementation.

## Medcore connection contract

For a deployment with separately authorized messaging users, the gateway is reusable but neither product is a prebuilt Medcore adapter. An authenticated application adapter would need to:

1. Link a sender to a signed-in Medcore staff account through a short-lived, single-use pairing challenge. Do not grant access merely because a phone number matches an editable patient record.
2. Authenticate inbound callbacks, deduplicate message IDs, reject group conversations, rate-limit requests, and allow disconnecting a linked sender.
3. Call the existing `AiAssistantService` under the linked account's current authorization. Do not share the demo administrator's session, use direct database writes, or let a messaging agent bypass the tool registry.
4. Scope conversation sessions and pending proposals to that account and sender. Recheck the account on every message.
5. Return a concise before/after proposal with an expiry and a confirmation link into the authenticated Medcore interface. The initial integration should retain confirmation in the web app; plain-text “yes” must not accidentally confirm the wrong proposal.
6. Keep audit attribution, capacity locking, version checks, ownership, expiry, cancellation and replay prevention unchanged.
7. Minimize personal information in message bodies. Begin with the synthetic demo, since the external messaging provider receives message contents.

Patient accounts currently cannot use the staff operations assistant. Linking a patient sender must not grant staff access. Supporting a separate patient-safe assistant would be additional product work.

## Acceptance checks for a future integration

- Unknown, disabled, unpaired and revoked senders cannot read records.
- Replayed webhook deliveries produce at most one response and never duplicate a mutation.
- One sender cannot confirm another account's proposal.
- An expired proposal or changed admission requires a new proposal.
- An inactive/full destination is rejected by the same business service used in the interface.
- Provider failures preserve saved hospital state and do not leak secrets into logs.
- Disconnecting the gateway revokes access without changing a user's normal login.

The existing owner-controlled demo uses browser automation. A separate multi-user deployment needs a reviewed account-linking flow before enabling messaging access to hospital records.
