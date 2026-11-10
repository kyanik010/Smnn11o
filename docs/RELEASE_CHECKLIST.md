# Release checklist

## Source and version

- Work from a dedicated release branch with a clean `git status --short`.
- Review all commits and generated Room schema changes.
- Set a monotonically increasing `VERSION_CODE` and intended `VERSION_NAME` in
  `version.properties`.
- Confirm package/application ID is `com.idealplayer.app`.
- Confirm the release notes state that this application installs separately from the former package
  and that Android does not migrate the former app-private data automatically.
- Scan tracked files and history for credentials, keystores, private keys, provider URLs, and user
  playlists.

## Automated verification

```bash
./gradlew clean
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

- Investigate every new lint warning; document intentional cleartext and target-SDK warnings.
- Confirm Room schema export exists and every schema version has a registered migration path.
- Inspect release mapping/native-symbol outputs before archiving them.

## Signing and artifacts

- Keep signing values outside Git in `local.properties`, environment variables, or CI secrets.
- Back up the keystore and passwords in separate secure locations.
- Verify the release certificate fingerprint before upload.
- Archive the AAB/APK, mapping file, native debug symbols, commit SHA, version, and checksums.
- Upload the `release` AAB to Play internal testing first.

## Store readiness

- Recheck target API, restricted permissions, foreground services, Data safety, privacy policy,
  content rating, target audience, store listing, TV banner/screenshots, and release notes.
- State clearly that the app provides no content and supports user-provided sources only.
- Do not include provider names, copyrighted channel artwork, real credentials, or unlicensed streams
  in screenshots or reviewer instructions.
- Complete [MANUAL_QA.md](MANUAL_QA.md) on phone and TV before promoting beyond internal testing.
