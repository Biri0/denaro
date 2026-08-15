# Denaro CI and releases

Pull requests targeting `main` run the single required check **PR checks**, which
executes `testDebugUnitTest` and `lintDebug`. Ordinary pushes to `main` do not run
CI or publish releases.

Pushing a semantic-version tag such as `v2.3.2` or `v2.4.0-beta.1` starts the
release workflow. The tagged commit must be contained in `main`. Release tags
must not include SemVer build metadata after a `+`; that part of the tag is
reserved for the workflow's F-Droid companion tag.

The workflow reruns the checks, builds a signed APK and AAB, uploads both to a
draft GitHub Release, publishes only the AAB to the completed Play
internal-testing track, and then finalizes the GitHub Release. A publishing
failure leaves the GitHub Release as a draft. Tags with a prerelease component
after the patch number become GitHub prereleases.

The release tag without its leading `v` is the app's `versionName`. On the first
run for a release, CI computes `versionCode` as:

```text
100000 + UTC seconds since 2026-01-01T00:00:00Z
```

CI freezes that value in a second tag on the same commit. For example, pushing
`v2.3.2` creates a companion tag such as
`fdroid-v2.3.2+19626400`. F-Droid can read the version name and code from that
tag without reproducing CI's time calculation. A rerun reuses the existing
companion tag and fails if the tag points to another commit or if multiple
companion tags exist for the release. Do not create, move, or delete companion
tags manually.

For local builds the checked-in defaults remain unchanged. Either value can be
tested locally with `-PreleaseVersionCode=...` and
`-PreleaseVersionName=...`.

## Store listing assets

F-Droid and Gradle Play Publisher read the localized listing from
`app/src/main/play`. Before tagging a release, update the English and Italian
`default.txt` release notes there. When the interface has changed, connect
exactly one physical Android device and regenerate the screenshots with:

```bash
tools/generate-marketing-assets.sh --date YYYY-MM-DD
```

The generator captures English and Italian in light and dark themes, updates
the README composites, and writes the ordered phone screenshots and icon to the
Triple-T listing. It refuses to capture from an emulator. The release workflow
publishes the AAB only; it does not run Gradle Play Publisher's separate
`publishListing` task.

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
