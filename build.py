import base64
import hashlib
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCES = ROOT / "src" if (ROOT / "src").is_dir() else ROOT
LIBS = ROOT / "libs" if (ROOT / "libs").is_dir() else ROOT.parent / "libs"
ANDROID_JAR = LIBS / "android-35.jar"
HOOK_JAR = LIBS / "hook.jar"
D8_JAR = LIBS / "build-tools" / "lib" / "d8.jar"
DEXDUMP = LIBS / "build-tools" / "dexdump.exe"
DEX = ROOT / "text_animation.dex"
PLUGIN = ROOT / "text_animation.plugin"
DEX_CLASS = "com.textanimation.TextAnimationCore"


def run(*args) -> str:
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True, errors="replace")
    if result.returncode != 0:
        sys.exit(f"{Path(str(args[0])).name} failed ({result.returncode}):\n{result.stdout}{result.stderr}")
    return result.stdout + result.stderr


def main() -> None:
    sources = sorted(SOURCES.glob("*.java"))
    for required in (ANDROID_JAR, HOOK_JAR, D8_JAR, PLUGIN, *sources):
        if not required.is_file():
            sys.exit(f"Required file is missing: {required}")
    if not sources:
        sys.exit("No Java sources found")

    with tempfile.TemporaryDirectory(prefix="text_animation_") as temp:
        temp = Path(temp)
        classes = temp / "classes"
        dex_out = temp / "dex"
        classes.mkdir()
        dex_out.mkdir()
        android_jar = shutil.copy(ANDROID_JAR, temp / "android.jar")
        hook_jar = shutil.copy(HOOK_JAR, temp / "hook.jar")

        print(run(
            "javac", "-encoding", "UTF-8", "-source", "11", "-target", "11", "-Xlint:-options",
            "-cp", f"{android_jar};{hook_jar}" if sys.platform == "win32" else f"{android_jar}:{hook_jar}",
            "-d", classes, *sources,
        ).strip())
        class_files = sorted(classes.rglob("*.class"))
        if not class_files:
            sys.exit("javac produced no class files")

        run("java", "-cp", D8_JAR, "com.android.tools.r8.D8",
            "--lib", android_jar, "--classpath", hook_jar, "--output", dex_out, *class_files)
        dex_bytes = (dex_out / "classes.dex").read_bytes()

        if dex_bytes[:4] != b"dex\n":
            sys.exit("Invalid DEX magic")
        if DEXDUMP.is_file():
            dump = run(DEXDUMP, "-f", dex_out / "classes.dex")
            descriptor = "L" + DEX_CLASS.replace(".", "/") + ";"
            if descriptor not in dump:
                sys.exit(f"{DEX_CLASS} is missing from the DEX")

    digest = hashlib.sha256(dex_bytes).hexdigest()
    DEX.write_bytes(dex_bytes)

    source = PLUGIN.read_text(encoding="utf-8")
    source, sha_count = re.subn(r'(?m)^DEX_SHA256 = "[0-9a-f]*"$', f'DEX_SHA256 = "{digest}"', source)
    encoded = base64.b64encode(dex_bytes).decode("ascii")
    source, dex_count = re.subn(r'(?m)^dex_string = "[A-Za-z0-9+/=]*"$', f'dex_string = "{encoded}"', source)
    if sha_count != 1 or dex_count != 1:
        sys.exit("text_animation.plugin: DEX_SHA256 or dex_string line not found")
    PLUGIN.write_text(source, encoding="utf-8", newline="\n")

    print(f"Built {DEX.name}: {len(dex_bytes)} bytes, {len(class_files)} classes")
    print(f"SHA-256: {digest}")


if __name__ == "__main__":
    main()
