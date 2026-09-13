// Run against Chrome's loopback CDP port 19222 and bootTestRun on port 18080.
// No third-party dependency; uses Node's built-in fetch and WebSocket.
import { mkdir, writeFile } from 'node:fs/promises';

const output = process.argv[2] || 'build/reports/backend-openapi-review/browser';
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
// Fixed reviewed inventory; intentionally independent of the running document.
const expectedOperations = [
  {
    "method": "GET",
    "path": "/api/v1/auth/callback/{registrationId}",
    "label": "OIDC callback handled by Spring Security"
  },
  {
    "method": "POST",
    "path": "/api/v1/auth/logout",
    "label": "Invalidate the authenticated browser session"
  },
  {
    "method": "PUT",
    "path": "/api/v1/links/order",
    "label": "Reorder all owned Links"
  },
  {
    "method": "GET",
    "path": "/api/v1/dashboards/home",
    "label": "Read Home Dashboard"
  },
  {
    "method": "PUT",
    "path": "/api/v1/dashboards/home",
    "label": "Save Home Dashboard"
  },
  {
    "method": "GET",
    "path": "/api/v1/tasks",
    "label": "List owned Tasks"
  },
  {
    "method": "POST",
    "path": "/api/v1/tasks",
    "label": "Create an owned Task"
  },
  {
    "method": "POST",
    "path": "/api/v1/tasks/{id}/restore",
    "label": "Restore an owned Task"
  },
  {
    "method": "GET",
    "path": "/api/v1/projects",
    "label": "List owned Projects"
  },
  {
    "method": "POST",
    "path": "/api/v1/projects",
    "label": "Create an owned Project"
  },
  {
    "method": "GET",
    "path": "/api/v1/milestones",
    "label": "List owned Milestones"
  },
  {
    "method": "POST",
    "path": "/api/v1/milestones",
    "label": "Create an owned Milestone"
  },
  {
    "method": "GET",
    "path": "/api/v1/links",
    "label": "List owned Links"
  },
  {
    "method": "POST",
    "path": "/api/v1/links",
    "label": "Create an owned Link"
  },
  {
    "method": "GET",
    "path": "/api/v1/journals",
    "label": "List owned Journals"
  },
  {
    "method": "POST",
    "path": "/api/v1/journals",
    "label": "Create an owned Journal"
  },
  {
    "method": "GET",
    "path": "/api/v1/tasks/{id}",
    "label": "Read an owned Task including trash"
  },
  {
    "method": "DELETE",
    "path": "/api/v1/tasks/{id}",
    "label": "Soft-delete an owned Task"
  },
  {
    "method": "PATCH",
    "path": "/api/v1/tasks/{id}",
    "label": "Update or complete/reopen a Task"
  },
  {
    "method": "GET",
    "path": "/api/v1/projects/{id}",
    "label": "Get an owned Project, including archived Projects"
  },
  {
    "method": "PATCH",
    "path": "/api/v1/projects/{id}",
    "label": "Update, archive or unarchive an owned Project"
  },
  {
    "method": "GET",
    "path": "/api/v1/milestones/{id}",
    "label": "Read an owned Milestone"
  },
  {
    "method": "DELETE",
    "path": "/api/v1/milestones/{id}",
    "label": "Permanently delete an owned Milestone"
  },
  {
    "method": "PATCH",
    "path": "/api/v1/milestones/{id}",
    "label": "Update a Milestone"
  },
  {
    "method": "GET",
    "path": "/api/v1/links/{id}",
    "label": "Read an owned Link"
  },
  {
    "method": "DELETE",
    "path": "/api/v1/links/{id}",
    "label": "Permanently delete an owned Link"
  },
  {
    "method": "PATCH",
    "path": "/api/v1/links/{id}",
    "label": "Update an owned Link"
  },
  {
    "method": "GET",
    "path": "/api/v1/journals/{id}",
    "label": "Read an owned Journal"
  },
  {
    "method": "DELETE",
    "path": "/api/v1/journals/{id}",
    "label": "Permanently delete an owned Journal"
  },
  {
    "method": "PATCH",
    "path": "/api/v1/journals/{id}",
    "label": "Update a Journal"
  },
  {
    "method": "GET",
    "path": "/api/v1/tasks/stats",
    "label": "Count undeleted Tasks by status"
  },
  {
    "method": "GET",
    "path": "/api/v1/overview",
    "label": "Read workspace Overview"
  },
  {
    "method": "GET",
    "path": "/api/v1/me",
    "label": "Get the authenticated user and personal workspace"
  },
  {
    "method": "GET",
    "path": "/api/v1/auth/login",
    "label": "Start the configured Google OIDC login"
  },
  {
    "method": "GET",
    "path": "/api/v1/auth/csrf",
    "label": "Get the CSRF token for the authenticated browser session"
  }
];
const expectedSchemas = ["ReorderLinksRequest", "ApiError", "LinkListResponse", "LinkResponse", "DashboardWidgetRequest", "SaveHomeDashboardRequest", "DashboardWidgetResponse", "HomeDashboardResponse", "CreateTaskRequest", "TaskResponse", "RestoreTaskRequest", "CreateProjectRequest", "ProjectResponse", "CreateMilestoneRequest", "MilestoneResponse", "CreateLinkRequest", "LinkMutationResponse", "CreateJournalRequest", "JournalResponse", "UpdateTaskRequest", "UpdateProjectRequest", "UpdateMilestoneRequest", "UpdateLinkRequest", "UpdateJournalRequest", "TaskListResponse", "TaskStatusCounts", "TaskStatsResponse", "ProjectListResponse", "OverviewProjectCounts", "OverviewProjectScopeCounts", "OverviewProjectsByScope", "OverviewResponse", "OverviewTaskCounts", "MilestoneListResponse", "MeResponse", "WorkspaceResponse", "JournalListResponse", "CsrfTokenResponse", "DeleteMilestoneResponse", "DeleteLinkResponse", "DeleteJournalResponse", "DashboardDataWidget", "DashboardUtilityWidget"];
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
          return label.split(' ')[0] === operation.method && label.split(' ')[1] === operation.path && label.includes(operation.label);
        })),
        schemas: schemas.filter(name => buttons().some(element => text(element) === name)),
      };
    })()`);
    if (rendered.operations.length === expectedOperations.length && rendered.schemas.length === expectedSchemas.length) break;
    await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (rendered.operations.length !== expectedOperations.length || rendered.schemas.length !== expectedSchemas.length)
    throw new Error('Missing visible backend operations or schema names: ' + JSON.stringify(rendered));
  const contract = await evaluate(`(async () => {
    const response = await fetch('/v3/api-docs', {credentials:'omit'});
    if (response.status !== 200) throw new Error('Anonymous OpenAPI access failed');
    const apiDocument = await response.json();
    if (apiDocument.components.schemas.HomeDashboardResponse.properties.revision.minimum !== 0)
      throw new Error('Missing virtual revision 0');
    if (apiDocument.components.schemas.DashboardWidgetRequest.oneOf.length !== 2)
      throw new Error('Missing widget type alternatives');
    for (const name of ${JSON.stringify(expectedSchemas)}) {
      if (!apiDocument.components.schemas[name]) throw new Error('Missing schema: ' + name);
    }
    for (const [path, methods] of Object.entries(apiDocument.paths)) {
      if (path === '/api/v1/auth/logout') continue;
      for (const [method, operation] of Object.entries(methods)) {
        if (!['post','patch','delete','put'].includes(method)) continue;
        if (!operation.parameters.some(p => p.name === 'X-CSRF-Token' && p.required && p.in === 'header'))
          throw new Error('Missing required CSRF documentation: ' + path);
      }
    }
    return {anonymous:true, requiredCsrfDocumented:true};
  })()`);
  const expanded = await evaluate(`(() => {
    ${renderedHelpers}
    let count = 0;
    for (const operation of operations) {
      const button = buttons().find(element => {
        const label = text(element).split(' ');
        return label[0] === operation.method && label[1] === operation.path;
      });
      if (!button) throw new Error('Missing operation button');
      if (button.getAttribute('aria-expanded') !== 'true') button.click();
      count++;
    }
    return count;
  })()`);
  await new Promise(resolve => setTimeout(resolve, 1000));
  const detail = await evaluate(`(() => {
    ${renderedHelpers}
    const expandedOperations = operations.filter(operation => buttons().some(element => {
      const label = text(element).split(' ');
      return label[0] === operation.method && label[1] === operation.path && element.getAttribute('aria-expanded') === 'true';
    })).length;
    const textContent = document.body.innerText;
    if (/Failed to load API definition|Resolver error|Errors Hide/i.test(textContent)) throw new Error('Swagger render error');
    return {expandedOperations, responseHeadings:[...document.querySelectorAll('h4')].filter(e=>text(e)==='Responses').length};
  })()`);
  if (expanded !== 35 || detail.expandedOperations !== 35 || detail.responseHeadings !== 35)
    throw new Error('Not all operation details rendered: ' + JSON.stringify(detail));
  const evidence = {result:'BACKEND_OPENAPI_SWAGGER_PASS', ...contract, ...rendered, ...detail};
  await writeFile(`${output}/result.json`, JSON.stringify(evidence, null, 2));
  await writeFile(`${output}/swagger.html`, await evaluate('document.documentElement.outerHTML'));
  const screenshot = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  await writeFile(`${output}/swagger.png`, Buffer.from(screenshot.data, 'base64'));
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
