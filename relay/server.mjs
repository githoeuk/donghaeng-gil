import http from 'node:http';
import {randomBytes, randomUUID} from 'node:crypto';
import {pathToFileURL} from 'node:url';

const token = () => randomBytes(32).toString('base64url');
const kinds = new Set(['REST','WAIT','GO','OK']);
class ApiError extends Error { constructor(status, code) { super(code); this.status = status; } }
const check = (condition, status, code) => { if (!condition) throw new ApiError(status, code); };
const text = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.length <= max;

/** Single-process ephemeral relay. No credentials, locations, or messages are logged or persisted. */
export function createRelay({clock = Date.now} = {}) {
  const sessions = new Map(), groups = new Map(), buckets = new Map();
  const now = () => clock();
  function removeMember(session) {
    const group = groups.get(session.groupId);
    if (group) {
      group.members.delete(session.id);
      if (!group.members.size) groups.delete(group.id);
    }
    session.groupId = null; session.sharing = false; session.state = null; session.epoch = null;
  }
  function prune() {
    const at = now();
    for (const [key, entry] of buckets) if (at - entry.at > 60000) buckets.delete(key);
    for (const [key, session] of sessions) {
      if (session.groupId && at - session.lastSeen > 60000) removeMember(session);
      if (session.state && at - session.state.receivedAt > 60000) session.state = null;
      if (at - session.createdAt > 7200000) { removeMember(session); sessions.delete(key); }
    }
    for (const group of groups.values()) group.messages = group.messages.filter(message => at - message.sentAt < 1800000);
  }
  function rate(key, limit) {
    if (!buckets.has(key) && buckets.size > 1000) throw new ApiError(503, 'BUSY');
    const entry = buckets.get(key);
    if (!entry || now() - entry.at > 60000) buckets.set(key,{at: now(), count: 1});
    else { check(entry.count++ < limit,429,'RATE_LIMIT'); }
  }
  function member(session) {
    const group = groups.get(session.groupId);
    check(group && group.members.has(session.id),409,'JOIN_REQUIRED'); return group;
  }
  function snapshot(session) {
    const group = session.groupId ? member(session) : null;
    if (!group) return {serverNow: now(), userId: session.id, groupId: '', inviteCode: '', members: [], messages: []};
    const members = [...group.members.values()].map(s => ({id: s.id, name: s.name,
      sharing: s.sharing, online: now() - s.lastSeen <= 15000,
      state: s.state ? {...s.state.data, sourceAgeMs: s.state.age + Math.max(0,now() - s.state.receivedAt)} : null}));
    const messages = group.messages.map(m => ({id: m.id, senderId: m.senderId, sender: m.sender,
      kind: m.kind, sentAt: m.sentAt,
      delivery: m.senderId === session.id
        ? (m.acked.size ? 'ACKNOWLEDGED' : m.received.size ? 'DELIVERED' : 'PENDING')
        : (m.acked.has(session.id) ? 'ACKNOWLEDGED' : m.received.has(session.id) ? 'DELIVERED' : 'PENDING')}));
    return {serverNow: now(), userId: session.id, groupId: group.id, inviteCode: group.code, members, messages};
  }
  async function readBody(req) {
    check((req.headers['content-type'] || '').split(';')[0] === 'application/json',415,'JSON_REQUIRED');
    let size = 0, chunks = [];
    for await (const chunk of req) { size += chunk.length; check(size <= 16384,413,'BODY_TOO_LARGE'); chunks.push(chunk); }
    try { const value = JSON.parse(Buffer.concat(chunks).toString('utf8')); check(value && !Array.isArray(value) && typeof value === 'object',400,'BAD_JSON'); return value; }
    catch (error) { if (error instanceof ApiError) throw error; throw new ApiError(400,'BAD_JSON'); }
  }
  function validateState(body) {
    check(Number.isSafeInteger(body.sequence) && body.sequence > 0,400,'BAD_SEQUENCE');
    if (body.available === false) return null;
    const p = body.state;
    check(p && Number.isFinite(p.latitude) && p.latitude >= -90 && p.latitude <= 90
      && Number.isFinite(p.longitude) && p.longitude >= -180 && p.longitude <= 180,400,'BAD_LOCATION');
    for (const key of ['address','destinationId','destinationName','destinationAddress']) check(typeof p[key] === 'string' && p[key].length <= 240,400,'BAD_TEXT');
    check(Number.isInteger(p.remainingSeconds) && p.remainingSeconds >= -1 && p.remainingSeconds <= 604800
      && Number.isInteger(p.remainingMeters) && p.remainingMeters >= -1 && p.remainingMeters <= 20000000,400,'BAD_ETA');
    check(Number.isFinite(p.sourceAgeMs) && p.sourceAgeMs >= 0 && p.sourceAgeMs <= 15000,400,'STALE_SOURCE');
    return {latitude:p.latitude, longitude:p.longitude, address:p.address,
      destinationId:p.destinationId,destinationName:p.destinationName,destinationAddress:p.destinationAddress,
      remainingSeconds:p.remainingSeconds,remainingMeters:p.remainingMeters, sequence:body.sequence,
      generatedAt:now(), sourceAgeMs:p.sourceAgeMs};
  }
  const server = http.createServer(async (req,res) => {
    res.setHeader('Content-Type','application/json; charset=utf-8'); res.setHeader('Cache-Control','no-store');
    res.setHeader('X-Content-Type-Options','nosniff');
    try {
      prune(); const path = req.url; const method = req.method;
      if (method === 'GET' && path === '/health') { res.end(JSON.stringify({ok:true,apiVersion:1})); return; }
      const body = method === 'GET' ? null : await readBody(req);
      if (method === 'POST' && path === '/v1/sessions') {
        rate('sessions:' + req.socket.remoteAddress,30); check(sessions.size < 100,503,'BUSY');
        check(text(body.name,24),400,'BAD_NAME');
        const bearer = token(), session = {id:randomUUID(),name:body.name.trim(),createdAt:now(),lastSeen:now(),groupId:null,sharing:false,state:null,sequence:0,epoch:null};
        sessions.set(bearer,session); res.statusCode=201; res.end(JSON.stringify({token:bearer,userId:session.id})); return;
      }
      const bearer = (req.headers.authorization || '').replace(/^Bearer /,''); const session = sessions.get(bearer);
      check(session,401,'UNAUTHORIZED'); session.lastSeen = now(); rate('requests:' + session.id,300);
      if (method === 'POST' && path === '/v1/groups') {
        rate('create:' + session.id,10); check(!session.groupId,409,'LEAVE_FIRST'); check(groups.size < 50,503,'BUSY');
        let code; do { code = randomBytes(5).toString('hex').toUpperCase(); } while ([...groups.values()].some(g => g.code === code));
        const group = {id:randomUUID(),code,createdAt:now(),members:new Map(),messages:[]};
        groups.set(group.id,group); group.members.set(session.id,session); session.groupId=group.id;
      } else if (method === 'POST' && path === '/v1/groups/join') {
        rate('join:' + req.socket.remoteAddress,15); check(!session.groupId,409,'LEAVE_FIRST');
        check(typeof body.code === 'string',400,'BAD_CODE');
        const group = [...groups.values()].find(g => g.code === body.code.toUpperCase() && now()-g.createdAt < 1800000);
        check(group,404,'INVALID_OR_EXPIRED_CODE'); check(group.members.size < 8,409,'GROUP_FULL');
        group.members.set(session.id,session); session.groupId=group.id;
      } else if (method === 'POST' && path === '/v1/groups/leave') {
        removeMember(session);
      } else if (method === 'POST' && path === '/v1/sharing') {
        member(session); check(typeof body.enabled === 'boolean',400,'BAD_SHARING');
        session.sharing=body.enabled; session.state=null; session.sequence=0; session.epoch=body.enabled?token():null;
      } else if (method === 'PUT' && path === '/v1/state') {
        member(session); check(session.sharing && body.epoch === session.epoch,409,'SHARING_STOPPED');
        const data = validateState(body); check(body.sequence > session.sequence,409,'OLD_SEQUENCE'); session.sequence=body.sequence;
        session.state = data ? {data,receivedAt:now(),age:data.sourceAgeMs} : null;
      } else if (method === 'POST' && path === '/v1/messages') {
        const group = member(session); check(session.sharing,409,'START_SHARING_FIRST');
        check(typeof body.id === 'string' && /^[a-zA-Z0-9-]{1,64}$/.test(body.id) && kinds.has(body.kind),400,'BAD_MESSAGE');
        const existing = group.messages.find(m=>m.id===body.id);
        check(!existing || (existing.senderId === session.id && existing.kind === body.kind),409,'ID_CONFLICT');
        if (!existing) { group.messages.push({id:body.id,senderId:session.id,sender:session.name,kind:body.kind,sentAt:now(),received:new Set(),acked:new Set()}); if(group.messages.length>50)group.messages.shift(); }
      } else if (method === 'POST' && /^\/v1\/messages\/[a-zA-Z0-9-]{1,64}\/(received|ack)$/.test(path)) {
        const group = member(session), parts=path.split('/'), message=group.messages.find(m=>m.id===parts[3]);
        check(message && message.senderId !== session.id,404,'MESSAGE_NOT_FOUND');
        if(parts[4]==='received')message.received.add(session.id);
        else { check(message.received.has(session.id),409,'RECEIVE_FIRST'); message.acked.add(session.id); }
      } else if (!(method === 'GET' && path === '/v1/group')) throw new ApiError(404,'NOT_FOUND');
      const result = snapshot(session);
      if(method==='POST' && path==='/v1/sharing')result.shareEpoch=session.epoch;
      res.end(JSON.stringify(result));
    } catch (error) {
      res.statusCode = error instanceof ApiError ? error.status : 500;
      res.end(JSON.stringify({error:error instanceof ApiError ? error.message : 'INTERNAL_ERROR'}));
    }
  });
  server.requestTimeout=10000; server.headersTimeout=10000; server.keepAliveTimeout=5000;
  const cleanup = setInterval(prune,1000); cleanup.unref(); server.on('close',()=>clearInterval(cleanup));
  return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const host=process.env.HOST || '127.0.0.1', port=Number(process.env.PORT || 8787);
  const server=createRelay(); server.listen(port,host,()=>console.log(`Donghaeng relay listening on ${host}:${port} (in-memory, API v1)`));
  process.on('SIGTERM',()=>server.close()); process.on('SIGINT',()=>server.close());
}
