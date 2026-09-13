// Run against Chrome's loopback CDP port 19222 and bootTestRun on port 18080.
// No third-party dependency; uses Node's built-in fetch and WebSocket.
import { mkdir, writeFile } from 'node:fs/promises';

const output = process.argv[2] || 'build/reports/link-api/browser';
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
// Use visible button text and semantic roles, not Swagger's internal CSS or JS state.
const expectedOperations = [
  { method: 'GET', path: '/api/v1/links', label: 'List owned Links' },
  { method: 'POST', path: '/api/v1/links', label: 'Create an owned Link' },
  { method: 'GET', path: '/api/v1/links/{id}', label: 'Read an owned Link' },
  { method: 'PATCH', path: '/api/v1/links/{id}', label: 'Update an owned Link' },
  { method: 'DELETE', path: '/api/v1/links/{id}', label: 'Permanently delete an owned Link' },
  { method: 'PUT', path: '/api/v1/links/order', label: 'Reorder all owned Links' },
];
const expectedSchemas = ['LinkResponse', 'CreateLinkRequest', 'UpdateLinkRequest', 'LinkListResponse', 'DeleteLinkResponse', 'LinkMutationResponse', 'ReorderLinksRequest'];
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
    throw new Error('Missing visible Link operations or schema names: ' + JSON.stringify(rendered));
  const contract = await evaluate(`(async () => {
    const response = await fetch('/v3/api-docs', {credentials:'omit'});
    if (response.status !== 200) throw new Error('Anonymous OpenAPI access failed');
    const apiDocument = await response.json();
    if (apiDocument.components.schemas.CreateLinkRequest.properties.description.default !== '')
      throw new Error('Missing empty description default');
    for (const name of ${JSON.stringify(expectedSchemas)}) {
      if (!apiDocument.components.schemas[name]) throw new Error('Missing schema: ' + name);
    }
    for (const [path, methods] of Object.entries(apiDocument.paths)) {
      if (!path.startsWith('/api/v1/links')) continue;
      for (const [method, operation] of Object.entries(methods)) {
        if (!['post','patch','delete','put'].includes(method)) continue;
        if (!operation.parameters.some(p => p.name === 'X-CSRF-Token' && p.required && p.in === 'header'))
          throw new Error('Missing required CSRF documentation: ' + path);
      }
    }
    return {anonymous:true, requiredCsrfDocumented:true};
  })()`);
  const evidence = {result:'LINK_API_SWAGGER_DOCUMENTATION_PASS', ...contract, ...rendered};
  await writeFile(`${output}/result.json`, JSON.stringify(evidence, null, 2));
  await writeFile(`${output}/swagger.html`, await evaluate('document.documentElement.outerHTML'));
  const screenshot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  await writeFile(`${output}/swagger.png`, Buffer.from(screenshot.data, 'base64'));
  console.log(JSON.stringify(evidence));
} finally {
  await cdp('Page.close');
  socket.close();
}
