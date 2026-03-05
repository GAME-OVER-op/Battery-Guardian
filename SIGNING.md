# Signing release APK (v1/v2/v3)

This project is configured to sign **release** builds with **v1, v2 and v3** signature schemes.

## Quick start (Termux)

1. Generate keystore (one-time):
   ```sh
   ./generate_release_keystore.sh
   ```

2. Add passwords (recommended) to `~/.gradle/gradle.properties`:
   ```properties
   BG_STORE_PASSWORD=YOUR_STORE_PASSWORD
   BG_KEY_PASSWORD=YOUR_KEY_PASSWORD
   ```

3. Build a signed release APK:
   ```sh
   ./gradlew :app:assembleRelease
   ```

Output:
`app/build/outputs/apk/release/app-release.apk`

## Notes
- If `release.jks` or passwords are missing, the build will fall back to debug signing (still installable),
  but for public releases you should always use your own `release.jks`.
- Keep `release.jks` and passwords safe. If you lose them, you won't be able to publish updates signed with the same key.
