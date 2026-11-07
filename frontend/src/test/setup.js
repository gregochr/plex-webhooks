import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';
import { resetAuth } from '../api/auth';

// jsdom has no modal <dialog> behaviour: give it the two methods the login dialog uses.
if (!HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close() {
    this.removeAttribute('open');
  };
}

afterEach(() => {
  cleanup();
  resetAuth();
});
