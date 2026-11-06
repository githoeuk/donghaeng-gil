import https from 'node:https';
import fs from 'node:fs';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const args=process.argv.slice(2);const option=name=>{const i=args.indexOf(name);return i>=0?args[i+1]:undefined;};
const base=new URL(option('--url')||'https://127.0.0.1:8443');
if(base.protocol!=='https:'||base.username||base.password||base.pathname!=='/'||base.search||base.hash)throw Error('Use an HTTPS origin without credentials/path/query.');
const caPath=option('--ca');const ca=caPath?fs.readFileSync(caPath):undefined;
const agent=new https.Agent({ca,keepAlive:true});let tlsVersion='';
function request(method,path,token,body,customAgent=agent,servername){return new Promise((resolve,reject)=>{
 const data=body===undefined?undefined:JSON.stringify(body);
 const req=https.request(new URL(path,base),{method,agent:customAgent,...(servername?{servername}:{}),headers:{...(token?{Authorization:'Bearer '+token}:{}),...(data?{'Content-Type':'application/json','Content-Length':Buffer.byteLength(data)}:{})}},res=>{
  tlsVersion=res.socket.getProtocol();let raw='';res.on('data',chunk=>{raw+=chunk;if(raw.length>131072)res.destroy(Error('Oversized response'));});res.on('error',reject);res.on('end',()=>{let body;try{body=JSON.parse(raw)}catch{body={}}resolve({status:res.statusCode,body});});
 });req.setTimeout(3000,()=>req.destroy(Error('Request timeout')));req.on('error',reject);req.end(data);
});}
async function call(method,path,token,body,status=200){const r=await request(method,path,token,body);assert.equal(r.status,status,`${method} ${path} status`);return r.body;}
let a,b;
try{
 if(ca){
  // This check is intentionally local-only: public CAs need not fail system trust.
  const systemOnly=new https.Agent({keepAlive:false});let rejected=false;
  try{await request('GET','/health',null,undefined,systemOnly);}catch(e){rejected=['UNABLE_TO_VERIFY_LEAF_SIGNATURE','SELF_SIGNED_CERT_IN_CHAIN','DEPTH_ZERO_SELF_SIGNED_CERT','UNABLE_TO_GET_ISSUER_CERT_LOCALLY'].includes(e.code);}
  finally{systemOnly.destroy();}assert(rejected,'Private CA must be rejected without explicit trust');
  let wrongHost=false;try{await request('GET','/health',null,undefined,agent,'wrong-host.example.invalid');}catch(e){wrongHost=e.code==='ERR_TLS_CERT_ALTNAME_INVALID';}assert(wrongHost,'Trusted CA must not bypass hostname checks');
 }
 const health=await call('GET','/health');assert.equal(health.ok,true);assert.equal(health.apiVersion,2);assert(['TLSv1.2','TLSv1.3'].includes(tlsVersion));
 await call('GET','/not-an-api',null,undefined,404);await call('GET','/v1/group',null,undefined,401);
 await call('POST','/v1/sessions',null,{name:'x'.repeat(20000)},413);
 a=await call('POST','/v1/sessions',null,{name:'TlsSmokeA'},201);b=await call('POST','/v1/sessions',null,{name:'TlsSmokeB'},201);
 const group=await call('POST','/v1/groups',a.token,{});
 const pending=await call('POST','/v1/groups/join',b.token,{code:group.inviteCode});assert(pending.pending);assert.equal(pending.groupId,'');assert.equal(pending.members.length,0);
 await call('POST','/v1/groups/requests/approve',a.token,{requestId:pending.requestId});
 let snapshot=await call('GET','/v1/group',b.token);assert(snapshot.members.every(m=>!m.sharing&&m.state===null));
 const shareA=await call('POST','/v1/sharing',a.token,{enabled:true});const shareB=await call('POST','/v1/sharing',b.token,{enabled:true});
 const state={latitude:37.4,longitude:127.1,address:'Synthetic HTTPS test address',destinationId:'tls-test',destinationName:'Synthetic HTTPS destination',destinationAddress:'Synthetic HTTPS detail',remainingSeconds:480,remainingMeters:1800,sourceAgeMs:0};
 await call('PUT','/v1/state',a.token,{epoch:shareA.shareEpoch,sequence:1,available:true,state});await call('PUT','/v1/state',b.token,{epoch:shareB.shareEpoch,sequence:1,available:true,state:{...state,latitude:37.401}});
 snapshot=await call('GET','/v1/group',b.token);assert.equal(snapshot.members.find(m=>m.id===a.userId).state.remainingSeconds,480);snapshot=await call('GET','/v1/group',a.token);assert.equal(snapshot.members.find(m=>m.id===b.userId).state.latitude,37.401);
 const id=randomUUID();await call('POST','/v1/messages',a.token,{id,kind:'REST'});await call('POST',`/v1/messages/${id}/received`,b.token,{});await call('POST',`/v1/messages/${id}/ack`,b.token,{});assert.equal((await call('GET','/v1/group',a.token)).messages[0].delivery,'ACKNOWLEDGED');
 await call('POST','/v1/sharing',b.token,{enabled:false});snapshot=await call('GET','/v1/group',a.token);assert.equal(snapshot.members.find(m=>m.id===b.userId).state,null);
 await call('POST','/v1/groups/leave',a.token,{});snapshot=await call('GET','/v1/group',b.token);assert.equal(snapshot.groupId,'');assert.equal(snapshot.members.length,0);assert.equal(snapshot.messages.length,0);
 console.log(`PASS HTTPS ${tlsVersion}: trust/hostname, health, route/body/auth limits, approval, separate consent, two-client state, message ack, stop and group close. No tokens or payloads logged.`);
} finally {
 for(const session of [a,b])if(session)try{await call('POST','/v1/groups/leave',session.token,{});}catch{}
 agent.destroy();
}
