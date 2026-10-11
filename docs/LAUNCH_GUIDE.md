# TactShoot — Launch Guide

This guide takes you from "code on my PC" to "people on the internet playing your game".

## Step 1 — Put the server online (free)

**Option A: Render (recommended, ~5 minutes)**

1. Create a free account at [render.com](https://render.com), push this project to GitHub.
2. In Render: **New → Blueprint** → pick the repo → Render reads `render.yaml` and creates the service automatically.
3. Or manually: **New → Web Service** → repo → root directory `server` → build `npm install` → start `npm start`.
4. When it's live you get a URL like `https://tactshoot-xxxx.onrender.com`.

**Option B: Railway / Fly.io / Glitch** — same idea, any Node host, root = `server/`.

**Option C: Your own PC** — `cd server && npm install && npm start`. Friends on the same
Wi-Fi connect to `ws://YOUR-LAN-IP:8080` by typing it in *Online → GAME SERVER*.

> Free tiers sleep after ~15 min of inactivity and wake on the next connection
> (first player may wait ~30 s). That's fine for a growing community. A $5 VPS
> removes the sleep and holds hundreds of players.

## Step 2 — Point the game at your server

Edit `core/src/main/java/com/tactshoot/game/NetConfig.java`:

```java
public static final String DEFAULT_SERVER = "wss://tactshoot-xxxx.onrender.com";
```

Note **`wss://`** (not `https://`) for a deployed server. Players can also override
the URL in-game, so a stale build still works — but ship the right default.

## Step 3 — Build a release APK

1. Android Studio → **Build → Generate Signed App Bundle / APK**.
2. Create a keystore (keep it safe forever — losing it means losing updates).
3. Choose **APK**, release variant, and the signature versions V1+V2.
4. Share the APK directly, or upload it to the Play Store (below).

## Step 4 — Grow the fanbase

**The room-code loop is your growth engine.** Every player is one tap away from
inviting friends: pause → SHARE ROOM CODE opens the phone's share sheet.

1. **Seed the community** — start a Discord/WhatsApp/Telegram group, post your
   APK + the server landing page link. The landing page shows live leaderboards,
   which gives people a reason to come back and grind the #1 spot.
2. **Organize events** — "FFA Friday": post a room code at 20:00, first to 25 kills
   gets bragging rights on the leaderboard. Screenshot the final scoreboard.
3. **Clip it** — record a 1v1 (desktop build: `gradlew :desktop:run`), post 30-second
   clips on TikTok/Reels/Shorts. FPS content travels.
4. **Play Store** — $25 one-time developer account. Publish with good screenshots,
   then link the APK in your communities. Note: Play needs the target SDK current
   (this project uses targetSdk 36) and a privacy-friendly description (no data
   collection — the server only stores a nickname and match stats).
5. **Monetize later, never pay-to-win** — when the community asks: cosmetic skins
   (sold as supporter packs), or ads on the menu only. Never sell power; that
   kills FPS communities.

## Scaling checklist

| Players online | What to do |
|---|---|
| 0–100 | Free Render/Railway instance, one region |
| 100–500 | $5–10 VPS (Hetzner/DigitalOcean), `npm start` with PM2 |
| 500–2000 | One VPS per region + a tiny load balancer; rooms are independent, so sharding is trivial |
| 2000+ | Congratulations — you have a real game. Hire a server dev, add accounts & anti-cheat |

The server is stateless per-room (each match is a self-contained room), which means
horizontal scaling is just running more copies of the same file.

## Anti-cheat reality check

v1 validates rate of fire, ammo, reload, range and teleports — good enough for a
friendly community. Before you hit 500+ players, add server-side map geometry
(the client already has `Box` lists you can mirror in `server/server.js`) to stop
wall-bangs. Don't announce weaknesses publicly; fix them quietly in updates.
