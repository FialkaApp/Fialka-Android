#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = []
# ///
"""
Fialka Android — Local Release Signing Script
==============================================
Prerequisites:
  - uv          (https://docs.astral.sh/uv/)
  - Android SDK (apksigner in build-tools)
  - GnuPG       (gpg, configured with key 0845E4B46420A2EA)
  - GitHub CLI  (gh, authenticated)

Usage:
  uv run scripts/release.py

Steps:
  1. Read VERSION_NAME / VERSION_CODE from version.properties
  2. Read RELEASE_STORE_FILE / RELEASE_KEY_ALIAS from local.properties
  3. Download unsigned APK artifact from the CI run for the current tag
  4. Sign with apksigner (password via stdin — never in process list)
  5. Compute SHA256 hash
  6. GPG clearsign the hash file
  7. Create GitHub Release with signed APK + hash files
"""

import getpass
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

# Force UTF-8 output on Windows (cp1252 console can't render emojis)
if sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# ── Constants ─────────────────────────────────────────────────────────────────

REPO        = "FialkaApp/Fialka-Android"
GPG_KEY_ID  = "0845E4B46420A2EA"
SCRIPT_DIR  = Path(__file__).resolve().parent
REPO_ROOT   = SCRIPT_DIR.parent

# ── Helpers ───────────────────────────────────────────────────────────────────

def read_properties(path: Path) -> dict[str, str]:
    props = {}
    if not path.exists():
        return props
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, _, v = line.partition("=")
            # Java .properties escapes backslashes (e.g. sdk.dir=C\:\\Users\\...)
            v = v.replace("\\:", ":").replace("\\\\", "\\")
            props[k.strip()] = v.strip()
    return props


def find_apksigner() -> Path:
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_root:
        local = read_properties(REPO_ROOT / "local.properties")
        sdk_root = local.get("sdk.dir")
    if not sdk_root:
        sys.exit("❌ Android SDK not found. Set ANDROID_HOME or add sdk.dir to local.properties.")

    build_tools = Path(sdk_root) / "build-tools"
    candidates = sorted(
        [p for bt in build_tools.iterdir() if bt.is_dir()
         for p in bt.iterdir() if p.stem == "apksigner"],
        key=lambda p: [int(x) for x in re.findall(r"\d+", p.parent.name)],
        reverse=True,
    )
    if not candidates:
        sys.exit(f"❌ apksigner not found under {sdk_root}/build-tools/")
    return Path(candidates[0])


def run(cmd: list[str], *, cwd=None, check=True) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=cwd, check=check)


def run_output(cmd: list[str], *, cwd=None, check=True) -> str:
    result = subprocess.run(cmd, cwd=cwd, check=check, capture_output=True, text=True)
    return result.stdout.strip()

# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    # 1. Read versions
    version_props = read_properties(REPO_ROOT / "version.properties")
    version_name  = version_props.get("VERSION_NAME")
    version_code  = version_props.get("VERSION_CODE")
    if not version_name:
        sys.exit("❌ VERSION_NAME not found in version.properties")

    tag_name = f"v{version_name}"
    apk_name = f"fialka-{version_name}.apk"

    # 2. Read signing config from local.properties
    local_props   = read_properties(REPO_ROOT / "local.properties")
    keystore_path = local_props.get("RELEASE_STORE_FILE")
    key_alias     = local_props.get("RELEASE_KEY_ALIAS", "fialka")

    if not keystore_path:
        # Fallback: fialka-release.p12 one level above the repo root
        fallback = REPO_ROOT.parent / "fialka-release.p12"
        if fallback.exists():
            keystore_path = str(fallback)
        else:
            sys.exit("❌ RELEASE_STORE_FILE not found in local.properties and no fallback keystore found.")

    # Resolve relative paths against the repo root
    keystore_path = str((REPO_ROOT / keystore_path).resolve())
    if not Path(keystore_path).exists():
        sys.exit(f"❌ Keystore not found: {keystore_path}")

    apksigner = find_apksigner()

    print(f"\n🔐 Fialka {version_name} (versionCode {version_code})")
    print(f"   Keystore : {keystore_path}")
    print(f"   Key alias: {key_alias}")
    print(f"   apksigner: {apksigner}")

    # 3. Prompt for password (not echoed, never in process args)
    password = getpass.getpass("\nKeystore password: ")
    password_bytes = password.encode("utf-8")
    password = None  # clear from Python string asap (best-effort)

    with tempfile.TemporaryDirectory(prefix="fialka-release-") as work_dir:
        work = Path(work_dir)

        # 4. Download unsigned APK from GitHub Actions
        print(f"\n⬇️  Downloading unsigned APK for {tag_name} ...")
        artifact_name = f"fialka-unsigned-{tag_name}"

        # Find the run triggered by this tag
        run_id = None
        try:
            run_id = run_output([
                "gh", "run", "list", "--repo", REPO,
                "--event", "push",
                "--json", "databaseId,headBranch,status",
                "--jq", f'[.[] | select(.headBranch=="{tag_name}" and .status=="completed")] | .[0].databaseId'
            ])
        except subprocess.CalledProcessError:
            pass

        if not run_id or run_id == "null":
            # Fallback: latest successful run
            run_id = run_output([
                "gh", "run", "list", "--repo", REPO,
                "--status", "success",
                "--json", "databaseId",
                "--jq", ".[0].databaseId"
            ])

        if not run_id or run_id == "null":
            sys.exit("❌ No completed CI run found. Ensure the CI pipeline has finished for this tag.")

        run(["gh", "run", "download", run_id, "--repo", REPO,
             "--name", artifact_name, "--dir", str(work)])

        unsigned_apks = list(work.glob("**/*.apk"))
        if not unsigned_apks:
            sys.exit(f"❌ No APK found after artifact download in {work}")
        unsigned_apk = unsigned_apks[0]
        print(f"   Downloaded: {unsigned_apk.name}")

        # 5. Sign the APK
        signed_apk = work / apk_name
        print(f"\n✍️  Signing APK ...")

        # Write password to a temp file — stdin is unreliable with .bat wrappers on Windows.
        # File is zeroed and deleted immediately after signing.
        pwd_file = work / ".pwd"
        # apksigner file: paths must use forward slashes on Windows
        pwd_file_uri = pwd_file.as_posix()
        try:
            # apksigner file: mode reads line 1 for ks-pass, line 2 for key-pass
            pwd_file.write_bytes(password_bytes + b"\n" + password_bytes)
            run([
                str(apksigner), "sign",
                "--ks", keystore_path,
                "--ks-key-alias", key_alias,
                "--ks-pass", f"file:{pwd_file_uri}",
                "--key-pass", f"file:{pwd_file_uri}",
                "--v1-signing-enabled", "false",
                "--v2-signing-enabled", "true",
                "--v3-signing-enabled", "true",
                "--v4-signing-enabled", "true",
                "--out", str(signed_apk),
                str(unsigned_apk),
            ])
        finally:
            # Zero and delete the password file regardless of success/failure
            pwd_file.write_bytes(b"\x00" * len(password_bytes))
            pwd_file.unlink(missing_ok=True)

        # Zero the password bytes from memory
        password_bytes = b"\x00" * len(password_bytes)
        del password_bytes

        # Verify
        run([str(apksigner), "verify", "--verbose", str(signed_apk)])
        print(f"   Signed & verified: {apk_name}")

        # 6. SHA256
        print(f"\n🔢 Computing SHA256 ...")
        digest = hashlib.sha256(signed_apk.read_bytes()).hexdigest()
        hash_file = work / "release-hashes.txt"
        hash_file.write_text(f"{digest}  {apk_name}\n", encoding="utf-8")
        print(f"   {digest}  {apk_name}")

        # 7. GPG clearsign
        print(f"\n🔏 GPG clearsigning hashes ...")
        hash_asc_file = work / "release-hashes.txt.asc"
        run([
            "gpg", "--batch", "--yes",
            "--local-user", GPG_KEY_ID,
            "--clearsign",
            "--output", str(hash_asc_file),
            str(hash_file),
        ])
        print(f"   Signed: {hash_asc_file.name}")

        # 8. Build release notes
        asc_content  = hash_asc_file.read_text(encoding="utf-8").strip()
        git_sha      = run_output(["git", "rev-parse", "HEAD"], cwd=str(REPO_ROOT))
        git_sha_short = git_sha[:7]
        is_prerelease = bool(re.search(r"alpha|beta", version_name, re.IGNORECASE))
        pre_note = "\n> ⚠️ Pre-release — requires **Android 13+** (API 33+)\n" if is_prerelease else ""

        release_notes = f"""\
## 🔐 Fialka {version_name} — `versionCode {version_code}`
{pre_note}
---

### 📥 Installation
1. Download `fialka-{version_name}.apk` below
2. Enable **Install from unknown sources** on your device
3. Install the APK

---

### 🔧 APK Signing
Signed locally with **ECDSA P-256 + SHA-256** (PKCS12)
APK schemes **v2 + v3 + v4** enabled — v1 disabled

```
sha256: {digest}  {apk_name}
```

---

### 🏗️ Build Provenance
Built from commit [`{git_sha_short}`](https://github.com/{REPO}/commit/{git_sha})
via GitHub Actions — source: [Fialka-Android@{tag_name}](https://github.com/{REPO}/tree/{tag_name})

---

### 🔐 Signature cryptographique

Signed SHA256 hashes of release files.
Developer GPG key: `FialkaApp <devbot1667+fialka@gmail.com>`, ID `{GPG_KEY_ID}`

```
{asc_content}
```

**Verify:**
```bash
gpg --keyserver keys.openpgp.org --recv-keys {GPG_KEY_ID}
gpg --verify release-hashes.txt.asc
sha256sum -c release-hashes.txt
```
"""
        notes_file = work / "release-notes.md"
        notes_file.write_text(release_notes, encoding="utf-8")

        # 9. Publish GitHub Release
        print(f"\n🚀 Publishing GitHub Release {tag_name} ...")

        # Delete existing release if present
        subprocess.run(
            ["gh", "release", "delete", tag_name, "--repo", REPO, "--yes"],
            capture_output=True
        )

        gh_args = [
            "gh", "release", "create", tag_name,
            "--repo", REPO,
            "--title", f"🔐 Fialka {version_name}",
            "--notes-file", str(notes_file),
            str(signed_apk),
            str(hash_file),
            str(hash_asc_file),
        ]
        if is_prerelease:
            gh_args.append("--prerelease")

        run(gh_args)

        print(f"\n✅ Release {tag_name} published successfully.")
        print(f"   https://github.com/{REPO}/releases/tag/{tag_name}")

    # TemporaryDirectory cleaned up automatically here
    print("\n🧹 Working directory cleaned up.")


if __name__ == "__main__":
    main()
