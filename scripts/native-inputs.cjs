const isNativeInput = path => path === 'upstream.json' || path.startsWith('patches/') || path.startsWith('tests/credentials/')
  || ['scripts/build-android.sh', 'scripts/prepare-source.py', 'tests/android-file-lock.rs'].includes(path);

module.exports = { isNativeInput };
