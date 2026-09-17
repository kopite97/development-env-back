// Isolated bootTestRun :18080 and a dedicated headless Chrome CDP :19223; no dependencies.
import {mkdir, writeFile} from 'node:fs/promises';
const output='build/reports/plan0013/browser';await mkdir(output,{recursive:true});
const base='http://127.0.0.1:18080';
const page=await fetch('http://127.0.0.1:19223/json/new?'+base+'/browser-test/login',{method:'PUT'}).then(r=>r.json());
const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise(r=>ws.addEventListener('open',r,{once:true}));
let seq=0;const pending=new Map();
ws.addEventListener('message',({data})=>{const m=JSON.parse(data);if(!m.id)return;const p=pending.get(m.id);pending.delete(m.id);m.error?p.reject(Error(JSON.stringify(m.error))):p.resolve(m.result);});
const cdp=(method,params={})=>new Promise((resolve,reject)=>{const id=++seq;pending.set(id,{resolve,reject});ws.send(JSON.stringify({id,method,params}));});
async function evaluate(expression){const r=await cdp('Runtime.evaluate',{expression,awaitPromise:true,returnByValue:true});if(r.exceptionDetails)throw Error(JSON.stringify(r.exceptionDetails));return r.result.value;}
const expected=[['GET','/api/v1/widget-types'],['GET','/api/v1/widgets'],['POST','/api/v1/widgets'],['GET','/api/v1/widgets/{id}'],['PUT','/api/v1/widgets/{id}'],['DELETE','/api/v1/widgets/{id}'],['GET','/api/v1/widgets/{id}/data'],['GET','/api/v3/dashboards/home'],['PUT','/api/v3/dashboards/home'],['POST','/api/v3/dashboards/home/initializations']];
try {
  let visible=[];
  for(let i=0;i<100;i++){
    visible=await evaluate(`(()=>{const expected=${JSON.stringify(expected)};return expected.filter(([method,path])=>[...document.querySelectorAll('button,[role="button"]')].some(e=>{const text=e.innerText.trim().replace(/\\s+/g,' ').split(' ');return text[0]===method&&text[1]===path;}));})()`);
    if(visible.length===expected.length)break;await new Promise(r=>setTimeout(r,250));
  }
  if(visible.length!==10)throw Error('Missing rendered operations: '+JSON.stringify(visible));
  await evaluate(`(()=>{for(const e of document.querySelectorAll('button,[role="button"]')){const words=e.innerText.trim().replace(/\\s+/g,' ').split(' ');if(words[0]==='GET'&&words[1]==='/api/v3/dashboards/home'){e.click();break;}}})()`);
  await new Promise(r=>setTimeout(r,300));
  await evaluate(`(()=>{const button=[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='Try it out');if(!button)throw Error('No Try it out control');button.click();})()`);
  await new Promise(r=>setTimeout(r,200));
  await evaluate(`(()=>{const button=[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='Execute');if(!button)throw Error('No Execute control');button.click();})()`);
  let executed=false;for(let i=0;i<80;i++){executed=await evaluate(`document.body.innerText.includes('"initialized"')&&document.body.innerText.includes('"layoutRevision"')&&document.body.innerText.includes('Server response')`);if(executed)break;await new Promise(r=>setTimeout(r,100));}
  if(!executed)throw Error('Swagger execution did not render layout response');
  const runtime=await evaluate(`(async()=>{
    const api=await fetch('/v3/api-docs').then(r=>r.json());
    for(const field of ['data','page','problem']){const s=api.components.schemas.WidgetDataEnvelope.properties[field];if(s.$ref||s.type||s.anyOf?.length!==2||s.anyOf[1].type!=='null')throw Error('Invalid nullable object schema: '+field);}
    if(api.components.schemas.WidgetPayload.discriminator.mapping.board!=='#/components/schemas/BoardWidgetData')throw Error('Missing payload discriminator mapping');
    const csrf=await fetch('/api/v1/auth/csrf').then(r=>r.json());const results=[];
    async function request(method,path,body,expected,key){const headers={'Content-Type':'application/json','X-CSRF-Token':csrf.csrfToken};if(key)headers['Idempotency-Key']=key;const r=await fetch(path,{method,headers,...(body===undefined?{}:{body:JSON.stringify(body)})});const text=await r.text();if(r.status!==expected)throw Error(method+' '+path+': '+r.status+' '+text);if(r.headers.get('Cache-Control')!=='no-store')throw Error('Missing no-store');results.push({method,path,status:r.status,revision:r.headers.get('X-Workspace-Data-Revision')});return {body:JSON.parse(text),text,location:r.headers.get('Location')};}
    const W='/api/v1/widgets',D='/api/v3/dashboards/home';
    await request('GET','/api/v1/widget-types',undefined,200);
    const layout=await request('GET',D,undefined,200);const key='browser-'+crypto.randomUUID();const body={type:'board',title:'Browser',configVersion:1,config:{selection:{kind:'all'}}};
    const created=await request('POST',W,body,201,key);const id=created.body.id;
    const saved=await request('PUT',D,{schemaVersion:3,layoutRevision:layout.body.layoutRevision,placements:[{widgetId:id,size:'wide'}]},200);
    const data=await request('GET',W+'/'+id+'/data',undefined,200);if(data.body.availability!=='empty'||data.body.data.columns.length!==3)throw Error('Wrong typed empty payload');
    await request('DELETE',W+'/'+id+'?revision=1',undefined,409);
    await request('PUT',D,{schemaVersion:3,layoutRevision:saved.body.layoutRevision,placements:[]},200);
    await request('DELETE',W+'/'+id+'?revision=1',undefined,200);
    const replay=await request('POST',W,body,201,key);if(replay.text!==created.text||replay.location!==created.location||results.at(-1).revision!==null)throw Error('Historical replay mismatch');
    await request('GET',W+'/'+id,undefined,404);await request('GET','/api/v2/dashboards/home',undefined,410);
    return results;
  })()`);
  const result={result:'WIDGET_SWAGGER_AND_BROWSER_PASS',renderedOperations:visible,swaggerExecute:true,nullableObjectSchemas:true,runtime};
  await writeFile(output+'/result.json',JSON.stringify(result,null,2));await writeFile(output+'/swagger.html',await evaluate('document.documentElement.outerHTML'));
  const shot=await cdp('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});await writeFile(output+'/swagger.png',Buffer.from(shot.data,'base64'));console.log(JSON.stringify(result));
} catch(error) {
  await writeFile(output+'/failure.json',JSON.stringify({error:String(error),page:await evaluate('({url:location.href,text:document.body.innerText,buttons:[...document.querySelectorAll("button,[role=button]")].map(e=>({text:e.innerText,label:e.getAttribute("aria-label")}))})')},null,2));
  throw error;
} finally {await cdp('Page.close');ws.close();}
