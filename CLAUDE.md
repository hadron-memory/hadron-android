# Agent dev guide — hadron-android

This is the native Android application for the Hadron platform (Kotlin, Jetpack
Compose, min SDK 26). It is the counterpart of `../hadron-ios`.

## Use of Hadron

Hadron is this project's institutional memory — assume it covers things that
aren't obvious from code alone (past incidents, decisions whose rationale isn't
in the code, conventions baked into several places). Relevant memories:

- `hrn:mem:hadronmemory.com:hadron-android` — memory for the Hadron Android app
- `hrn:mem:hadronmemory.com:hadron-ios` — the iOS app this one ports
- `hrn:mem:hadronmemory.com:hadron-macapp` — the macOS app (shares HadronKit with iOS)
- `hrn:mem:hadronmemory.com:dev` — findings, conventions, ops, tasks (the routing index)
- `hrn:mem:hadronmemory.com:specs` — product specs (loc-as-citation; `hadron spec …`)
- `hrn:mem:hadronmemory.com:hadron-server` — Hadron's server/backend

(1) **Query Hadron before reading project code.** For the topics, entities, and
decision areas in a request, run `hadron_find_nodes` first, then
`hadron_get_node` on promising hits. Cite node `loc` values when you reference
what you found.

(2) Call `hadron_get_node` with `urn: "hrn:node:hadronmemory.com:dev:instructions"`
to load the project introduction (what Hadron is, URN grammar, the platform-specs
corpus, core architecture). Read it once per session.

(3) At the start of **every change** — before drafting a plan — call
`hadron_get_node` with `urn: "hrn:node:hadronmemory.com:dev:preflight"`. It's a
symptom-to-pattern routing index into the findings, conventions, and ops branches.

(4) When a non-obvious finding emerges — a convention you discovered, a gotcha
you hit, a node that turned out to be wrong — capture, fix, or delete it
immediately via `hadron_create_node` / `hadron_update_node` (don't batch to
end-of-session; context decays).

## Build & run

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle needs a JDK 17+; Android Studio's bundled JBR works:
`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Play Store upload

```bash
./gradlew :app:bundleRelease
```

The bundle is deliberately **unsigned** — signing happens with the owner's
upload key or Play App Signing, never from this repo. Bump `versionCode` (and
`versionName` where it matters) in `app/build.gradle.kts` before each upload.

## Things to know

- **`:hadronkit`** is the port of hadron-ios' `HadronKit` Swift package. Keep it
  UI-free: no Compose, no Activity beyond the `Activity` the Custom Tab needs to
  launch from. When the iOS/macOS HadronKit changes, this is the module that has
  to follow.
- **AGP 9 compiles Kotlin itself** ("built-in Kotlin") — do NOT add the
  `org.jetbrains.kotlin.android` plugin; AGP 9 fails the build if you do. Only
  the Compose and serialization compiler plugins are applied, and declaring
  their version is what pins KGP. `kotlin.compilerOptions.jvmTarget` derives
  from `android.compileOptions.targetCompatibility`.
- **OAuth contract** (spec `cor:aut:030:01`): `resource=<base>/mcp` is REQUIRED
  on both `/oauth/authorize` and `/oauth/token`; the DCR client id is cached in
  the secure store and must survive sign-out (10/min rate limit); the issued
  `hdr_user_*` key never expires — a GraphQL 401 means revoked → sign out.
- **The redirect URI must use the double-slash form**
  (`com.hadronmemory.android://oauth-callback`). The server's DCR validator
  requires scheme + non-empty authority; the Apple-style single-slash form is
  rejected.
- **The sign-in flow is split in two**, unlike iOS' single suspending call:
  `beginAuthorization` persists the PKCE verifier and state, `completeAuthorization`
  consumes the redirect. Android can kill the process while the browser is in
  front, and the email magic link legitimately completes minutes later from a
  different app. Never turn this back into an in-memory continuation.
- **Browser-dismissal is inferred, not reported.** Custom Tabs have no result
  callback, so `MainActivity`'s pause/resume pair drives the cancel path. The
  email provider is exempt (leaving the app is the expected behaviour there) and
  gets an explicit Cancel button instead.
- **findNodes keyword mode parses a boolean grammar** — free-form input goes
  through `HadronClient.literalSafeQuery` (quoted-phrase fallback), never raw.
- **Optional GraphQL variables must be OMITTED, not null.** The `buildJsonObject`
  builders make that structural: don't `put` the key. Don't reintroduce a nullable
  variables map.
- **The brand mark (`hadron_mark.png`) is a two-tone opaque disc, not an alpha
  mask.** Tinting it flattens it into a filled circle. Render it as-is; it has
  enough internal contrast for both light and dark. Same reason the adaptive icon
  ships no `<monochrome>` layer.
- **The Material colour scheme overrides the full surface-container ramp** on
  purpose. Left at the baseline, menus and dialogs come out lavender-tinted.
- **`mipmap-anydpi-v26` must keep the `-v26`** even though minSdk is 26 and lint
  says to merge it into `mipmap-anydpi`. Take the suggestion and AAPT2 stops
  resolving `@mipmap/ic_launcher` at all — resource linking fails. The
  `ObsoleteSdkInt` warning here is a known false positive; leave it.
