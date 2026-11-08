import { describe, expect, it } from 'vitest';
import { draftsValid, intervalChanges, msToSeconds, parseSeconds } from './intervals';

const items = [
  { key: 'a', value: '30000' },
  { key: 'b', value: '60000' },
];

describe('msToSeconds', () => {
  it('converts milliseconds to whole seconds', () => {
    expect(msToSeconds('30000')).toBe('30');
    expect(msToSeconds(1500)).toBe('2');
  });

  it.each([null, undefined, '', 'abc'])('gives an empty string for %j', (value) => {
    expect(msToSeconds(value)).toBe('');
  });
});

describe('parseSeconds', () => {
  it('accepts whole seconds from 1 to 86400, with the boundaries', () => {
    expect(parseSeconds('1')).toBe(1);
    expect(parseSeconds(' 86400 ')).toBe(86400);
    expect(parseSeconds('0')).toBeNull();
    expect(parseSeconds('86401')).toBeNull();
  });

  it.each(['', 'ten', '1.5', '-5', '1e3', '1234567'])('rejects %j', (text) => {
    expect(parseSeconds(text)).toBeNull();
  });
});

describe('intervalChanges and draftsValid', () => {
  it('has no changes for no drafts or an unchanged draft', () => {
    expect(intervalChanges(items, {})).toEqual({});
    expect(intervalChanges(items, { a: '30' })).toEqual({});
  });

  it('sends only the changed items, in milliseconds', () => {
    expect(intervalChanges(items, { a: '45', b: '60' })).toEqual({ a: '45000' });
  });

  it('leaves an invalid draft out of the changes but flags the drafts as invalid', () => {
    expect(intervalChanges(items, { a: '0' })).toEqual({});
    expect(draftsValid({ a: '0' })).toBe(false);
    expect(draftsValid({ a: '5', b: '9' })).toBe(true);
    expect(draftsValid({})).toBe(true);
  });
});
