# Google Drive setup (one time, ~10 minutes)

PhotoDream reads photos from a Google Drive folder you choose, at random, in the
background. Google needs to know the app first. There is **nothing to paste into
the code**: Google recognises the app by its package name + signing-key fingerprint.

## 1. Google Cloud project
1. <https://console.cloud.google.com> – sign in with your personal Gmail.
2. Project picker (top) → **New project** → name `PhotoDream` → **Create** → select it.
   (An existing project also works, but users see *its* app name on the consent screen.)

## 2. Turn on the Drive API
Search **Google Drive API** → **Enable**.

## 3. Google Auth Platform
1. Search **Google Auth Platform** → **Get started**.
2. **Branding:** App name `PhotoDream`, support email, **developer contact email**.
   Leave **App logo empty** (a logo triggers a brand review).
3. **Audience:** **External**.
4. **Data Access → Add or remove scopes** → paste
   `https://www.googleapis.com/auth/drive.readonly` → add → **Save**.
   It shows as *restricted* – expected.
5. **Clients → Create client → Android**
   - Package name: `com.noam.photodream`
   - SHA-1: `84:21:FE:43:EE:E0:2C:70:BA:50:3B:46:08:3B:82:43:B7:F1:86:4B`
     (the PhotoDream release key – see `../PhotoDream-signing/README.txt`)

## 4. Testing vs. In production
| Status | Who can sign in | Catch |
|---|---|---|
| **Testing** | only emails listed under *Audience → Test users* (max 100) | everyone is signed out every **7 days** |
| **In production** (unverified) | anyone, up to **100 users in total** | users see "Google hasn't verified this app" |

For yourself while developing: **Testing** + add your Gmail as a test user.
Before giving it to students: **Audience → Publish app**.
If *Publish app* is greyed out, the **Branding** page is incomplete
(usually the developer contact email, or the App domain links – home page and
privacy policy URL, which can be a GitHub Pages page).
Do **not** submit for verification – not needed for ≤ 100 users.

## 5. What students see
Connect Google Drive → pick account → "Google hasn't verified this app" →
**Advanced → Go to PhotoDream (unsafe)** → allow *See and download all your Google Drive files* (read-only).

School Google accounts may be blocked by the school admin (students under 18 on
Google Workspace for Education). Use personal Gmail accounts.

## Troubleshooting
| Message in PhotoDream | Fix |
|---|---|
| "Google does not recognise this app" (error 10) | Android client missing, wrong package name, or wrong SHA-1. An APK signed with another key (e.g. a debug build) has a different SHA-1. |
| "Access blocked: app has not completed verification" / "403 access_denied" | App is in Testing and this account is not a test user. |
| Sync says "Sign-in expired" | In Testing mode after 7 days, or access was removed in the Google account – Disconnect and connect again. |
