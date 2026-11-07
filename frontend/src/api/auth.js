// Admin credentials and the login-dialog state, in memory only.
//
// The password is never written to localStorage or sessionStorage (a test asserts it). A page reload
// forgets it, which is the point: the dashboard is public, and signing in is for the occasional write.

const initial = { open: false, error: false };

let state = initial;
let credentials = null; // the Authorization header value, or null while signed out
let waiting = null; // { promise, resolve, reject } shared by every write waiting on the dialog
const listeners = new Set();

function setState(next) {
  state = next;
  listeners.forEach((listener) => listener());
}

/** UTF-8 safe Basic header: btoa alone throws on anything outside Latin-1. */
function basicHeader(username, password) {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('');
  return `Basic ${btoa(binary)}`;
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getAuthState() {
  return state;
}

export function getAuthorization() {
  return credentials;
}

/**
 * Opens the login dialog (or keeps it open) and resolves once the user submits, rejecting if they cancel.
 * Concurrent writes share one dialog. `failed` marks that the credentials just sent were refused.
 */
export function requestLogin({ failed = false } = {}) {
  if (failed) {
    credentials = null;
  }
  if (!waiting) {
    const next = {};
    next.promise = new Promise((resolve, reject) => {
      next.resolve = resolve;
      next.reject = reject;
    });
    waiting = next;
  }
  setState({ open: true, error: failed });
  return waiting.promise;
}

/** The dialog's submit: remembers the credentials and releases the waiting writes to retry. */
export function submitLogin(username, password) {
  credentials = basicHeader(username, password);
  const current = waiting;
  waiting = null;
  current?.resolve();
}

/** The dialog's cancel: every waiting write is rejected and the dialog closes. */
export function cancelLogin() {
  const current = waiting;
  waiting = null;
  setState(initial);
  current?.reject(new Error('Sign-in cancelled'));
}

/** Called after a write succeeds: closes the dialog if it was still open for a retry. */
export function confirmLogin() {
  if (state.open) {
    setState(initial);
  }
}

/** Test hook: forget everything. */
export function resetAuth() {
  credentials = null;
  waiting = null;
  state = initial;
  listeners.clear();
}
