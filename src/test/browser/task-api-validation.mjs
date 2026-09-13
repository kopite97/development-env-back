// Run against Chrome's loopback CDP port 19222 and bootTestRun on port 18080.
// No third-party dependency; uses Node's built-in fetch and WebSocket.
import { mkdir, writeFile } from 'node:fs/promises';

const output = process.env.TASK_BROWSER_OUTPUT || 'build/reports/task-api/browser';
await mkdir(output, { recursive: true });
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
try {
  let ready = false;
  for (let attempt = 0; attempt < 120; attempt++) {
    ready = await evaluate(`Boolean(window.ui && window.ui.getSystem && document.querySelectorAll('.opblock').length >= 7)`);
    if (ready) break;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (!ready) throw new Error('Swagger UI did not render');
  const evidence = await evaluate(`(async () => {
    const system = window.ui.getSystem();
    const spec = system.specSelectors.specJson().toJS();
    const operations = [...document.querySelectorAll('.opblock-summary-path')].map(e => e.textContent.trim());
    if (operations.filter(p => p.startsWith('/api/v1/tasks')).length !== 7) throw new Error('Task operations not rendered');
    const response = await fetch('/v3/api-docs');
    if (response.status !== 200) throw new Error('Anonymous OpenAPI access failed');
    const apiDocument = await response.json();
    for (const name of ['TaskResponse','CreateTaskRequest','UpdateTaskRequest','RestoreTaskRequest','TaskListResponse','TaskStatsResponse']) {
      if (!apiDocument.components.schemas[name]) throw new Error('Missing schema: ' + name);
    }
    for (const [path, methods] of Object.entries(apiDocument.paths)) {
      if (!path.startsWith('/api/v1/tasks')) continue;
      for (const [method, operation] of Object.entries(methods)) {
        if (!['post','patch','delete'].includes(method)) continue;
        if (!operation.parameters.some(p => p.name === 'X-CSRF-Token' && p.required && p.in === 'header'))
          throw new Error('Missing required CSRF documentation: ' + path);
      }
    }
    const taskTag = [...globalThis.document.querySelectorAll('.opblock-tag')].find(e => e.textContent.includes('Tasks'));
    taskTag?.scrollIntoView();
    return {result:'TASK_API_SWAGGER_DOCUMENTATION_PASS',anonymous:true,operations,schemas:Object.keys(apiDocument.components.schemas).filter(n => n.includes('Task'))};
  })()`);
  await writeFile(`${output}/result.json`, JSON.stringify(evidence, null, 2));
  await writeFile(`${output}/swagger.html`, await evaluate('document.documentElement.outerHTML'));
  const screenshot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  await writeFile(`${output}/swagger.png`, Buffer.from(screenshot.data, 'base64'));
  console.log(JSON.stringify(evidence));
} finally {
  await cdp('Page.close');
  socket.close();
}
