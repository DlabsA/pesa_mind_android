# Google Play — SMS permission rejection (v39) and the v40 response

Status: v40 built, pending upload. Rejection received against `cc.dlabs.pesamind` v39 on the
"Permissions and APIs that Access Sensitive Information" policy, two findings:

1. *Requested permissions do not match core functionality* — "your store listing information does
   not match the core functionality for your declared use case."
2. *Unable to verify core functionality* — "your in-app experience does not match the core
   functionality for your declared use case", with a request for an updated Permissions
   Declaration Form and a demo video.

Declared use case (unchanged, and still correct): **SMS-based financial transactions, and related
activity including OTP account verification for financial transactions and fraud detection.**

---

## 1. Root cause — the app never asked for the permission

This was not a wording problem. From v37 onward the app **declared `RECEIVE_SMS` in the manifest
and never requested it at runtime.**

Commit `75d0f4d` removed `MainActivity`'s `permissionLauncher`, `requestRequiredPermissions()`,
the rationale dialog and the settings-fallback dialog, and replaced them with a comment:

> `// No permission is requested here — see SmsTracingPermissions for why the SMS/phone prompts`
> `// live in the screens that explain them instead.`

`SmsTracingPermissions` was never written. Nothing else in `app/src/main` called
`requestPermissions`, `checkSelfPermission` or an `ActivityResultContracts.RequestPermission*`
launcher for SMS. Consequences on a clean install — i.e. exactly what a Play reviewer sees:

- `RECEIVE_SMS` is never granted, so `SmsReceiver` never receives `SMS_RECEIVED`.
- `SMSMessageProcessor` never runs, so no transaction is ever auto-created.
- `READ_PHONE_STATE` / `READ_PHONE_NUMBERS` are likewise never granted, so `SimSlotManager`
  cannot name a carrier and alerts cannot be attributed to a SIM.
- `POST_NOTIFICATIONS` is never granted, so no transaction notification ever appears.

Both findings follow directly: the reviewer cannot verify the core functionality because on their
device it genuinely does not run, and an app that never uses `RECEIVE_SMS` does not match a
declaration that says the permission is core.

## 2. What changed in v40

New package `core/permissions/`:

| File | Role |
|---|---|
| `SmsTracingPermissions.kt` | Single source of truth for which permissions tracing needs (`RECEIVE_SMS` core; phone-state/numbers/notifications supporting), grant checks, permanent-denial check, settings deep link. |
| `SmsTracingPermissionState.kt` | `rememberSmsTracingPermissionState()` — screen-local handle driving disclosure → system prompt, re-reading the grant on every `ON_RESUME` so a grant made in system Settings is picked up. |
| `SmsTracingDisclosure.kt` | The **prominent disclosure** dialog, plus `SmsTracingPermissionHost` that renders it. The system prompt is unreachable except through "Allow" on this dialog. |
| `SmsTracingBanner.kt` | Standing "Automatic tracing is off" banner with a re-ask / open-settings action. |

Wiring:

- New onboarding step **1 of 7**, `OnboardingSmsAccessScreen` (`features/onboarding/`), between the
  intro and the SIM-slot step. Every new user reaches it immediately after signup. Skippable.
- The step is placed *before* the SIM-slot step so `READ_PHONE_STATE` is granted in time for
  carrier detection there.
- `DashboardScreen` shows `SmsTracingBanner` above the offline banner whenever `RECEIVE_SMS` is
  missing, so a user who skipped or denied has a permanent, discoverable way back.
- `READ_SMS` remains **not** declared (removed before v39): the pipeline reads only the body of an
  incoming broadcast, never the inbox.

Version: `versionCode`/`versionName` **40**. `ktlintCheck`, `assembleDebug` and `test` all pass.

### The disclosure text as shipped

> **Allow Pesa Mind to read your transaction SMS**
>
> Pesa Mind records your money automatically. To do that it needs SMS access, so it can read the
> mobile money and bank alerts your provider sends you — MTN MoMo, Airtel Money, Stanbic, Centenary
> and the other channels you set up.
>
> - Each alert is turned into a transaction — amount, date, balance and reference — and added to
>   your ledger without you typing anything.
> - Only messages from the financial senders you've added are used. Every other SMS is ignored, and
>   your existing inbox is never read.
> - Messages are read on this device. Message text is never sold, and never shared with anyone for
>   advertising.
>
> Without SMS access you can still use Pesa Mind, but every transaction has to be entered by hand.
>
> [ Not now ]  [ Allow SMS access ]

This text must stay true to `SMSMessageProcessor`. If the parser starts reading anything else, the
disclosure changes with it — an overstated or understated disclosure is itself the violation.

## 3. Permissions Declaration Form — answers to submit

**Which permission(s) are you requesting?** `RECEIVE_SMS`.
(`READ_SMS`, `SEND_SMS`, `WRITE_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, and all Call Log
permissions: **not requested**.)

**Core functionality served:** *SMS-based financial transactions, and related activity including
OTP account verification for financial transactions and fraud detection.*

**Describe how your app uses the permission:**

> Pesa Mind is a personal finance tracker for mobile money and bank users in Uganda. Its core
> feature is automatic transaction capture: when a user's provider sends a transaction alert by
> SMS (MTN Mobile Money, Airtel Money, Stanbic Bank, Centenary Bank and similar), the app reads
> that incoming message, parses the amount, date, running balance, counterparty and transaction ID
> out of it on the device, and creates a transaction in the user's ledger without any manual entry.
> `RECEIVE_SMS` is the only permission that makes this possible — the app is notified of the
> message body as it arrives via the `SMS_RECEIVED` broadcast. The app does not request `READ_SMS`
> and never reads the user's existing inbox; it only handles messages that arrive while it is
> installed, and only those whose sender matches a financial channel the user has explicitly added.
> Messages from any other sender are discarded without being stored. Parsing happens entirely on
> the device; raw SMS text is never uploaded, sold, or shared with third parties.

**Is there an alternative to using this permission?** No. Ugandan mobile money and bank providers
in this market expose no transaction API to end users; the SMS alert is the only channel through
which a user's own transaction data is delivered. The app offers manual entry and bank-statement
import as fallbacks, but they cannot provide real-time automatic capture, which is the feature the
app is built around.

**Prominent disclosure:** yes — shown in-app before the runtime prompt (text in §2 above; shown at
onboarding step 1, and again whenever the user re-enables from the dashboard banner).

**Privacy policy:** https://pesamind.dlabs.cc/privacy/

## 4. Instructions for the reviewer (paste into the declaration / review notes)

```
Steps to verify automatic SMS transaction capture (v40, versionCode 40):

1. Install and open the app. Create an account (email + password) or sign in with Google.
2. Onboarding starts automatically. Step 1 of 7 is "Turn on automatic tracing".
   Tap "Turn on automatic tracing" — a disclosure dialog explains exactly what is read and
   why. Tap "Allow SMS access", then Allow on the Android permission prompt.
3. Continue through onboarding and add at least one Mobile Money channel
   (step 4, "MTN Mobile Money") and/or a Bank channel (step 6). Tap Finish on the review step.
4. Send the device an SMS from the number/sender below, or from any handset:

   Sender: MTNMobMoney        (must be this sender ID — see MessageSender.MTN_MOB_MONEY)
   Body:   You have received UGX 25,000 from JOHN DOE 256772123456. Your new
           balance is UGX 118,400. Transaction ID 1234567890.

5. Within a second or two a notification appears and the transaction is on the Dashboard
   and in the Transactions list — amount UGX 25,000, income, with the transaction ID and
   the new balance — with no manual entry.
6. To see the permission being asked for in context a second time: revoke SMS in
   Android Settings > Apps > Pesamind > Permissions and reopen the app. The dashboard shows
   an "Automatic tracing is off" banner with a "Turn on" action that replays the disclosure.

If SMS cannot be delivered to the review device, the demo video linked in this declaration shows
steps 1-5 end to end on a physical device.
```

## 5. Store listing — required changes

Finding 1 is about the **listing**, and is fixed in Play Console, not in code. The listing must
promote the SMS feature, because policy only permits a permission that supports a feature
"promoted in your Google Play listing." Suggested copy:

**Short description (max 80 chars):**

> Auto-track MTN, Airtel & bank transactions from your SMS alerts.

**Full description — first paragraph (must appear above the fold):**

> Pesa Mind reads the mobile money and bank transaction alerts your provider sends you by SMS and
> turns them into a complete record of your money — automatically. Every MTN Mobile Money, Airtel
> Money or bank SMS becomes a transaction with its amount, date, balance and reference, with no
> typing. Pesa Mind asks for SMS permission for this reason only: it reads incoming alerts from the
> financial senders you set up, never your inbox, and your message text never leaves your device.

**Screenshots:** at least one must show the automatic-capture feature — the disclosure/onboarding
step, or the transaction list with SMS-captured entries. A listing whose screenshots show only
budgets and charts is what "listing does not match the declared use case" means.

**Data safety form:** SMS messages — collected: **No**; shared: **No**; processed ephemerally on
device. Keep this consistent with the disclosure text above.

## 6. Demo video

Play asks for a link "if portions of your app are restricted". Record ~90 seconds, unlisted on
YouTube, no cuts, on a physical device:

1. Fresh install, app opens on the login screen.
2. Sign up / sign in.
3. Onboarding step 1: tap "Turn on automatic tracing" → disclosure dialog fully visible and
   readable for ~4 seconds → "Allow SMS access" → Android prompt → Allow.
4. Add an MTN Mobile Money channel; finish onboarding.
5. From a second handset, send the sample SMS above. Show the notification arriving and the
   transaction appearing in the list, then open it to show the parsed amount and reference.
6. Settings → Privacy Policy, to show the policy is reachable in-app.

Put the URL in the declaration form's video field and in the appeal note.

## 7. Console state as of 2026-09-09

Done in Play Console (Dlabs Agency / cc.dlabs.pesamind):

- **Permissions Declaration Form — saved.** Core functionality reduced to **"SMS based money
  management"** only. The box "SMS-based financial transactions ... (for example, 5-digit
  messages)" was **unticked**: its permitted use covers OTP account verification and fraud
  detection, which Pesa Mind does not do, so declaring it was itself a mismatch. Instructions for
  review replaced with the 4-step verification recipe (the field caps at **500 characters**, so the
  long version in §3/§4 above does not fit — it is kept here as the reference text). Video
  instructions field set to the demo video link. All four attestations ticked.
- **Store listing — saved and staged** in Publishing overview (saving does not submit).
  Short description is now "Auto-track MTN, Airtel & bank transactions from your SMS alerts."
  (64/80). Full description gained an "Automatic SMS Transaction Tracking" section as the third
  block, above the fold, and "Log expenses in seconds" became "Digital transactions arrive on their
  own from your SMS alerts. Cash you log in seconds." (3889/4000).

Still open:

- [ ] **Upload the v40 AAB.** `app/build/outputs/bundle/release/app-release.aab` (21 MB, signed).
      A draft Production release is already created and waiting at the Production track's
      "Create production release" page — drag the file onto it. It could not be uploaded through
      browser automation (10 MB bridge limit).
- [ ] **`READ_SMS` is still declared** in the console's permission list. It comes from release
      **34 on the Closed testing – Alpha track**, which is still live and predates the manifest
      cleanup; v40 does not declare it. Retire or supersede release 34 so no active bundle declares
      a permission nothing uses.
- [ ] **Screenshots.** All 3 phone screenshots show the dashboard and analytics. At least one must
      show automatic SMS capture (the onboarding disclosure step, or the transaction list with
      SMS-captured entries) — a listing showing only budgets and charts is what "listing does not
      match the declared use case" means.
- [ ] **Data safety form** — confirm SMS messages: collected **No**, shared **No**, processed
      ephemerally on device. Must stay consistent with the in-app disclosure.
- [ ] **Privacy policy** at https://pesamind.dlabs.cc/privacy/ must describe SMS handling in the
      same terms as the in-app disclosure. Not verified.
- [ ] **Send for review** from Publishing overview, once the bundle is up.
