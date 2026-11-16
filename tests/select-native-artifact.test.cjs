const { test } = require('node:test');
const assert = require('node:assert/strict');
const select = require('../scripts/select-native-artifact.cjs');

function fixture({ runs, changed = [], artifacts } = {}) {
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
          listWorkflowRuns: listRuns, listWorkflowRunArtifacts: listArtifacts },
        repos: { compareCommits: async () => ({ data: { status: 'ahead', files: changed } }) },
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
