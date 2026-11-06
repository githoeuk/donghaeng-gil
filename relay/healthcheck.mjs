const endpoint=process.env.RELAY_HEALTH_URL || `http://127.0.0.1:${process.env.PORT || 8787}/health`;
try {
  const response=await fetch(endpoint,{signal:AbortSignal.timeout(2000),redirect:'error'});
  const body=await response.json();
  if(!response.ok || body.ok!==true || body.apiVersion!==2)throw Error('UNHEALTHY');
} catch {
  console.error('Relay health check failed (no credentials or response body logged).');
  process.exitCode=1;
}
