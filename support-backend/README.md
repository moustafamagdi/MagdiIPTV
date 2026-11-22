# MM Apps support service

This backend is shared by MM IPTV and MM Tube. Production project: `hncaagdpxhbysdcdwuoa` (Frankfurt), free plan, owned by the existing developer organization. The deployed Edge Function is `mm-diagnostics`. `schema.sql` is the source of the deployed `private_support_ingestion` migration. It must not be reapplied over existing tables.

## Owner workflow

Open [Support inbox](https://supabase.com/dashboard/project/hncaagdpxhbysdcdwuoa/explorer/notebook/fea19f9e-0f9a-4975-b690-ea13452e8e3c). Only project members can view it. Replace the all-zero UUID in the lookup cell with the user's copied Support ID. Tube and IPTV use separate installation IDs. SQL times are UTC; Riyadh is UTC+3. Recent reports, grouped errors, and budget/storage queries are already saved.

The ID is pseudonymous, not a phone number, hardware ID or cross-app tracking identity. Reinstallation/deletion generates a new ID. The per-installation bearer credential is randomly generated on device; only SHA-256 hashes are stored server-side. Knowing a support ID does not authorize writing/deleting its reports. No database secret or project admin key is shipped in either APK.

## Privacy and collection

Automatic delivery is OFF until an explicit in-app consent dialog is accepted. Manual Send also asks consent and does not enable automatic sending. Local allowlisted history is retained for 24 hours / 256 events. Each upload contains at most 128 recent events. Auto delivery only includes events after consent/last successful delivery, roughly every two hours while Android allows execution. JobScheduler provides network-constrained background attempts; process restarts reschedule the job. Force-stop, reboot before app launch, offline use and OEM battery policies can delay reports. Failed attempts do not claim successful delivery.

Only closed diagnostic categories, enumerated states, bounded numeric performance values, device model/manufacturer, Android API, software versions and enumerated installation source types are sent. No raw log files, watched titles/IDs, searches, thumbnails, provider addresses, stream URLs, cookies, passwords, subscription data, Google account data, location, serials or advertising identifiers. Unknown diagnostic lines are dropped, not regex-redacted and uploaded. ApplicationExitInfo only supplies a crash/ANR reason code on next start; no stack trace or system trace is uploaded.

Supabase necessarily receives network IP addresses to serve HTTPS and may retain operational/security logs under its own policy. The application's diagnostic tables do not store IPs. Do not describe this as perfectly anonymous telemetry. SupportActivity describes the recipient, fields, location, retention and controls in English/Arabic.

Stopping automatic sharing cancels future uploads; already received data remains until deletion/retention. Delete stops auto delivery, authenticates deletion of the current app/installation and rotates the ID only after server success. When offline, preserve the old credential so deletion can be retried. Externally saved manual files are not deleted. Advanced local log sessions retain the prior 40-part/7-day policy; they are never automatically uploaded.

## Quotas and security boundaries

Global budget: 2,000 reports and 100 new installations per UTC day; each installation at most 96 stored reports per rolling day. Up to 5,000 reports total and 10,000 active installation records. Up to 32 KiB request body, 128 events; old reports pruned under storage pressure. Hourly cron removes reports/installations older than 30 days (expiry cleanup can lag by one hour). These are conservative rollout limits, not promises of unlimited free capacity. HTTP 429 is shown as a service limit and manual sharing remains available. Free Supabase currently includes 500 MB database, 500k Edge Function invocations/month and 5 GB egress, and can pause inactive projects; monitor actual usage in Supabase. No paid plan was enabled.

Public registration necessarily permits forged installations; this is not app attestation. Server validation, transaction-serialized quotas and bounded storage limit persistence abuse. Invalid traffic still consumes function invocations. If adoption/abuse grows, add platform rate limiting/attestation or enrollment before raising limits. Do not embed a shared master secret in the APK to pretend it solves this.

Tables have RLS enabled, no anon/authenticated grants/policies. The service-role-only RPC is SECURITY INVOKER. The Edge Function authenticates per-installation tokens and uses its server-provided secret key to call the RPC. It does not expose a report-read endpoint. Supabase advisors' three `rls_enabled_no_policy` INFO notices are intentional deny-by-default, not missing public policies.

## Verification

Run the Java SupportPolicyTest/SupportFlowTest suites in both applications. They cover the real native screen buttons/consent, opt-in/out, manual sends, failure/deletion behavior, bounded history and closed data vocabulary. Backend `validation.test.mjs` checks malformed and private input rejection. Live endpoint testing also covers successful upload, idempotent retries, wrong/missing credentials, oversized bodies, ownership-enforced deletion, administrative lookup, and denied anon/authenticated database access.

This is app diagnostics, not Android Auto system logs. Playback counters do not prove audible sound or compatibility with a physical car. An absent report does not prove an absent fault.

## MM Auto extension

The allow_mm_auto_support migration (add-mm-auto.sql) extends the app discriminator to auto. Edge Function v2 also accepts closed download/install/release-check/repair/permission events, fixed MM app targets, enumerated install methods/source types and validated version strings. Auto never uploads raw installer output or local APK paths. The same owner inbox shows app=auto; existing IPTV/Tube payloads still pass live ingestion tests. Permission bitmask: 1=request-install permission, 2=Shizuku running, 4=Shizuku granted. A matched installer pair is not proof of Android Auto visibility.

