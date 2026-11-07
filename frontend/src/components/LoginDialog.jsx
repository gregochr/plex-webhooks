import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { cancelLogin, getAuthState, submitLogin, subscribe } from '../api/auth';

/** The admin sign-in <dialog>. Opens on the first 401 of a write; credentials stay in memory. */
export default function LoginDialog() {
  const { open, error } = useSyncExternalStore(subscribe, getAuthState);
  const dialogRef = useRef(null);
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  function handleSubmit(event) {
    event.preventDefault();
    submitLogin(username, password);
    setPassword('');
  }

  function handleCancel(event) {
    // Escape: keep the dialog under React's control so the waiting write is rejected.
    event.preventDefault();
    cancelLogin();
  }

  return (
    <dialog
      ref={dialogRef}
      className="login-dialog"
      aria-labelledby="login-title"
      onCancel={handleCancel}
    >
      <form className="dialog" onSubmit={handleSubmit}>
        <h2 id="login-title" className="dialog-title">
          Sign in to make changes
        </h2>
        <p className="dialog-body">
          Viewing is open to everyone on the network. Changing the lights or the rules needs the
          admin login.
        </p>
        {error && (
          <p role="alert" className="login-error">
            That username or password was not accepted.
          </p>
        )}
        <div className="field">
          <label htmlFor="login-username">Username</label>
          <input
            id="login-username"
            className="input"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            className="input"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        <div className="dialog-actions">
          <button type="button" className="btn btn-secondary" onClick={cancelLogin}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary">
            Sign in
          </button>
        </div>
      </form>
    </dialog>
  );
}
