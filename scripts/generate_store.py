"""Generate a Mihon protobuf store from signed extension packages."""
import argparse
import json
import shutil
import subprocess
from pathlib import Path
from urllib.parse import urlparse

import index_pb2
from google.protobuf.json_format import MessageToJson


def generate(root, base_url, apksigner):
    if urlparse(base_url).scheme != "https" or not urlparse(base_url).netloc:
        raise ValueError("The store base URL must be an absolute HTTPS URL")
    base_url = base_url.rstrip("/")
    config = json.loads((root / "store.json").read_text())
    index = index_pb2.Index(name=config["name"], badgeLabel=config["badgeLabel"], signingKey=config["signingKey"])
    index.contact.website = config.get("website", base_url + "/")
    public = root / "public"
    public.mkdir(exist_ok=True)
    seen_packages, seen_sources = set(), set()
    for folder in sorted((root / "packages").iterdir()):
        if not folder.is_dir():
            continue
        info = json.loads((folder / "metadata.json").read_text())
        package = info["packageName"]
        if package in seen_packages:
            raise ValueError("Duplicate package: " + package)
        seen_packages.add(package)
        verification = subprocess.run([str(apksigner), "verify", "--print-certs", str(folder / "extension.apk")], check=True, capture_output=True, text=True)
        fingerprints = [line.partition("certificate SHA-256 digest:")[2].strip() for line in verification.stdout.splitlines() if "certificate SHA-256 digest:" in line]
        if fingerprints != [config["signingKey"]]:
            raise ValueError("APK signing identity does not match this store: " + package)
        ext = index.extensionList.extensions.add(name=info["name"], packageName=package, extensionLib=info["extensionLib"], versionCode=info["versionCode"], versionName=info["versionName"], contentWarning=info["contentWarning"])
        # Versioned paths prevent stale downloads after the index is updated.
        relative = "packages/" + folder.name + "/" + str(info["versionCode"])
        destination = public / relative
        destination.mkdir(parents=True, exist_ok=True)
        for filename in ("extension.apk", "extension.jar", "icon.png"):
            shutil.copy2(folder / filename, destination / filename)
        ext.resources.apkUrl = base_url + "/" + relative + "/extension.apk"
        ext.resources.jarUrl = base_url + "/" + relative + "/extension.jar"
        ext.resources.iconUrl = base_url + "/" + relative + "/icon.png"
        for source in info["sources"]:
            source_id = int(source["id"])
            if source_id in seen_sources:
                raise ValueError("Duplicate source ID: " + str(source_id))
            seen_sources.add(source_id)
            ext.sources.add(id=source_id, name=source["name"], language=source["lang"], homeUrl=source["baseUrl"], mirrorUrls=source.get("mirrorUrls", []))
    if not seen_packages:
        raise ValueError("The store contains no extensions")
    data = index.SerializeToString()
    decoded = index_pb2.Index.FromString(data)
    assert decoded == index
    (public / "index.pb").write_bytes(data)
    (public / "index.json").write_text(MessageToJson(index), encoding="utf-8")
    (public / "repo.json").write_text(json.dumps({"index_v2": base_url + "/index.pb", "meta": {"name": config["name"], "website": index.contact.website, "signingKeyFingerprint": config["signingKey"]}}, indent=2))
    print(f"Validated {len(seen_packages)} signed extensions; store URL: {base_url}/index.pb")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    args = parser.parse_args()
    generate(Path(__file__).resolve().parents[1], args.base_url, args.apksigner.resolve())
