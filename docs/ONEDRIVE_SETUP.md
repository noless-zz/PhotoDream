# OneDrive setup (one time, ~5 minutes)

PhotoDream signs in to Microsoft with its own "client ID". You get one by
registering the app (free) in the Microsoft Entra admin center.

## 1. Register the app

1. Go to <https://entra.microsoft.com> and sign in.
   - Use an account that is allowed to register apps — for example your school
     Microsoft 365 account. A plain personal account (outlook.com / hotmail) can
     only register apps if it has its own Entra tenant (e.g. a free Azure account).
2. **Applications → App registrations → New registration**
   - **Name:** `PhotoDream`
   - **Supported account types:** *Accounts in any organizational directory and
     personal Microsoft accounts* (so your personal OneDrive works too)
   - **Redirect URI:** platform **Public client/native (mobile & desktop)**,
     value exactly: `photodream://auth`
   - Click **Register**.
3. On the app's **Overview** page copy the **Application (client) ID**
   (looks like `1a2b3c4d-....`).
4. (Optional, makes the consent screen tidy) **API permissions → Add a permission →
   Microsoft Graph → Delegated**: `Files.Read`, `User.Read`, `offline_access`.
   No admin consent is needed for personal accounts.

No client secret is needed — the app uses PKCE.

## 2. Put the ID in the app

Open `app/src/main/res/values/onedrive_config.xml` and replace
`PASTE-YOUR-CLIENT-ID-HERE` with your client ID. Rebuild and install.

## 3. Connect

PhotoDream → **Connect OneDrive** → sign in → allow access → you return to the app.
Then **Choose OneDrive folder**, open the folder you want, tap **Use this folder**.
The first sync starts right away; after that it runs every ~6 hours
(by default only on Wi-Fi while charging).

## Troubleshooting

| Message | Fix |
|---|---|
| "AADSTS50011 … redirect URI … does not match" | The redirect URI in Entra must be exactly `photodream://auth` under *Mobile and desktop*. |
| "AADSTS700016 … application not found" | Client ID typo, or the app was registered for "this organization only". |
| "You can't sign in here with a personal account" | Supported account types must include personal Microsoft accounts. |
| School account: "Need admin approval" | Your school blocks user consent – use your personal account, or ask IT. |
| Sync says "sign-in expired" | Tap Disconnect, then Connect again. |
