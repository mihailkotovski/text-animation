import base64
import hashlib
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCES = ROOT / "src"
LIBS = ROOT / "libs" if (ROOT / "libs").is_dir() else ROOT.parent / "libs"
ANDROID_JAR = LIBS / "android-35.jar"
HOOK_JAR = LIBS / "hook.jar"
D8_JAR = LIBS / "build-tools" / "lib" / "d8.jar"
DEXDUMP = LIBS / "build-tools" / "dexdump.exe"
DEX = ROOT / "text_animation.dex"
PLUGIN = ROOT / "text_animation.plugin"
ENTRY = "Lcom/textanimation/TextAnimationCore;"
BRIDGE = ("startBridge", "updateSettingsBridge", "onUnloadBridge", "BRIDGE_API", "VERSION_CODE")


def run(*args) -> str:
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True, errors="replace")
    if result.returncode != 0:
        sys.exit(f"{Path(str(args[0])).name} failed ({result.returncode}):\n{result.stdout}{result.stderr}")
    return result.stdout + result.stderr


def compile_dex(temp: Path) -> bytes:
    sources = sorted(SOURCES.glob("*.java"))
    if not sources:
        sys.exit(f"No Java sources in {SOURCES}")
    classes = temp / "classes"
    output = temp / "dex"
    classes.mkdir()
    output.mkdir()
    android_jar = shutil.copy(ANDROID_JAR, temp / "android.jar")
    hook_jar = shutil.copy(HOOK_JAR, temp / "hook.jar")
    separator = ";" if sys.platform == "win32" else ":"
    print(run(
        "javac", "-encoding", "UTF-8", "-source", "11", "-target", "11", "-Xlint:all,-options,-processing",
        "-cp", f"{android_jar}{separator}{hook_jar}", "-d", classes, *sources,
    ).strip())
    class_files = sorted(classes.rglob("*.class"))
    run("java", "-cp", D8_JAR, "com.android.tools.r8.D8", "--release", "--min-api", "21",
        "--lib", android_jar, "--classpath", hook_jar, "--output", output, *class_files)
    payload = (output / "classes.dex").read_bytes()
    if payload[:4] != b"dex\n":
        sys.exit("Invalid DEX magic")
    if DEXDUMP.is_file():
        dump = run(DEXDUMP, "-f", output / "classes.dex")
        missing = [name for name in (ENTRY, *BRIDGE) if name not in dump]
        if missing:
            sys.exit(f"DEX is missing: {', '.join(missing)}")
    return payload


def main() -> None:
    for required in (ANDROID_JAR, HOOK_JAR, D8_JAR, PLUGIN):
        if not required.is_file():
            sys.exit(f"Required file is missing: {required}")
    with tempfile.TemporaryDirectory(prefix="text_animation_") as temp:
        payload = compile_dex(Path(temp))
    digest = hashlib.sha256(payload).hexdigest()
    DEX.write_bytes(payload)
    source = PLUGIN.read_text(encoding="utf-8")
    source, hashes = re.subn(r'(?m)^DEX_SHA256 = "[0-9a-f]*"$', f'DEX_SHA256 = "{digest}"', source)
    encoded = base64.b64encode(payload).decode("ascii")
    source, bodies = re.subn(r'(?m)^DEX_BASE64 = "[A-Za-z0-9+/=]*"$', f'DEX_BASE64 = "{encoded}"', source)
    if hashes != 1 or bodies != 1:
        sys.exit("text_animation.plugin: DEX_SHA256 or DEX_BASE64 line not found")
    PLUGIN.write_text(source, encoding="utf-8", newline="\n")
    print(f"Built {DEX.name}: {len(payload)} bytes, SHA-256 {digest}")


if __name__ == "__main__":
    main()
