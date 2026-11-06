import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createRelay} from './server.mjs';
test('two clients: consent, isolation, location/ETA, delivery, ack, epochs, expiry',async()=>{
 let now=100000;const server=createRelay({clock:()=>now});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url=`http://127.0.0.1:${server.address().port}`;
 async function call(method,path,token,body,expected=200){const res=await fetch(url+path,{method,headers:{...(token?{Authorization:`Bearer ${token}`} :{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});assert.equal(res.status,expected,path);return res.json();}
 try{
  await call('GET','/v1/group',null,null,401);
  const a=await call('POST','/v1/sessions',null,{name:'A'},201),b=await call('POST','/v1/sessions',null,{name:'B'},201),c=await call('POST','/v1/sessions',null,{name:'C'},201);
  const group=await call('POST','/v1/groups',a.token,{});
  const pendingB=await call('POST','/v1/groups/join',b.token,{code:group.inviteCode});
  await call('POST','/v1/groups/requests/approve',a.token,{requestId:pendingB.requestId});
  assert.equal((await call('GET','/v1/group',c.token)).members.length,0);
  await call('PUT','/v1/state',a.token,{sequence:1},409);
  const enabled=await call('POST','/v1/sharing',a.token,{enabled:true});
  const state={latitude:37.4,longitude:127.1,address:'test address',destinationId:'poi',destinationName:'test destination',destinationAddress:'test detail',remainingSeconds:600,remainingMeters:5000,sourceAgeMs:100};
  const update={epoch:enabled.shareEpoch,sequence:1,available:true,state};
  await call('PUT','/v1/state',a.token,update);
  const received=await call('GET','/v1/group',b.token);assert.deepEqual(received.members.find(m=>m.id===a.userId).state.latitude,37.4);assert.equal(received.members[0].state.remainingSeconds,600);
  await call('PUT','/v1/state',a.token,update,409);
  await call('PUT','/v1/state',a.token,{...update,sequence:2,state:{...state,sourceAgeMs:15001}},400);
  await call('POST','/v1/messages',a.token,{id:'message-one',kind:'REST'});
  await call('POST','/v1/messages',a.token,{id:'message-one',kind:'REST'});
  assert.equal((await call('GET','/v1/group',b.token)).messages.length,1);
  await call('POST','/v1/messages/message-one/ack',b.token,{},409);
  await call('POST','/v1/messages/message-one/received',c.token,{},409);
  await call('POST','/v1/messages/message-one/received',b.token,{});
  assert.equal((await call('GET','/v1/group',a.token)).messages[0].delivery,'DELIVERED');
  await call('POST','/v1/messages/message-one/ack',b.token,{});
  assert.equal((await call('GET','/v1/group',a.token)).messages[0].delivery,'ACKNOWLEDGED');
  await call('POST','/v1/sharing',a.token,{enabled:false});assert.equal((await call('GET','/v1/group',b.token)).members[0].state,null);
  const again=await call('POST','/v1/sharing',a.token,{enabled:true});assert.notEqual(again.shareEpoch,enabled.shareEpoch);
  await call('PUT','/v1/state',a.token,{...update,sequence:2},409);
  await call('PUT','/v1/state',a.token,{...update,epoch:again.shareEpoch});
  now+=16000;const stale=await call('GET','/v1/group',b.token);assert.equal(stale.members[0].online,false);assert.equal(stale.members[0].state.sourceAgeMs,16100);
  now+=61000;assert.equal((await call('GET','/v1/group',b.token)).groupId,'');
  await call('POST','/v1/groups/join',c.token,{code:group.inviteCode},404);
 }finally{await new Promise(r=>server.close(r));}
});

test('invite expires exactly at 30 minutes while active members remain connected',async()=>{
 let now=100000;const server=createRelay({clock:()=>now});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url='http://127.0.0.1:'+server.address().port;
 async function call(path,token,body){const r=await fetch(url+path,{method:body?'POST':'GET',headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,body:await r.json()};}
 try{
  const a=(await call('/v1/sessions',null,{name:'A'})).body;
  const b=(await call('/v1/sessions',null,{name:'B'})).body;
  const c=(await call('/v1/sessions',null,{name:'C'})).body;
  const group=(await call('/v1/groups',a.token,{})).body;
  for(let i=0;i<59;i++){now+=30000;await call('/v1/group',a.token);await call('/v1/group',b.token);await call('/v1/group',c.token);}
  now+=29999;const pendingB=await call('/v1/groups/join',b.token,{code:group.inviteCode});assert.equal(pendingB.status,200);
  assert.equal((await call('/v1/groups/requests/approve',a.token,{requestId:pendingB.body.requestId})).status,200);
  await call('/v1/group',a.token);
  now+=1;const rejected=await call('/v1/groups/join',c.token,{code:group.inviteCode});
  assert.equal(rejected.status,404);assert.equal(rejected.body.error,'INVALID_OR_EXPIRED_CODE');
  assert.equal((await call('/v1/group',a.token)).body.members.length,2);
 }finally{await new Promise(r=>server.close(r));}
});

test('server restart invalidates sessions and previous invite codes',async()=>{
 let server=createRelay();await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const port=server.address().port;const url='http://127.0.0.1:'+port;
 async function call(path,token,body){const r=await fetch(url+path,{method:body?'POST':'GET',headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,body:await r.json()};}
 try{
  const old=(await call('/v1/sessions',null,{name:'Old'})).body;
  const group=(await call('/v1/groups',old.token,{})).body;
  await call('/v1/sharing',old.token,{enabled:true});
  await new Promise(r=>server.close(r));
  await assert.rejects(()=>fetch(url+'/health'));
  server=createRelay();await new Promise(r=>server.listen(port,'127.0.0.1',r));
  assert.equal((await call('/v1/group',old.token)).status,401);
  const fresh=(await call('/v1/sessions',null,{name:'New'})).body;
  assert.equal((await call('/v1/group',fresh.token)).body.groupId,'');
  assert.equal((await call('/v1/groups/join',fresh.token,{code:group.inviteCode})).status,404);
 }finally{await new Promise(r=>server.close(r));}
});

test('owner controls admission, rotation, revoke, rejection and removal without leaking data',async()=>{
 let now=100000;const server=createRelay({clock:()=>now});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url='http://127.0.0.1:'+server.address().port;
 async function call(path,who,body,expected=200,method){
  const r=await fetch(url+path,{method:method||(body?'POST':'GET'),headers:{...(who?{Authorization:'Bearer '+who.token}:{}),...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
  const data=await r.json();assert.equal(r.status,expected,path+' '+JSON.stringify(data));return data;
 }
 try{
  const make=name=>call('/v1/sessions',null,{name},201);
  const a=await make('Owner'),b=await make('Member'),c=await make('Pending'),d=await make('Outsider');
  const g=await call('/v1/groups',a,{});
  const waiting=await call('/v1/groups/join',b,{code:g.inviteCode});
  assert.equal(waiting.pending,true);assert.equal(waiting.groupId,'');assert.deepEqual(waiting.members,[]);assert.deepEqual(waiting.messages,[]);assert.equal(waiting.inviteCode,'');
  await call('/v1/sharing',b,{enabled:true},409);
  await call('/v1/state',b,{available:false,sequence:1},409,'PUT');
  const duplicate=await call('/v1/groups/join',b,{code:g.inviteCode});assert.equal(duplicate.pending,true);
  assert.equal((await call('/v1/group',a)).requests.length,1);
  await call('/v1/groups/requests/approve',a,{requestId:waiting.requestId});
  const accepted=await call('/v1/group',b);assert.equal(accepted.groupId,g.groupId);assert.equal(accepted.inviteCode,'');assert.deepEqual(accepted.requests,[]);assert.equal(accepted.members.find(m=>m.id===b.userId).sharing,false);
  for(const action of ['invite/rotate','invite/revoke','requests/approve','requests/reject','kick'])await call('/v1/groups/'+action,b,{requestId:c.userId,memberId:a.userId},403);
  await call('/v1/groups/invite/rotate',d,{},409);
  let pendingC=await call('/v1/groups/join',c,{code:g.inviteCode});
  const rotated=await call('/v1/groups/invite/rotate',a,{});assert.notEqual(rotated.inviteCode,g.inviteCode);
  assert.equal((await call('/v1/group',c)).accessNotice,'INVITE_CHANGED');
  await call('/v1/groups/join',c,{code:g.inviteCode},404);
  await call('/v1/groups/requests/approve',a,{requestId:pendingC.requestId},404);
  pendingC=await call('/v1/groups/join',c,{code:rotated.inviteCode});
  await call('/v1/groups/requests/reject',a,{requestId:pendingC.requestId});
  assert.equal((await call('/v1/group',c)).accessNotice,'REQUEST_REJECTED');
  pendingC=await call('/v1/groups/join',c,{code:rotated.inviteCode});
  await call('/v1/groups/invite/revoke',a,{});
  assert.equal((await call('/v1/group',a)).inviteCode,'');
  assert.equal((await call('/v1/group',b)).groupId,g.groupId);
  assert.equal((await call('/v1/group',c)).pending,false);
  await call('/v1/groups/join',c,{code:rotated.inviteCode},404);
  const open=await call('/v1/groups/invite/rotate',a,{});
  const epoch=await call('/v1/sharing',b,{enabled:true});
  await call('/v1/state',b,{epoch:epoch.shareEpoch,sequence:1,available:true,state:{latitude:37.4,longitude:127.1,address:'synthetic address',destinationId:'fixture',destinationName:'synthetic destination',destinationAddress:'synthetic detail',remainingSeconds:600,remainingMeters:5000,sourceAgeMs:0}},200,'PUT');
  assert.notEqual((await call('/v1/group',a)).members.find(m=>m.id===b.userId).state,null);
  await call('/v1/messages',b,{id:'removed-message',kind:'WAIT'});
  await call('/v1/groups/kick',a,{memberId:a.userId},409);
  await call('/v1/groups/kick',a,{memberId:b.userId});
  const kicked=await call('/v1/group',b);assert.equal(kicked.groupId,'');assert.equal(kicked.accessNotice,'REMOVED_FROM_GROUP');assert.deepEqual(kicked.members,[]);
  const afterKick=await call('/v1/group',a);assert.equal(afterKick.messages.length,0);assert.equal(afterKick.members.some(m=>m.id===b.userId),false);
  await call('/v1/state',b,{epoch:epoch.shareEpoch,available:false,sequence:1},409,'PUT');
  await call('/v1/groups/join',b,{code:open.inviteCode},403);
  pendingC=await call('/v1/groups/join',c,{code:open.inviteCode});
  for(let i=0;i<10;i++){now+=30000;await call('/v1/group',a);await call('/v1/group',c);}
  assert.equal((await call('/v1/group',c)).accessNotice,'REQUEST_EXPIRED');
  await call('/v1/groups/requests/approve',a,{requestId:pendingC.requestId},404);
  pendingC=await call('/v1/groups/join',c,{code:open.inviteCode});
  await call('/v1/groups/requests/approve',a,{requestId:pendingC.requestId});
  await call('/v1/groups/leave',a,{});
  const closed=await call('/v1/group',c);assert.equal(closed.groupId,'');assert.equal(closed.accessNotice,'GROUP_CLOSED');
 }finally{await new Promise(r=>server.close(r));}
});

test('pending capacity, member capacity, fresh request IDs and owner timeout',async()=>{
 let now=100000;const server=createRelay({clock:()=>now});await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url='http://127.0.0.1:'+server.address().port;
 async function call(path,who,body,expected=200){const r=await fetch(url+path,{method:body?'POST':'GET',headers:{...(who?{Authorization:'Bearer '+who.token}:{}),...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});const data=await r.json();assert.equal(r.status,expected,path);return data;}
 try{
  const owner=await call('/v1/sessions',null,{name:'owner'},201),peers=[];
  const g=await call('/v1/groups',owner,{});
  for(let i=0;i<11;i++)peers.push(await call('/v1/sessions',null,{name:'P'+i},201));
  const requests=[];
  for(let i=0;i<10;i++)requests.push(await call('/v1/groups/join',peers[i],{code:g.inviteCode}));
  await call('/v1/groups/join',peers[10],{code:g.inviteCode},429);
  await call('/v1/groups/requests/reject',owner,{requestId:requests[0].requestId});
  const repeat=await call('/v1/groups/join',peers[0],{code:g.inviteCode});
  assert.notEqual(repeat.requestId,requests[0].requestId);
  await call('/v1/groups/requests/approve',owner,{requestId:requests[0].requestId},404);
  const otherOwner=await call('/v1/sessions',null,{name:'otherOwner'},201);
  await call('/v1/groups',otherOwner,{});
  await call('/v1/groups/requests/approve',otherOwner,{requestId:repeat.requestId},404);
  for(let i=1;i<=7;i++)await call('/v1/groups/requests/approve',owner,{requestId:requests[i].requestId});
  await call('/v1/groups/requests/approve',owner,{requestId:requests[8].requestId},409);
  now+=30000;await call('/v1/group',peers[1]);now+=31000;
  const closed=await call('/v1/group',peers[1]);assert.equal(closed.groupId,'');assert.equal(closed.accessNotice,'GROUP_CLOSED');
  assert.equal((await call('/v1/group',peers[8])).pending,false);
 }finally{await new Promise(r=>server.close(r));}
});
