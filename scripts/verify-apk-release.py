"""Fail distribution builds when aapt reports a debuggable or unexpected package."""
from pathlib import Path
import sys


def verify(text):
    if "package: name='org.minanode.android'" not in text:
        raise ValueError('Unexpected APK package')
    if 'application-debuggable' in text:
        raise ValueError('Refusing to distribute a debuggable APK')
    if "native-code: 'arm64-v8a'" not in text:
        raise ValueError('Expected ARM64 APK')


if __name__ == '__main__':
    verify(Path(sys.argv[1]).read_text(encoding='utf-8'))
    print('APK is non-debuggable, ARM64 and has the expected package identity')
