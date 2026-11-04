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
  await call('POST','/v1/groups/join',b.token,{code:group.inviteCode});
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
