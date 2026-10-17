/**
 * TactShoot multiplayer server.
 *
 * Runs the whole backend with zero external services: matchmaking queues,
 * private rooms with shareable codes, a 15 Hz state broadcast, and
 * server-authoritative damage validation. Also serves a landing page and
 * a public stats API.
 *
 * Deploy anywhere Node runs. Free options: Render, Railway, Fly.io, Glitch
 * (see ../README.md). Run locally:  npm install && npm start
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;
const PROTOCOL_VERSION = 1;

// ---------------------------------------------------------------- constants (mirror NetConfig.java)

const TICK_MS = 66;                 // 15 Hz
const RESPAWN_S = 3.0;
const MATCH_S = 480;
const TDM_KILL_LIMIT = 40;
const FFA_KILL_LIMIT = 25;
const MAX_PLAYERS = 12;
const FFA_PLAYERS = 8;
const PLAYER_RADIUS = 0.32;
const STAND_H = 1.78;
const CROUCH_H = 1.15;
const ROOM_JOIN_WINDOW_S = 300;     // can join a started match during first 5 minutes

// server copy of the client weapon table
const WEAPONS = [
  { name: 'ASSAULT RIFLE', mag: 30, dmg: 25, head: 2.0, range: 55, falloff: 0.35, rof: 10.5, pellets: 1 },
  { name: 'SMG', mag: 40, dmg: 19, head: 1.9, range: 32, falloff: 0.40, rof: 15.0, pellets: 1 },
  { name: 'SHOTGUN', mag: 8, dmg: 9.5, head: 1.6, range: 18, falloff: 0.20, rof: 1.15, pellets: 9, shotgun: true },
  { name: 'PISTOL', mag: 12, dmg: 34, head: 2.0, range: 35, falloff: 0.40, rof: 5.5, pellets: 1 },
];
const PRIMARY_WEAPONS = [0, 1, 2];
const PISTOL = 3;

const GRENADE_DAMAGE = 100;
const GRENADE_MIN = 25;
const GRENADE_RADIUS = 6.5;
const GRENADE_FUSE_S = 1.5;
const GRENADE_COOLDOWN_S = 3.0;
const THROW_SPEED = 16;

const MAPS = {
  dust: {
    name: 'Dustyard',
    spawnA: [[26, 2], [18, -24], [2, 26], [28, 14]],
    spawnB: [[-26, 2], [-18, 24], [-6, -24], [-28, -14]],
  },
  facility: {
    name: 'Facility',
    spawnA: [[0, 24], [-24, 24], [24, 24], [0, -28]],
    spawnB: [[0, -24], [-24, -24], [24, -24], [0, 28]],
  },
  arena: {
    name: 'Arena',
    spawnA: [[16, 0], [-16, 0], [0, 16], [0, -16]],
    spawnB: [[6, 13], [-6, -13], [-6, 13], [13, 6]],
  },
};
const MAP_IDS = Object.keys(MAPS);

// ---------------------------------------------------------------- state

let nextId = 1;
const players = new Map();   // id -> player
const rooms = new Map();     // id -> room
const queues = { tdm: [], ffa: [] };
let roomSeq = 1;

const statsFile = path.join(__dirname, 'stats.json');
let stats = { kills: 0, deaths: 0, matches: 0, players: {} };
let statsDirty = false;

function loadStats() {
  try {
    stats = JSON.parse(fs.readFileSync(statsFile, 'utf8'));
  } catch (e) { /* first run */ }
}
function saveStats() {
  if (!statsDirty) return;
  statsDirty = false;
  try {
    fs.writeFileSync(statsFile, JSON.stringify(stats, null, 2));
  } catch (e) { console.error('stats save failed', e.message); }
}
loadStats();

function statName(name) {
  if (!stats.players[name]) stats.players[name] = { kills: 0, deaths: 0, matches: 0 };
  return stats.players[name];
}

// ---------------------------------------------------------------- helpers

function rand(min, max) { return min + Math.random() * (max - min); }
function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }
function dist3(ax, ay, az, bx, by, bz) {
  const dx = ax - bx, dy = ay - by, dz = az - bz;
  return Math.sqrt(dx * dx + dy * dy + dz * dz);
}
function round(v, d) { const m = Math.pow(10, d); return Math.round(v * m) / m; }

function makeCode() {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let code = '';
  for (let i = 0; i < 4; i++) code += chars[Math.floor(Math.random() * chars.length)];
  return code;
}

function sanitizeName(name) {
  let n = String(name || '').replace(/[^\p{L}\p{N} _\-.]/gu, '').trim().slice(0, 16);
  if (!n) n = 'Ranger' + Math.floor(rand(100, 999));
  return n;
}

// ---------------------------------------------------------------- player helpers

function playerSpawn(p) {
  const map = MAPS[p.room.map];
  let list;
  if (p.room.mode === 'tdm') {
    list = p.team === 0 ? map.spawnA : map.spawnB;
  } else {
    list = Math.random() < 0.5 ? map.spawnA : map.spawnB;
  }
  const s = list[Math.floor(Math.random() * list.length)];
  p.pos = { x: s[0] + rand(-1, 1), y: 0.05, z: s[1] + rand(-1, 1) };
  p.hp = 100;
  p.alive = true;
  p.mag = WEAPONS[p.primary].mag;
  p.reserve = WEAPONS[p.primary].mag * 4;
  p.magP = WEAPONS[PISTOL].mag;
  p.reserveP = WEAPONS[PISTOL].mag * 5;
  p.grenades = 2;
  p.reloadUntil = 0;
  p.fireUntil = 0;
  p.grenadeCooldown = 0;
  p.weapon = 0;
}

function broadcast(room, obj) {
  const text = JSON.stringify(obj);
  for (const p of room.players) {
    if (p.ws.readyState === p.ws.OPEN) {
      try { p.ws.send(text); } catch (e) { /* ignore */ }
    }
  }
}

function send(ws, obj) {
  if (ws.readyState === ws.OPEN) {
    try { ws.send(JSON.stringify(obj)); } catch (e) { /* ignore */ }
  }
}

// ---------------------------------------------------------------- combat

function rayVsPlayerAabb(ox, oy, oz, dx, dy, dz, p) {
  const h = (p.flags & 2) ? CROUCH_H : STAND_H; // crouching flag
  const minx = p.pos.x - PLAYER_RADIUS, maxx = p.pos.x + PLAYER_RADIUS;
  const miny = p.pos.y, maxy = p.pos.y + h;
  const minz = p.pos.z - PLAYER_RADIUS, maxz = p.pos.z + PLAYER_RADIUS;
  let tmin = 0, tmax = Infinity;
  // x
  if (Math.abs(dx) < 1e-8) {
    if (ox < minx || ox > maxx) return -1;
  } else {
    let t1 = (minx - ox) / dx, t2 = (maxx - ox) / dx;
    if (t1 > t2) [t1, t2] = [t2, t1];
    tmin = Math.max(tmin, t1);
    tmax = Math.min(tmax, t2);
    if (tmin > tmax) return -1;
  }
  if (Math.abs(dy) < 1e-8) {
    if (oy < miny || oy > maxy) return -1;
  } else {
    let t1 = (miny - oy) / dy, t2 = (maxy - oy) / dy;
    if (t1 > t2) [t1, t2] = [t2, t1];
    tmin = Math.max(tmin, t1);
    tmax = Math.min(tmax, t2);
    if (tmin > tmax) return -1;
  }
  if (Math.abs(dz) < 1e-8) {
    if (oz < minz || oz > maxz) return -1;
  } else {
    let t1 = (minz - oz) / dz, t2 = (maxz - oz) / dz;
    if (t1 > t2) [t1, t2] = [t2, t1];
    tmin = Math.max(tmin, t1);
    tmax = Math.min(tmax, t2);
    if (tmin > tmax) return -1;
  }
  return tmin;
}

function friendly(shooter, victim) {
  if (shooter.room.mode === 'tdm') return shooter.team === victim.team;
  return shooter.id === victim.id;
}

function applyDamage(room, shooter, victim, dmg, head) {
  if (!victim.alive) return;
  victim.hp -= dmg;
  broadcast(room, {
    t: 'ev', e: 'hit', f: shooter.id, v: victim.id,
    dmg: round(dmg, 1), hp: round(Math.max(0, victim.hp), 1), head: !!head,
  });
  if (victim.hp <= 0) {
    victim.hp = 0;
    victim.alive = false;
    victim.respawnAt = Date.now() + RESPAWN_S * 1000;
    victim.deaths++;
    shooter.kills++;
    shooter.score += head ? 150 : 100;
    statName(victim.name).deaths++;
    statName(shooter.name).kills++;
    stats.deaths++;
    stats.kills++;
    statsDirty = true;
    broadcast(room, {
      t: 'ev', e: 'die', id: victim.id, by: shooter.id,
      w: WEAPONS[shooter.weapon === 0 ? shooter.primary : PISTOL].name,
      head: !!head, rt: RESPAWN_S,
    });
    if (room.mode === 'tdm') {
      // scoreA = team 1's score, scoreB = team 0's score.
      if (victim.team === 0) room.scoreA++;
      else room.scoreB++;
      broadcast(room, { t: 'ev', e: 'score', a: room.scoreA, b: room.scoreB });
    }
    checkWin(room, shooter);
  }
}

function checkWin(room, shooter) {
  if (room.ended) return;
  let done = false;
  if (room.mode === 'tdm') {
    done = room.scoreA >= TDM_KILL_LIMIT || room.scoreB >= TDM_KILL_LIMIT;
  } else {
    done = shooter.kills >= FFA_KILL_LIMIT;
  }
  if (done) endMatch(room);
}

function endMatch(room) {
  if (room.ended) return;
  room.ended = true;
  const list = room.players.map((p) => ({
    id: p.id, name: p.name, kills: p.kills, deaths: p.deaths, score: p.score,
  }));
  const topKills = room.mode === 'tdm'
    ? 0
    : room.players.reduce((top, p) => Math.max(top, p.kills), 0);
  for (const p of room.players) {
    let win;
    if (room.mode === 'tdm') {
      // Team 1 contributes to scoreA; team 0 contributes to scoreB.
      win = (room.scoreA > room.scoreB && p.team === 1) || (room.scoreB > room.scoreA && p.team === 0);
    } else {
      win = p.kills === topKills;
    }
    statName(p.name).matches++;
    send(p.ws, { t: 'ev', e: 'end', win, scores: list });
  }
  stats.matches++;
  statsDirty = true;
  // close the room shortly after
  setTimeout(() => {
    for (const p of room.players) {
      try { p.ws.close(1000, 'match over'); } catch (e) { /* ignore */ }
      p.room = null;
    }
    rooms.delete(room.id);
  }, 15000);
}

function handleShoot(p, msg) {
  const room = p.room;
  if (!room || !room.started || room.ended) return;
  if (!p.alive) return;
  const now = Date.now();
  const w = clamp(Math.round(msg.w || 0), 0, 1);
  if (w !== p.weapon) return;
  const spec = WEAPONS[w === 0 ? p.primary : PISTOL];
  if (p.fireUntil > now || p.reloadUntil > now) return;
  const magKey = w === 0 ? 'mag' : 'magP';
  const resKey = w === 0 ? 'reserve' : 'reserveP';
  if (p[magKey] <= 0) return;
  const ox = msg.ox, oy = msg.oy, oz = msg.oz;
  const dx = msg.dx, dy = msg.dy, dz = msg.dz;
  if (typeof ox !== 'number' || typeof dx !== 'number') return;
  const len = Math.sqrt(dx * dx + dy * dy + dz * dz);
  if (len < 0.5 || len > 1.5) return;
  // origin must be near the player
  const eyeY = (p.flags & 2) ? 1.02 : 1.62;
  if (dist3(ox, oy, oz, p.pos.x, p.pos.y + eyeY, p.pos.z) > 2.5) return;
  const ndx = dx / len, ndy = dy / len, ndz = dz / len;

  p[magKey]--;
  p.fireUntil = now + 1000 / spec.rof;
  if (p[magKey] <= 0 && p[resKey] > 0) {
    // auto reload
    const need = spec.mag - p[magKey];
    const take = Math.min(need, p[resKey]);
    p[resKey] -= take;
    p[magKey] += take;
    p.reloadUntil = now + (spec.shotgun ? 0.55 : 2.0) * 1000;
  }

  // pellets
  for (let i = 0; i < spec.pellets; i++) {
    let px = ndx, py = ndy, pz = ndz;
    if (spec.pellets > 1) {
      px += rand(-0.055, 0.055);
      py += rand(-0.055, 0.055);
      pz += rand(-0.055, 0.055);
      const pl = Math.sqrt(px * px + py * py + pz * pz);
      px /= pl; py /= pl; pz /= pl;
    }
    let best = null, bestT = Infinity;
    for (const v of room.players) {
      if (v.id === p.id || !v.alive) continue;
      if (friendly(p, v)) continue;
      const t = rayVsPlayerAabb(ox, oy, oz, px, py, pz, v);
      if (t >= 0 && t < bestT) { bestT = t; best = v; }
    }
    if (!best) continue;
    const hx = ox + px * bestT, hy = oy + py * bestT, hz = oz + pz * bestT;
    const head = hy > best.pos.y + 1.42;
    const falloff = bestT <= spec.range ? 1
      : Math.max(spec.falloff, 1 - ((bestT - spec.range) / spec.range) * (1 - spec.falloff));
    const dmg = spec.dmg * falloff * (head ? spec.head : 1);
    applyDamage(room, p, best, dmg, head);
  }

  // tell others for tracers / sound
  for (const v of room.players) {
    if (v.id === p.id) continue;
    send(v.ws, {
      t: 'ev', e: 'shot', id: p.id,
      ox: round(ox, 2), oy: round(oy, 2), oz: round(oz, 2),
      dx: round(ndx, 3), dy: round(ndy, 3), dz: round(ndz, 3),
      w: p.weapon,
    });
  }
}

function handleGrenade(p, msg) {
  const room = p.room;
  if (!room || !room.started || room.ended) return;
  if (!p.alive) return;
  const now = Date.now();
  if (p.grenades <= 0 || p.grenadeCooldown > now) return;
  const ox = msg.ox, oy = msg.oy, oz = msg.oz;
  const dx = msg.dx, dy = msg.dy, dz = msg.dz;
  if (typeof ox !== 'number' || typeof dx !== 'number') return;
  const len = Math.sqrt(dx * dx + dy * dy + dz * dz);
  if (len < 0.5 || len > 1.5) return;
  const ndx = dx / len, ndy = dy / len, ndz = dz / len;
  p.grenades--;
  p.grenadeCooldown = now + GRENADE_COOLDOWN_S * 1000;
  for (const v of room.players) {
    if (v.id === p.id) continue;
    send(v.ws, {
      t: 'ev', e: 'gren', id: p.id,
      ox: round(ox, 2), oy: round(oy, 2), oz: round(oz, 2),
      dx: round(ndx, 3), dy: round(ndy, 3), dz: round(ndz, 3),
    });
  }
  // approximate landing point
  const bx = ox + ndx * THROW_SPEED * GRENADE_FUSE_S;
  const by = Math.max(0.4, oy + ndy * THROW_SPEED * GRENADE_FUSE_S);
  const bz = oz + ndz * THROW_SPEED * GRENADE_FUSE_S;
  setTimeout(() => {
    broadcast(room, { t: 'ev', e: 'boom', x: round(bx, 2), y: round(by, 2), z: round(bz, 2) });
    for (const v of room.players) {
      if (!v.alive) continue;
      if (friendly(p, v)) continue;
      const d = dist3(bx, by, bz, v.pos.x, v.pos.y + 1, v.pos.z);
      if (d > GRENADE_RADIUS) continue;
      const t = d / GRENADE_RADIUS;
      const dmg = GRENADE_MIN + (GRENADE_DAMAGE - GRENADE_MIN) * (1 - t);
      applyDamage(room, p, v, dmg, false);
    }
  }, GRENADE_FUSE_S * 1000);
}

// ---------------------------------------------------------------- rooms & matchmaking

function createRoom(mode, map) {
  const room = {
    id: roomSeq++,
    mode,
    map,
    code: makeCode(),
    players: [],
    started: false,
    startedAt: 0,
    ended: false,
    endAt: 0,
    scoreA: 0,
    scoreB: 0,
    teamCounts: [0, 0],
  };
  rooms.set(room.id, room);
  return room;
}

function joinRoom(room, p) {
  p.room = room;
  room.players.push(p);
  if (room.mode === 'tdm') {
    p.team = room.teamCounts[0] <= room.teamCounts[1] ? 0 : 1;
    room.teamCounts[p.team]++;
  } else {
    p.team = 100 + p.id;
  }
  p.kills = 0;
  p.deaths = 0;
  p.score = 0;
  return room;
}

function startRoom(room) {
  room.started = true;
  room.startedAt = Date.now();
  room.endAt = Date.now() + MATCH_S * 1000;
  for (const p of room.players) {
    playerSpawn(p);
    send(p.ws, {
      t: 'match',
      id: room.id,
      mode: room.mode,
      map: room.map,
      team: p.team,
      code: room.code,
      you: p.id,
      players: room.players.map((o) => ({ id: o.id, name: o.name, team: o.team })),
    });
  }
  for (const p of room.players) {
    broadcast(room, {
      t: 'ev', e: 'spawn', id: p.id,
      x: round(p.pos.x, 2), y: p.pos.y, z: round(p.pos.z, 2),
      hp: 100, name: p.name, team: p.team,
    });
  }
  console.log(`[match] room ${room.code} started (${room.mode} ${room.map}, ${room.players.length} players)`);
}

function matchmakingTick() {
  const now = Date.now();
  for (const mode of ['tdm', 'ffa']) {
    const q = queues[mode];
    if (q.length === 0) continue;
    // try to join an existing open room first
    let target = null;
    for (const room of rooms.values()) {
      if (room.mode !== mode) continue;
      if (!room.started && room.players.length < (mode === 'tdm' ? MAX_PLAYERS : FFA_PLAYERS)) {
        target = room;
        break;
      }
      if (room.started && !room.ended
          && (now - room.startedAt) < ROOM_JOIN_WINDOW_S * 1000
          && room.players.length < (mode === 'tdm' ? MAX_PLAYERS : FFA_PLAYERS)) {
        target = room;
        break;
      }
    }
    if (target) {
      const p = q.shift();
      if (players.has(p.id)) {
        joinRoom(target, p);
        if (!target.started && target.players.length >= 2) {
          startRoom(target);
        } else if (target.started) {
          playerSpawn(p);
          send(p.ws, {
            t: 'match',
            id: target.id,
            mode: target.mode,
            map: target.map,
            team: p.team,
            code: target.code,
            you: p.id,
            players: target.players.map((o) => ({ id: o.id, name: o.name, team: o.team })),
          });
          broadcast(target, {
            t: 'ev', e: 'spawn', id: p.id,
            x: round(p.pos.x, 2), y: p.pos.y, z: round(p.pos.z, 2),
            hp: 100, name: p.name, team: p.team,
          });
          console.log(`[match] ${p.name} joined running room ${target.code}`);
        }
      }
      continue;
    }
    // create a new room when at least 2 are queued
    if (q.length >= 2) {
      const map = MAP_IDS[Math.floor(Math.random() * MAP_IDS.length)];
      const room = createRoom(mode, map);
      const cap = mode === 'tdm' ? MAX_PLAYERS : FFA_PLAYERS;
      while (q.length > 0 && room.players.length < cap) {
        const p = q.shift();
        if (players.has(p.id)) joinRoom(room, p);
      }
      startRoom(room);
    }
  }
}

// ---------------------------------------------------------------- room ticks

function tickRooms() {
  const now = Date.now();
  for (const room of rooms.values()) {
    if (!room.started) continue;
    // respawns
    for (const p of room.players) {
      if (!p.alive && p.respawnAt <= now) {
        playerSpawn(p);
        broadcast(room, {
          t: 'ev', e: 'spawn', id: p.id,
          x: round(p.pos.x, 2), y: p.pos.y, z: round(p.pos.z, 2),
          hp: 100, name: p.name, team: p.team,
        });
      }
    }
    // state broadcast
    const state = {
      t: 'state',
      seq: room.seq = (room.seq || 0) + 1,
      players: room.players.map((p) => ({
        id: p.id,
        name: p.name,
        team: p.team,
        x: round(p.pos.x, 2),
        y: round(p.pos.y, 2),
        z: round(p.pos.z, 2),
        yaw: round(p.yaw, 3),
        pitch: round(p.pitch, 3),
        hp: round(Math.max(0, p.hp), 1),
        f: p.flags || 0,
        w: p.weapon,
      })),
    };
    broadcast(room, state);
    // timeout
    if (!room.ended && now >= room.endAt) {
      endMatch(room);
    }
  }
}

// ---------------------------------------------------------------- websocket

const server = http.createServer(handler);
const wss = new WebSocketServer({ server, maxPayload: 8 * 1024 });

wss.on('connection', (ws) => {
  const p = {
    id: nextId++,
    ws,
    name: 'player',
    primary: PRIMARY_WEAPONS[Math.floor(Math.random() * PRIMARY_WEAPONS.length)],
    pos: { x: 0, y: 0, z: 0 },
    yaw: 0,
    pitch: 0,
    hp: 100,
    alive: false,
    kills: 0,
    deaths: 0,
    score: 0,
    weapon: 0,
    mag: 0,
    reserve: 0,
    magP: 0,
    reserveP: 0,
    grenades: 2,
    reloadUntil: 0,
    fireUntil: 0,
    grenadeCooldown: 0,
    room: null,
    flags: 0,
    lastInputAt: Date.now(),
    queuedMode: null,
  };
  players.set(p.id, p);
  ws.on('message', (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch (e) {
      return;
    }
    if (!msg || typeof msg.t !== 'string') return;
    p.lastInputAt = Date.now();
    switch (msg.t) {
      case 'hello': {
        if (msg.v !== PROTOCOL_VERSION) {
          send(ws, { t: 'error', m: 'Version mismatch - update your game!' });
          break;
        }
        p.name = sanitizeName(msg.name);
        if (typeof msg.w === 'number' && PRIMARY_WEAPONS.includes(msg.w)) p.primary = msg.w;
        send(ws, { t: 'welcome', id: p.id, name: p.name });
        break;
      }
      case 'queue': {
        const mode = msg.mode === 'tdm' ? 'tdm' : 'ffa';
        leaveRoom(p);
        p.queuedMode = mode;
        queues[mode].push(p);
        send(ws, { t: 'queued', mode });
        matchmakingTick();
        break;
      }
      case 'roomCreate': {
        const mode = msg.mode === 'tdm' ? 'tdm' : 'ffa';
        leaveRoom(p);
        const map = MAP_IDS[Math.floor(Math.random() * MAP_IDS.length)];
        const room = createRoom(mode, map);
        joinRoom(room, p);
        p.queuedMode = null;
        send(ws, { t: 'roomCode', code: room.code, mode });
        break;
      }
      case 'roomJoin': {
        const code = String(msg.code || '').toUpperCase().trim();
        leaveRoom(p);
        const room = [...rooms.values()].find((r) => r.code === code && !r.ended);
        if (!room) {
          send(ws, { t: 'roomError', m: 'Room not found' });
          break;
        }
        const cap = room.mode === 'tdm' ? MAX_PLAYERS : FFA_PLAYERS;
        if (room.players.length >= cap || (room.started
            && Date.now() - room.startedAt > ROOM_JOIN_WINDOW_S * 1000)) {
          send(ws, { t: 'roomError', m: 'Room is full or the match is too far along' });
          break;
        }
        joinRoom(room, p);
        p.queuedMode = null;
        if (!room.started && room.players.length >= 2) {
          startRoom(room);
        } else if (room.started) {
          playerSpawn(p);
          send(ws, {
            t: 'match',
            id: room.id, mode: room.mode, map: room.map, team: p.team,
            code: room.code, you: p.id,
            players: room.players.map((o) => ({ id: o.id, name: o.name, team: o.team })),
          });
          broadcast(room, {
            t: 'ev', e: 'spawn', id: p.id,
            x: round(p.pos.x, 2), y: p.pos.y, z: round(p.pos.z, 2),
            hp: 100, name: p.name, team: p.team,
          });
        } else {
          send(ws, { t: 'queued', mode: room.mode });
        }
        break;
      }
      case 'leave': {
        leaveRoom(p);
        break;
      }
      case 'input': {
        if (typeof msg.x !== 'number' || typeof msg.z !== 'number') break;
        const d = dist3(msg.x, msg.y || 0, msg.z, p.pos.x, p.pos.y, p.pos.z);
        if (d > 15) break; // teleport guard
        p.pos.x = msg.x;
        p.pos.y = clamp(msg.y || 0, -1, 12);
        p.pos.z = msg.z;
        p.yaw = clamp(msg.yaw || 0, -Math.PI, Math.PI);
        p.pitch = clamp(msg.pitch || 0, -1.5, 1.5);
        p.flags = clamp(Math.round(msg.f || 0), 0, 255);
        if (msg.w === 0 || msg.w === 1) p.weapon = msg.w;
        break;
      }
      case 'shoot':
        handleShoot(p, msg);
        break;
      case 'grenade':
        handleGrenade(p, msg);
        break;
      case 'chat': {
        if (!p.room) break;
        const text = String(msg.m || '').slice(0, 140);
        if (!text) break;
        broadcast(p.room, { t: 'chat', id: p.id, name: p.name, m: text });
        break;
      }
      case 'ping':
        send(ws, { t: 'pong', tm: msg.tm });
        break;
      default:
        break;
    }
  });

  ws.on('close', () => {
    leaveRoom(p);
    players.delete(p.id);
  });
  ws.on('error', () => { /* handled by close */ });
});

function leaveRoom(p) {
  if (p.queuedMode) {
    const q = queues[p.queuedMode];
    const i = q.indexOf(p);
    if (i >= 0) q.splice(i, 1);
    p.queuedMode = null;
  }
  const room = p.room;
  if (!room) return;
  const i = room.players.indexOf(p);
  if (i >= 0) room.players.splice(i, 1);
  if (room.mode === 'tdm' && p.team === 0) room.teamCounts[0]--;
  if (room.mode === 'tdm' && p.team === 1) room.teamCounts[1]--;
  p.room = null;
  if (room.players.length === 0) {
    rooms.delete(room.id);
  }
}

// ---------------------------------------------------------------- http

function handler(req, res) {
  const url = (req.url || '/').split('?')[0];
  if (url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ ok: true, rooms: rooms.size, players: players.size }));
    return;
  }
  if (url === '/api/stats') {
    const top = Object.entries(stats.players)
      .map(([name, s]) => ({ name, kills: s.kills, deaths: s.deaths, matches: s.matches }))
      .sort((a, b) => b.kills - a.kills)
      .slice(0, 25);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ totalKills: stats.kills, totalDeaths: stats.deaths, matches: stats.matches, top }));
    return;
  }
  if (url === '/' || url === '/index.html') {
    const file = path.join(__dirname, 'public', 'index.html');
    fs.readFile(file, (err, data) => {
      if (err) {
        res.writeHead(500);
        res.end('landing page missing');
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end(data);
    });
    return;
  }
  res.writeHead(404);
  res.end('not found');
}

// ---------------------------------------------------------------- main

setInterval(tickRooms, TICK_MS);
setInterval(matchmakingTick, 1000);
setInterval(saveStats, 10000);

server.listen(PORT, () => {
  console.log(`TactShoot server listening on :${PORT}`);
  console.log(`  landing page : http://localhost:${PORT}/`);
  console.log(`  stats api    : http://localhost:${PORT}/api/stats`);
});

process.on('SIGTERM', () => {
  saveStats();
  process.exit(0);
});
process.on('SIGINT', () => {
  saveStats();
  process.exit(0);
});
