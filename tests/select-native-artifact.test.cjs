const { test } = require('node:test');
const assert = require('node:assert/strict');
const select = require('../scripts/select-native-artifact.cjs');
const { isNativeInput } = require('../scripts/native-inputs.cjs');

test('mixed changes route to the full build, app-only changes do not', () => {
  assert.equal(['android/app/src/main/AndroidManifest.xml', 'patches/fix.patch'].some(isNativeInput), true);
  assert.equal(['android/app/build.gradle.kts', 'scripts/build-apk.sh'].some(isNativeInput), false);
  assert.equal(isNativeInput('tests/android-file-lock.rs'), true);
  assert.equal(isNativeInput('tests/test_patches.py'), true);
  assert.equal(isNativeInput('upstream.json'), true);
});

function fixture({ runs, changed = [], artifacts, comparisons = {} } = {}) {
  const outputs = {};
  const run = { id: 42, workflow_id: 7, head_branch: 'main', head_sha: 'old',
    head_repository: { full_name: 'owner/repo' }, event: 'push', status: 'completed',
    conclusion: 'failure', html_url: 'https://github.com/owner/repo/actions/runs/42' };
  const listRuns = Symbol('runs'), listArtifacts = Symbol('artifacts');
  const summary = { addHeading() { return this; }, addLink() { return this; },
    addRaw() { return this; }, async write() {} };
  return {
    outputs,
    github: {
      rest: {
        actions: { getWorkflow: async () => ({ data: { id: 7 } }),
          getWorkflowRun: async ({ run_id }) => ({ data: (runs ?? [run]).find(r => r.id === run_id) }),
          listWorkflowRuns: listRuns, listWorkflowRunArtifacts: listArtifacts },
        repos: { compareCommits: async ({ base }) => ({ data: comparisons[base] ?? { status: 'ahead', files: changed } }) },
      },
      paginate: async method => method === listRuns ? (runs ?? [run])
        : (artifacts ?? [{ name: 'mina-android-arm64', expired: false }]),
    },
    context: { repo: { owner: 'owner', repo: 'repo' }, sha: 'new' },
    core: { setOutput: (key, value) => { outputs[key] = value; }, summary },
  };
}

test('reuses a native artifact even when the later app build failed', async () => {
  const f = fixture({ changed: [{ filename: 'android/app/build.gradle.kts' }] });
  await select(f);
  assert.equal(f.outputs.run_id, '42');
  assert.equal(f.outputs.source_sha, 'old');
});
test('rejects artifacts when native inputs changed', async () => {
  const f = fixture({ changed: [{ filename: 'patches/0002-android-frontier-file-lock.patch' }] });
  await assert.rejects(select(f), /Native inputs changed/);
  assert.deepEqual(f.outputs, {});
});
test('rejects renamed native inputs', async () => {
  await assert.rejects(select(fixture({ changed: [{ filename: 'archive/old.patch', previous_filename: 'patches/old.patch' }] })), /Native inputs changed/);
});
test('reports expired or missing artifacts without rebuilding Rust', async () => {
  await assert.rejects(select(fixture({ artifacts: [{ name: 'mina-android-arm64', expired: true }] })), /No unexpired/);
});
test('does not select a pull request artifact', async () => {
  await assert.rejects(select(fixture({ runs: [{ workflow_id: 7, head_branch: 'main',
    head_repository: { full_name: 'owner/repo' }, event: 'pull_request', status: 'completed' }] })), /No unexpired/);
});

function nativeRun(id, sha) {
  return { id, workflow_id: 7, head_branch: 'main', head_sha: sha,
    head_repository: { full_name: 'owner/repo' }, event: 'push', status: 'completed',
    html_url: `https://github.com/owner/repo/actions/runs/${id}` };
}

test('skips an incompatible newer artifact and reuses the older matching native source', async () => {
  const f = fixture({ runs: [nativeRun(43, 'recent'), nativeRun(42, 'matching')],
    comparisons: {
      recent: { status: 'ahead', files: [{ filename: 'upstream.json' }] },
      matching: { status: 'ahead', files: [{ filename: 'android/app/src/main/AboutPage.kt' }] },
    } });
  await select(f);
  assert.equal(f.outputs.run_id, '42');
  assert.equal(f.outputs.source_sha, 'matching');
});

test('never falls back from an explicitly requested incompatible native run', async () => {
  const before = process.env.NATIVE_RUN_ID;
  process.env.NATIVE_RUN_ID = '43';
  try {
    const f = fixture({ runs: [nativeRun(43, 'recent'), nativeRun(42, 'matching')],
      comparisons: { recent: { status: 'ahead', files: [{ filename: 'upstream.json' }] } } });
    await assert.rejects(select(f), /Native inputs changed/);
    assert.deepEqual(f.outputs, {});
  } finally {
    if (before === undefined) delete process.env.NATIVE_RUN_ID;
    else process.env.NATIVE_RUN_ID = before;
  }
});
