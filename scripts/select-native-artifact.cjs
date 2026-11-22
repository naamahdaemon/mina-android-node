// Called by actions/github-script. Only reuse artifacts from this repository's
// main branch, and reject changes to the native compiler inputs since that run.
const { isNativeInput } = require('./native-inputs.cjs');

module.exports = async ({ github, context, core }) => {
  const repo = context.repo;
  const requested = (process.env.NATIVE_RUN_ID || '').trim();
  if (requested && !/^\d+$/.test(requested)) throw new Error('native_run_id must be numeric');
  const workflow = (await github.rest.actions.getWorkflow({ ...repo, workflow_id: 'android-native.yml' })).data;
  const runs = requested
    ? [(await github.rest.actions.getWorkflowRun({ ...repo, run_id: Number(requested) })).data]
    : await github.paginate(github.rest.actions.listWorkflowRuns, {
        ...repo, workflow_id: workflow.id, branch: 'main', status: 'completed', per_page: 50,
      });
  let incompatibility;
  for (const run of runs) {
    if (run.workflow_id !== workflow.id || run.head_branch !== 'main'
        || run.head_repository?.full_name !== `${repo.owner}/${repo.repo}`
        || !['push', 'workflow_dispatch'].includes(run.event) || run.status !== 'completed') {
      if (requested) throw new Error('Choose a completed native push/manual run from this repository on main');
      continue;
    }
    const artifacts = await github.paginate(github.rest.actions.listWorkflowRunArtifacts, { ...repo, run_id: run.id });
    if (!artifacts.some(a => a.name === 'mina-android-arm64' && !a.expired)) continue;
    const comparison = (await github.rest.repos.compareCommits({
      ...repo, base: run.head_sha, head: context.sha,
    })).data;
    if (!['ahead', 'identical'].includes(comparison.status) || (comparison.files || []).length >= 300) {
      incompatibility = 'Native artifact ancestry cannot be verified. Run the native workflow for the current source.';
      if (requested) throw new Error(incompatibility);
      continue;
    }
    if ((comparison.files || []).some(f => isNativeInput(f.filename) || (f.previous_filename && isNativeInput(f.previous_filename)))) {
      incompatibility = 'Native inputs changed since this artifact. Run Android native prototype first; no automatic Rust rebuild is performed here.';
      if (requested) throw new Error(incompatibility);
      continue;
    }
    core.setOutput('run_id', String(run.id));
    core.setOutput('source_sha', run.head_sha);
    await core.summary.addHeading('Reused Mina native binary')
      .addLink(`Native run ${run.id}`, run.html_url)
      .addRaw(`\nSource commit: ${run.head_sha}\n`).write();
    return;
  }
  if (incompatibility) throw new Error(incompatibility);
  throw new Error('No unexpired mina-android-arm64 artifact found on main. Run Android native prototype first.');
};
