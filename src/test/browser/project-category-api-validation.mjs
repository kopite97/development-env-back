// Run against Chrome's loopback CDP port 19222 and bootTestRun on port 18080.
// No third-party dependency; uses Node's built-in fetch and WebSocket.
import { mkdir, writeFile } from 'node:fs/promises';

const output = process.argv[2] || '.gradle/project-category-only-validation/browser';
await mkdir(output, { recursive: true });
// A navigation made before boot completes can remain on Chrome's network-error page.
// Wait for the public documentation endpoint before creating the browser target.
let ready = false;
for (let attempt = 0; attempt < 120; attempt++) {
  try {
    const response = await fetch('http://127.0.0.1:18080/swagger-ui/index.html', { signal: AbortSignal.timeout(1000) });
    await response.arrayBuffer();
    if (response.ok) { ready = true; break; }
  } catch { /* Server may still be starting. */ }
  await new Promise(resolve => setTimeout(resolve, 500));
}
if (!ready) throw new Error('Swagger HTTP endpoint did not become ready');
const page = await fetch('http://127.0.0.1:19222/json/new?http://127.0.0.1:18080/swagger-ui/index.html', { method: 'PUT' }).then(r => r.json());
const socket = new WebSocket(page.webSocketDebuggerUrl);
await new Promise(resolve => socket.addEventListener('open', resolve, { once: true }));
let sequence = 0;
const pending = new Map();
socket.addEventListener('message', ({ data }) => {
  const message = JSON.parse(data);
  if (!message.id) return;
  const call = pending.get(message.id);
  pending.delete(message.id);
  if (message.error) call.reject(new Error(JSON.stringify(message.error)));
  else call.resolve(message.result);
});
function cdp(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++sequence;
    pending.set(id, { resolve, reject });
    socket.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const result = await cdp('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) throw new Error(JSON.stringify(result.exceptionDetails));
  return result.result.value;
}
async function waitVisible(expression) {
  for (let attempt=0; attempt<100; attempt++) {
    if (await evaluate(expression)) return true;
    await new Promise(resolve=>setTimeout(resolve,100));
  }
  return false;
}
// Use visible button text and semantic roles, not Swagger's internal CSS or JS state.
const expectedOperations = [
  {method:'GET',path:'/api/v1/project-categories',label:'List Project Categories'},
  {method:'POST',path:'/api/v1/project-categories',label:'Create a Project Category'},
  {method:'GET',path:'/api/v1/project-categories/{id}',label:'Read a Project Category'},
  {method:'PATCH',path:'/api/v1/project-categories/{id}',label:'Rename a Project Category'},
  {method:'DELETE',path:'/api/v1/project-categories/{id}',label:'Delete a Project Category'},
  {method:'POST',path:'/api/v2/projects',label:'Create an owned Project'},
  {method:'PATCH',path:'/api/v2/projects/{id}',label:'Update, archive or unarchive an owned Project'},
  {method:'GET',path:'/api/v2/dashboards/home',label:'Read'},
  {method:'PUT',path:'/api/v2/dashboards/home',label:'Save'},
  {method:'GET',path:'/api/v2/links',label:'List owned Links'},
  {method:'GET',path:'/api/v2/overview',label:'Overview'},
];
const expectedSchemas = ['CategoryResponse','CategoryListResponse','DeleteCategoryResponse','CreateCategoryRequest','RenameCategoryRequest','CreateProjectRequest','UpdateProjectRequest','ProjectResponse','DashboardSelectionDto','DashboardSelectionCategory','DashboardSelectionUncategorized','DashboardWidgetResponse','LinkResponse'];
const renderedHelpers = `
  const visible = element => element.checkVisibility({checkOpacity:true, checkVisibilityCSS:true})
    && element.getBoundingClientRect().height > 0;
  const text = element => element.innerText.replace(/\\s+/g, ' ').trim();
  const buttons = () => [...document.querySelectorAll('button, [role="button"]')].filter(visible);
  const operations = ${JSON.stringify(expectedOperations)};
  const schemas = ${JSON.stringify(expectedSchemas)};
`;
try {
  let rendered;
  for (let attempt = 0; attempt < 120; attempt++) {
    rendered = await evaluate(`(() => {
      ${renderedHelpers}
      const control = buttons().find(element => text(element) === 'Schemas');
      if (control?.getAttribute('aria-expanded') === 'false') control.click();
      return {
        operations: operations.filter(operation => buttons().some(element => {
          const label = text(element);
          return label.startsWith(operation.method) && label.includes(operation.path) && label.includes(operation.label);
        })),
        schemas: schemas.filter(name => buttons().some(element => text(element) === name)),
      };
    })()`);
    if (rendered.operations.length === expectedOperations.length && rendered.schemas.length === expectedSchemas.length) break;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (rendered.operations.length !== expectedOperations.length || rendered.schemas.length !== expectedSchemas.length)
    throw new Error('Missing visible Category/Project operations or schema names: ' + JSON.stringify(rendered));
  const contract = await evaluate(`(async () => {
    const response = await fetch('/v3/api-docs', {credentials:'omit'});
    if (response.status !== 200) throw new Error('Anonymous OpenAPI access failed');
    const apiDocument = await response.json();
    if (!apiDocument.components.schemas.ProjectResponse.required.includes('categoryId'))
      throw new Error('Missing required nullable Category relation');
    if (apiDocument.components.schemas.LegacyProjectResponse || apiDocument.paths['/api/v1/projects']) throw new Error('Legacy shape leaked into normal API');
    if (apiDocument.components.schemas.DashboardSelectionDto.oneOf.length !== 4) throw new Error('Missing Dashboard selectors');
    if (apiDocument.components.schemas.HomeDashboardResponse.properties.schemaVersion.enum[0] !== 2) throw new Error('Wrong Dashboard version');
    for (const schema of Object.values(apiDocument.components.schemas)) for (const field of ['scope','colorToken','byScope'])
      if (schema.properties?.[field]) throw new Error('Retired classification field: '+field);
    for (const resource of ['projects','tasks','journals','milestones','links','overview']) {
      const op=apiDocument.paths['/api/v2/'+resource].get;
      if (!op.parameters.some(p=>p.name==='category')) throw new Error('Missing category filter: '+resource);
      if (!op.responses['200'].headers['X-Workspace-Data-Revision']) throw new Error('Missing observation header');
    }
    await Promise.resolve();
    for (const name of ${JSON.stringify(expectedSchemas)}) {
      if (!apiDocument.components.schemas[name]) throw new Error('Missing schema: ' + name);
    }
    for (const [path, methods] of Object.entries(apiDocument.paths)) {
      if (!path.startsWith('/api/v1/project-categories') && !path.startsWith('/api/v2/projects')) continue;
      for (const [method, operation] of Object.entries(methods)) {
        if (!['post','patch','delete','put'].includes(method)) continue;
        if (!operation.parameters.some(p => p.name === 'X-CSRF-Token' && p.required && p.in === 'header'))
          throw new Error('Missing required CSRF documentation: ' + path);
      }
    }
    return {anonymous:true, requiredCsrfDocumented:true};
  })()`);
  const evidence = {result:'CATEGORY_ONLY_SWAGGER_DOCUMENTATION_PASS', ...contract, ...rendered};
  await writeFile(`${output}/result.json`, JSON.stringify(evidence, null, 2));
  await writeFile(`${output}/swagger.html`, await evaluate('document.documentElement.outerHTML'));
  await evaluate(`(() => { const button=[...document.querySelectorAll('button')].find(b=>b.innerText.includes('Create a Project Category')); if(button){button.click();button.scrollIntoView();} })()`);
  const screenshot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  await writeFile(`${output}/swagger.png`, Buffer.from(screenshot.data, 'base64'));
  await evaluate(`(() => {
    const button=[...document.querySelectorAll('button')].find(b=>b.innerText.includes('Create an owned Project'));
    if (!button) throw new Error('Project creation control missing');
    button.click(); button.scrollIntoView();
  })()`);
  const replayVisible = await waitVisible(`document.body.innerText.includes('fingerprint v3') && document.body.innerText.includes('Legacy intents must retry their original v1 endpoint')`);
  if (!replayVisible) throw new Error('Replay compatibility description is not visibly rendered');
  const headerVisible = await waitVisible(`document.body.innerText.includes('X-Workspace-Data-Revision') && document.body.innerText.includes('omitted on creation replays')`);
  if (!headerVisible) throw new Error('Workspace observation/replay header semantics are not visibly rendered');
  const projectShot = await cdp('Page.captureScreenshot', {format:'png'});
  await writeFile(`${output}/project-replay.png`, Buffer.from(projectShot.data, 'base64'));
  await evaluate(`(() => {
    const button=[...document.querySelectorAll('button')].find(b=>b.innerText.trim()==='ProjectResponse');
    if (!button) throw new Error('Project response schema control missing');
    button.click(); button.scrollIntoView();
  })()`);
  const nullableVisible = await waitVisible(`document.body.innerText.includes('categoryId') && /string\\s*\\|\\s*null|string\\s*,\\s*null/.test(document.body.innerText)`);
  if (!nullableVisible) throw new Error('Nullable relation schema is not visibly rendered');
  const relationShot = await cdp('Page.captureScreenshot', {format:'png'});
  await writeFile(`${output}/project-relation.png`, Buffer.from(relationShot.data, 'base64'));
  await evaluate(`(() => { const b=[...document.querySelectorAll('button')].find(b=>b.innerText.trim()==='DashboardSelectionCategory'); if(!b)throw Error('Missing Category selector');b.click();b.scrollIntoView();})()`);
  const selectionVisible=await waitVisible(`document.body.innerText.includes('categoryId') && document.body.innerText.includes('kind')`);
  if(!selectionVisible)throw Error('Category selector not rendered');
  await writeFile(`${output}/dashboard-selection.png`,Buffer.from((await cdp('Page.captureScreenshot',{format:'png'})).data,'base64'));
  await writeFile(`${output}/openapi.json`,await fetch('http://127.0.0.1:18080/v3/api-docs').then(r=>r.text()));
  evidence.selectionVisible=selectionVisible;
  evidence.workspaceHeaderVisible=headerVisible;
  evidence.replayDescriptionVisible = replayVisible;
  evidence.nullableRelationVisible = nullableVisible;
  await writeFile(`${output}/result.json`, JSON.stringify(evidence, null, 2));
  await writeFile(`${output}/swagger.html`, await evaluate('document.documentElement.outerHTML'));
  console.log(JSON.stringify(evidence));
} catch (error) {
  await writeFile(`${output}/blocker.json`, JSON.stringify({error:String(error)}, null, 2));
  await writeFile(`${output}/blocker.html`, await evaluate('document.documentElement.outerHTML'));
  const shot = await cdp('Page.captureScreenshot', {format:'png', captureBeyondViewport:true});
  await writeFile(`${output}/blocker.png`, Buffer.from(shot.data, 'base64'));
  throw error;
} finally {
  await cdp('Page.close');
  socket.close();
}
