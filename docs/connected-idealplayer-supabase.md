# Connected IdealPlayer Supabase deployment

Connected IdealPlayer is disabled by default. To enable QR setup in an APK, deploy the included Supabase
Edge Function and build the app with HTTPS API/web URLs.

This repository intentionally does not commit Supabase project refs, API keys, service-role keys,
playlist URLs, or provider credentials.

## Backend deployment

Prerequisites:

- A Supabase project.
- Supabase CLI installed and authenticated.
- A deployed HTTPS host for `web/remote-setup` such as GitHub Pages, Cloudflare Pages, Netlify, or
  Vercel.

Commands:

```bash
supabase login
supabase link --project-ref <project-ref>
supabase db push
supabase secrets set CONNECTED_IDEALPLAYER_ALLOWED_ORIGINS=https://<your-web-host>
supabase functions deploy idealplayer-remote-setup --no-verify-jwt
```

The Android API base URL is:

```text
https://<project-ref>.supabase.co/functions/v1/idealplayer-remote-setup
```

The web companion URL is the HTTPS origin/path where `web/remote-setup` is hosted.

## APK build configuration

Put these values in the real, uncommitted `local.properties` before building:

```properties
REMOTE_SETUP_API_BASE_URL=https://<project-ref>.supabase.co/functions/v1/idealplayer-remote-setup
REMOTE_SETUP_WEB_BASE_URL=https://<your-web-host>
```

Both values must be HTTPS. If either is missing, the Android build keeps Connected IdealPlayer disabled.

## Security model

- The companion web page only talks to the Edge Function API. It does not receive Supabase anon or
  service-role keys.
- The `tv_pairings` table has Row Level Security enabled and grants no anon/authenticated direct
  table policies.
- Pairing rows expire after 10 minutes and completed rows are deleted after the TV retrieves the
  payload.
- The Edge Function validates the exact 8-character pairing code, payload version/source/type, URL
  shape, and 5 MB playlist-file limit.
- Do not log request bodies or payloads. They can contain playlist URLs and Xtream credentials.
- For a public deployment, put a WAF/rate-limit layer in front of the function or use Supabase
  platform limits/monitoring. The function deliberately has no login requirement because the TV
  pairing code is the handoff mechanism.

## Smoke test

After deployment, open a temporary pairing:

```bash
curl -X POST \
  -H 'content-type: application/json' \
  -d '{"pairingCode":"A7K9Q2BC","status":"pending","payload":{}}' \
  https://<project-ref>.supabase.co/functions/v1/idealplayer-remote-setup/api/pairings
```

Then check it:

```bash
curl https://<project-ref>.supabase.co/functions/v1/idealplayer-remote-setup/api/pairings/A7K9Q2BC
```

Expected response:

```json
{"status":"pending"}
```
