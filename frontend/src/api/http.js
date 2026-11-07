import { confirmLogin, getAuthorization, requestLogin } from './auth';

/** A non-2xx response. */
export class ApiError extends Error {
  constructor(status) {
    super(`Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function parseBody(response) {
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

/** GET a public JSON endpoint. */
export async function apiGet(path) {
  const response = await fetch(path, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new ApiError(response.status);
  }
  return parseBody(response);
}

function send(path, method, body) {
  const headers = {
    Accept: 'application/json',
    // Makes Spring's HTTP Basic answer 401 with no WWW-Authenticate, so the browser's own prompt never
    // appears and the dashboard's login dialog is the only way in.
    'X-Requested-With': 'XMLHttpRequest',
  };
  const init = { method, headers };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const authorization = getAuthorization();
  if (authorization) {
    headers.Authorization = authorization;
  }
  return fetch(path, init);
}

/**
 * An admin write (PUT or POST). On a 401 it opens the login dialog and retries with the entered credentials,
 * as often as the user keeps trying; it rejects only when the dialog is cancelled or the server says
 * something other than 401.
 */
export async function apiWrite(path, { method = 'PUT', body } = {}) {
  // A 401 to a request that carried credentials means they were refused; without any, it is just "sign in".
  let failed = getAuthorization() !== null;
  let response = await send(path, method, body);
  while (response.status === 401) {
    await requestLogin({ failed });
    response = await send(path, method, body);
    failed = true;
  }
  if (!response.ok) {
    throw new ApiError(response.status);
  }
  confirmLogin();
  return parseBody(response);
}
