import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

// CSS is not applied in jsdom, so the one layout rule that keeps a phone from scrolling sideways is checked in
// the source: .sr-only is absolutely positioned, and unless its scroll container is positioned too, the label
// escapes the container's overflow clipping and widens the page (scrollWidth 385 at 375px).
const css = readFileSync(resolve(process.cwd(), 'src/styles/screens.css'), 'utf8');
const rule = (selector) =>
  css.match(new RegExp(`^${selector.replace('.', '\\.')} \\{([^}]*)\\}`, 'm'))?.[1] ?? '';

describe('screens.css', () => {
  it('positions every overflow container that holds .sr-only labels', () => {
    expect(rule('.sr-only')).toMatch(/position:\s*absolute/);
    expect(rule('.table-scroll')).toMatch(/position:\s*relative/);
    expect(rule('.table-scroll')).toMatch(/overflow-x:\s*auto/);
  });
});
