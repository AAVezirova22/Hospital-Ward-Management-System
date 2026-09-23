import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const decoder = new TextDecoder('utf-8', { fatal: true });

const mojibakePatterns = [
  /\u00c3[\u0080-\u00bf\u0192]/u,
  /\u00c2[\u0080-\u00bf\u00a0\u00b7]/u,
  /\u00e2[\u0080-\u00bf\u20ac\u2020]/u,
  /\u00f0\u0178/u,
  /\u00ef\u00bf\u00bd/u,
  /\ufffd/u,
];

export function inspectTextBytes(bytes) {
  if (bytes.includes(0)) return [];

  let text;
  try {
    text = decoder.decode(bytes);
  } catch {
    return [{ line: 1, reason: 'invalid UTF-8' }];
  }

  const findings = [];
  for (const [index, line] of text.split(/\r?\n/u).entries()) {
    if (mojibakePatterns.some((pattern) => pattern.test(line))) {
      findings.push({ line: index + 1, reason: 'known mojibake signature' });
    }
  }
  return findings;
}

function checkTrackedFiles() {
  const tracked = spawnSync('git', ['ls-files', '-z'], { encoding: 'buffer' });
  if (tracked.error) throw tracked.error;
  if (tracked.status !== 0) {
    throw new Error(`git ls-files failed with exit code ${tracked.status}`);
  }

  const paths = tracked.stdout.toString('utf8').split('\0').filter(Boolean);
  const failures = [];
  for (const path of paths) {
    let bytes;
    try {
      bytes = readFileSync(path);
    } catch (error) {
      failures.push(`${path}: could not read tracked file (${error.message})`);
      continue;
    }

    for (const finding of inspectTextBytes(bytes)) {
      failures.push(`${path}:${finding.line}: ${finding.reason}`);
    }
  }

  if (failures.length > 0) {
    process.stderr.write(`Text encoding check failed:\n${failures.join('\n')}\n`);
    process.exitCode = 1;
    return;
  }

  process.stdout.write(`Checked ${paths.length} tracked files: valid UTF-8, no known mojibake signatures.\n`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  checkTrackedFiles();
}
