# Denaro CI and releases

Pull requests targeting `main` run the single required check **PR checks**, which
executes `testDebugUnitTest` and `lintDebug`. Ordinary pushes to `main` do not run
CI or publish releases.

Pushing a semantic-version tag such as `v2.0.0-pre-alpha.2` starts the release
workflow. The tagged commit must be contained in `main`. The workflow reruns the
checks, builds a signed APK and AAB, uploads both to a draft GitHub Release,
publishes only the AAB to the completed Play internal-testing track, and then
finalizes the GitHub Release. A publishing failure leaves the GitHub Release as a
draft. Tags with a suffix after the patch number become GitHub prereleases.

The tag without its leading `v` is the app's `versionName`. CI computes
`versionCode` as:

```text
100000 + UTC seconds since 2026-01-01T00:00:00Z
```

For local builds the checked-in defaults remain unchanged. Either value can be
tested locally with `-PreleaseVersionCode=...` and
`-PreleaseVersionName=...`.

## GitHub configuration

Create a protected environment named `play-internal`. Put these secrets in it:

- `DENARO_UPLOAD_KEYSTORE_BASE64`
- `DENARO_UPLOAD_STORE_PASSWORD`
- `DENARO_UPLOAD_KEY_ALIAS`
- `DENARO_UPLOAD_KEY_PASSWORD`
- `GCP_PROJECT_ID`
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_PLAY_SERVICE_ACCOUNT`

Base64-encode the existing registered upload keystore as one uninterrupted
string for `DENARO_UPLOAD_KEYSTORE_BASE64`. The workflow decodes it only into the
ephemeral runner directory. `keystore.properties` continues to support local
release signing and remains ignored by Git.

Protect `main` by requiring pull requests, requiring the **PR checks** status
check, requiring branches to be current before merging, blocking direct pushes,
and disallowing bypass where the GitHub plan permits it. These settings matter
because CI intentionally does not rerun on ordinary pushes to `main`.

## Google and Play configuration

Enable the Android Publisher API and configure GitHub OIDC Workload Identity
Federation. Restrict the provider's attribute condition to this repository and
tag refs matching `refs/tags/v*`. For example, replace `OWNER/REPOSITORY` in:

```text
assertion.repository == 'OWNER/REPOSITORY' &&
assertion.ref.startsWith('refs/tags/v')
```

Permit that GitHub principal to impersonate only the service account named by
`GCP_PLAY_SERVICE_ACCOUNT`.

In Play Console, grant that service account access only to Denaro
(`it.rfmariano.denaro`) with app read access and permission to create releases on
testing tracks. Do not grant production-release or account-wide administration
permissions. Denaro must already exist in Play Console, Play App Signing must be
enabled, and the local keystore must be registered as its upload key.

Gradle Play Publisher uses Application Default Credentials created by the OIDC
authentication step; no Google service-account JSON key is stored in GitHub.

## First-tag verification

Before tagging, confirm that the pull request check is required and current.
After pushing the first tag, verify the APK and AAB names and versions on the
GitHub prerelease/release, and verify that Play shows a completed internal-track
release with the same version code.

The GitHub APK is signed with the upload key. If Play App Signing uses a
different app-signing key, that APK cannot update an installation obtained from
Play; it is still suitable for a fresh sideload installation.

This checkout currently has no GitHub remote configured, so the environment,
branch protection, and Google trust relationship must be activated after the
repository is connected to GitHub.
