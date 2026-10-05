# Working on PhotoDream issues with Claude Code

Every issue in this repo is written so Claude Code can pick it up on its own:
goal, behaviour, acceptance criteria and the files to touch. `CLAUDE.md` in the repo
root gives Claude the project rules (Java only, Hebrew strings, no secrets, tests + lint).

There are three ways to run it. Start with **A**.

## A. On GitHub: comment `@claude` on an issue  (recommended)

One-time setup (5 minutes):
1. Install the **Claude GitHub App** on `noless-zz/PhotoDream`: <https://github.com/apps/claude>
2. On your PC, in a terminal with Claude Code installed, run `claude setup-token` and copy the token.
3. GitHub → PhotoDream → **Settings → Secrets and variables → Actions → New repository secret**:
   name `CLAUDE_CODE_OAUTH_TOKEN`, value = the token. (Runs then use your Claude subscription.)

Use it:
- Open an issue and comment: `@claude please implement this issue`.
- Claude replies in the issue, works in a GitHub runner (Android SDK + Java are there, so it
  can run `./gradlew` builds, tests and lint), and links a branch / pull request when done.
- The **CI** workflow builds every PR and attaches a **debug APK** to the run
  (Actions → the PR's CI run → Artifacts) so you can test on your phone before merging.
- Ask for changes by commenting `@claude …` on the PR, or review it like any PR.
- Merge, then publish a GitHub Release (`v0.3`, …) – the Release workflow builds the signed APK.

Tips: work through the roadmap in order (`docs/ROADMAP.md`), one issue per PR.
Issues marked **needs: decision** have an open question – answer it in a comment before tagging `@claude`.

## B. In the browser: claude.ai/code (cloud session)

Good for bigger issues where you want to chat with Claude while it works.
1. <https://claude.ai/code> → choose the repo `noless-zz/PhotoDream`.
2. Cloud environment → **Network access**: add the host `dl.google.com`
   (Android SDK and Google's Maven live there; it is not on the default list).
3. Start a session: *"Work on issue #12 and open a PR"*.
   The repo's `.claude/settings.json` runs `scripts/cloud-setup.sh` on session start, which installs
   the Android SDK the first time (a minute or two).

## C. On your PC: Claude Code in the project folder

`cd C:\Users\ADMIN\AndroidStudioProjects\PhotoDream` → `claude` →
*"Implement issue #12 (use gh issue view 12)"*. Builds use your Android Studio SDK and the release
key in `..\PhotoDream-signing`, so you can install straight onto your phone with
`gradlew installDebug`.
