"""Shared APK numbering for both workflows, based on UTC build time."""
from datetime import datetime, timezone


def apk_version(now: datetime) -> tuple[int, str]:
    if now.tzinfo is None:
        raise ValueError("Build time must be timezone-aware")
    now = now.astimezone(timezone.utc)
    code = int(now.timestamp()) - 1577836800  # 2020-01-01 UTC
    if not 1 <= code <= 2_100_000_000:
        raise ValueError("Build time is outside the Android version range")
    return code, "0.2." + now.strftime("%Y%m%d%H%M%S")


if __name__ == "__main__":
    for value in apk_version(datetime.now(timezone.utc)):
        print(value)
