import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectTextBytes } from './check-text-encoding.mjs';

const encode = (text) => Buffer.from(text, 'utf8');

test('detects common mojibake signatures and reports their line', () => {
  const corrupted = [
    String.fromCodePoint(0x00c3, 0x00a9),
    String.fromCodePoint(0x00c2, 0x00b7),
    String.fromCodePoint(0x00e2, 0x20ac, 0x2122),
    String.fromCodePoint(0x00e2, 0x2020, 0x2019),
    String.fromCodePoint(0x00f0, 0x0178),
    String.fromCodePoint(0xfffd),
  ].join('\n');

  assert.deepEqual(inspectTextBytes(encode(`heading\n${corrupted}`)), [
    { line: 2, reason: 'known mojibake signature' },
    { line: 3, reason: 'known mojibake signature' },
    { line: 4, reason: 'known mojibake signature' },
    { line: 5, reason: 'known mojibake signature' },
    { line: 6, reason: 'known mojibake signature' },
    { line: 7, reason: 'known mojibake signature' },
  ]);
});

test('allows correctly encoded punctuation and multilingual text', () => {
  assert.deepEqual(
    inspectTextBytes(encode('Klinika · patient’s notes — café, Україна, 東京')),
    [],
  );
});

test('reports invalid UTF-8 and skips binary content', () => {
  assert.deepEqual(inspectTextBytes(Buffer.from([0xc3, 0x28])), [
    { line: 1, reason: 'invalid UTF-8' },
  ]);
  assert.deepEqual(inspectTextBytes(Buffer.from([0x00, 0xc3, 0x28])), []);
});
