"""Build the modules in extensions/ with the pinned upstream build engine."""
import base64
import json
import os
import shutil
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
engine = root / "build-engine"
required = ("SIGNING_KEY", "KEY_STORE_PASSWORD", "KEY_PASSWORD", "ALIAS")
if any(not os.environ.get(key) for key in required):
    raise RuntimeError("Configure SIGNING_KEY, KEY_STORE_PASSWORD, KEY_PASSWORD and ALIAS repository secrets before rebuilding")
modules = sorted((root / "extensions").glob("*/*/build.gradle.kts"))
if not modules:
    raise RuntimeError("No extension modules found")
key_file = engine / "signingkey.jks"
try:
    key_file.write_bytes(base64.b64decode(os.environ["SIGNING_KEY"], validate=True))
    includes, tasks = [], []
    for module in modules:
        lang, name = module.parent.parent.name, module.parent.name
        shutil.copytree(module.parent, engine / "src" / lang / name, dirs_exist_ok=True)
        includes.append(f'loadIndividualExtension("{lang}", "{name}")')
        tasks.extend(f":src:{lang}:{name}:{task}" for task in ("assembleRelease", "lintRelease", "testDebugUnitTest"))
    settings = engine / "settings.gradle.kts"
    settings.write_text(settings.read_text().replace("\nloadAllIndividualExtensions()\n", "\n" + "\n".join(includes) + "\n"))
    subprocess.run(["bash", "./gradlew", *tasks, "--console=plain"], cwd=engine, check=True)
    for module in modules:
        lang, name = module.parent.parent.name, module.parent.name
        build = engine / "src" / lang / name / "build"
        info = json.loads((build / "keiyoushi-source-info.json").read_text())
        filename = f'tachiyomi-{lang}.{name}-v{info["versionName"]}'
        package = root / "packages" / f"{lang}.{name}"
        package.mkdir(parents=True, exist_ok=True)
        shutil.copy2(build / "outputs/apk/release" / (filename + ".apk"), package / "extension.apk")
        shutil.copy2(build / "outputs/jar/release" / (filename + ".jar"), package / "extension.jar")
        shutil.copy2(build / "keiyoushi-source-info.json", package / "metadata.json")
        icon = next((module.parent / "res").glob("mipmap-*/ic_launcher.png"))
        shutil.copy2(icon, package / "icon.png")
finally:
    key_file.unlink(missing_ok=True)
