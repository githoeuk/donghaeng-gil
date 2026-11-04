# Donghaeng Gil repository rules

- Active repository: `D:\projects\donghaeng-gil`. Use an explicit workdir for Git, app edits, and builds.
- Build through `& 'D:\projects\build-donghaeng.ps1'`. Keep the installed Android SDK/JDK on C:; do not develop in migration backups.
- Before app, build, permission, or demo changes, read `docs/submission-release-guidelines.md`. It defines the submission scope, actual versus simulated behavior, Release signing, and verification gates. It distinguishes planned configuration from implemented behavior.
- Use `docs/communication-plan.md` for replaceable transport boundaries and known SDK limitations. Do not invent official MyHyundai/Kia App integration APIs.
- Preserve existing user changes. Record completed changes, evidence, limitations, and next steps in `docs/work-log.md`.
- Follow the user's latest instructions if the scope changes. Do not commit or push unless the user requests it for the current work.
