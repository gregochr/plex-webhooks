import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginDialog from '../components/LoginDialog';
import { getAuthState, getAuthorization } from './auth';
import { ApiError, apiGet, apiWrite } from './http';

const unauthorized = () => new Response('', { status: 401 });
const ok = (body = {}) => Response.json(body);
const basic = (user, pass) => `Basic ${btoa(`${user}:${pass}`)}`;

let fetchMock;

beforeEach(() => {
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('apiGet', () => {
  it('returns the parsed body, and null for an empty one', async () => {
    fetchMock
      .mockResolvedValueOnce(ok({ a: 1 }))
      .mockResolvedValueOnce(new Response('', { status: 200 }));
    expect(await apiGet('/api/x')).toEqual({ a: 1 });
    expect(await apiGet('/api/x')).toBeNull();
  });

  it('throws an ApiError carrying the status', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 503 }));
    await expect(apiGet('/api/x')).rejects.toMatchObject({ name: 'ApiError', status: 503 });
  });
});

describe('apiWrite', () => {
  it('always sends X-Requested-With, JSON, and the method', async () => {
    fetchMock.mockResolvedValueOnce(ok({ done: true }));
    const result = await apiWrite('/api/config/hue-enabled', { body: { enabled: true } });
    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe('/api/config/hue-enabled');
    expect(init.method).toBe('PUT');
    expect(init.headers['X-Requested-With']).toBe('XMLHttpRequest');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.headers.Authorization).toBeUndefined();
    expect(init.body).toBe('{"enabled":true}');
    expect(result).toEqual({ done: true });
  });

  it('sends a POST without a body or content type', async () => {
    fetchMock.mockResolvedValueOnce(ok());
    await apiWrite('/api/debug/play', { method: 'POST' });
    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
    expect(init.headers['Content-Type']).toBeUndefined();
    expect(init.headers['X-Requested-With']).toBe('XMLHttpRequest');
  });

  it('rejects with an ApiError on a non-401 failure and never opens the dialog', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 400 }));
    await expect(apiWrite('/api/config/hue-scene', { body: {} })).rejects.toBeInstanceOf(ApiError);
    expect(getAuthState().open).toBe(false);
  });
});

describe('the login dialog', () => {
  it('opens on the first 401, retries with the entered credentials, then closes', async () => {
    const user = userEvent.setup();
    render(<LoginDialog />);
    fetchMock.mockResolvedValueOnce(unauthorized()).mockResolvedValueOnce(ok({ saved: 1 }));

    const pending = apiWrite('/api/config/hue-scene', { body: { key: 'k' } });
    const dialog = await screen.findByRole('dialog', { name: /sign in/i });
    expect(dialog).toHaveAttribute('open');

    await user.type(screen.getByLabelText('Password'), 'secret');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await pending).toEqual({ saved: 1 });
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe(basic('admin', 'secret'));
    await waitFor(() => expect(dialog).not.toHaveAttribute('open'));
  });

  it('says the password was wrong, stays open, and succeeds on the next try', async () => {
    const user = userEvent.setup();
    render(<LoginDialog />);
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok({ saved: 2 }));

    const pending = apiWrite('/api/config/hue-scene', { body: {} });
    await screen.findByRole('dialog');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(/not accepted/i);
    expect(screen.getByRole('dialog')).toHaveAttribute('open');
    expect(getAuthorization()).toBeNull();
    expect(screen.getByLabelText('Password')).toHaveValue('');

    await user.type(screen.getByLabelText('Password'), 'right');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await pending).toEqual({ saved: 2 });
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe(basic('admin', 'right'));
    await waitFor(() =>
      expect(screen.getByRole('dialog', { hidden: true })).not.toHaveAttribute('open')
    );
  });

  it('rejects the pending write only when the dialog is cancelled', async () => {
    const user = userEvent.setup();
    render(<LoginDialog />);
    fetchMock.mockResolvedValueOnce(unauthorized());

    const pending = apiWrite('/api/config/hue-scene', { body: {} });
    const settled = vi.fn();
    pending.catch(settled);
    await screen.findByRole('dialog');
    expect(settled).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    await expect(pending).rejects.toThrow(/cancelled/i);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(getAuthState().open).toBe(false));
  });

  it('treats Escape as cancel', async () => {
    render(<LoginDialog />);
    fetchMock.mockResolvedValueOnce(unauthorized());
    const pending = apiWrite('/api/config/hue-scene', { body: {} });
    const dialog = await screen.findByRole('dialog');

    dialog.dispatchEvent(new Event('cancel', { cancelable: true }));

    await expect(pending).rejects.toThrow(/cancelled/i);
  });

  it('shares one dialog between concurrent writes and retries both', async () => {
    const user = userEvent.setup();
    render(<LoginDialog />);
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok({ n: 1 }))
      .mockResolvedValueOnce(ok({ n: 2 }));

    const first = apiWrite('/api/config/hue-scene', { body: { a: 1 } });
    const second = apiWrite('/api/config/plex-commands/movie', { body: { b: 2 } });
    await screen.findByRole('dialog');
    await user.type(screen.getByLabelText('Password'), 'pw');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await first).toEqual({ n: 1 });
    expect(await second).toEqual({ n: 2 });
    expect(screen.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
  });

  it('reuses the credentials for later writes without asking again', async () => {
    const user = userEvent.setup();
    render(<LoginDialog />);
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(ok());

    const first = apiWrite('/api/config/hue-scene', { body: {} });
    await screen.findByRole('dialog');
    await user.type(screen.getByLabelText('Password'), 'pw');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await first;

    await apiWrite('/api/config/hue-scene', { body: {} });
    expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe(basic('admin', 'pw'));
    expect(getAuthState().open).toBe(false);
  });

  it('asks again, with the error showing, when remembered credentials are refused later', async () => {
    const user = userEvent.setup();
    render(<LoginDialog />);
    fetchMock
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok())
      .mockResolvedValueOnce(unauthorized())
      .mockResolvedValueOnce(ok());

    const first = apiWrite('/api/config/hue-scene', { body: {} });
    await screen.findByRole('dialog');
    await user.type(screen.getByLabelText('Password'), 'old');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await first;

    const second = apiWrite('/api/config/hue-scene', { body: {} });
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Password'), 'new');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await second;
    expect(getAuthorization()).toBe(basic('admin', 'new'));
  });

  it('never writes credentials to localStorage or sessionStorage', async () => {
    const storage = () => ({
      setItem: vi.fn(),
      getItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
    });
    const local = storage();
    const session = storage();
    vi.stubGlobal('localStorage', local);
    vi.stubGlobal('sessionStorage', session);
    const user = userEvent.setup();
    render(<LoginDialog />);
    fetchMock.mockResolvedValueOnce(unauthorized()).mockResolvedValueOnce(ok());

    const pending = apiWrite('/api/config/hue-scene', { body: {} });
    await screen.findByRole('dialog');
    await user.clear(screen.getByLabelText('Username'));
    await user.type(screen.getByLabelText('Username'), 'ådmin');
    await user.type(screen.getByLabelText('Password'), 'hunter2');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await pending;

    expect(local.setItem).not.toHaveBeenCalled();
    expect(session.setItem).not.toHaveBeenCalled();
  });
});
