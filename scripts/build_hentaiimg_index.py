"""Build small gallery-name lookup shards from the site's public image sitemaps."""
import concurrent.futures
import gzip
import io
import json
import re
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

BASE = "https://hentai-img.com"
NS = "{http://www.sitemaps.org/schemas/sitemap/0.9}"
IMAGE = "{http://www.google.com/schemas/sitemap-image/1.1}"

def words(value):
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode().lower()
    return re.findall("[a-z0-9]+", value)

def prefixes(slug):
    return {word[:3] for word in words(urllib.parse.unquote(slug)) if len(word) >= 3}

def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "MEX-Gallery-Index/1.0"}), timeout=90) as response:
                return response.read()
        except (OSError, urllib.error.HTTPError):
            if attempt == 2:
                raise
            time.sleep(2 * (attempt + 1))

def entries(data):
    for _, element in ET.iterparse(io.BytesIO(gzip.decompress(data)), events=("end",)):
        if element.tag != NS + "url":
            continue
        url = element.findtext(NS + "loc", "")
        parsed = urllib.parse.urlparse(url)
        if parsed.hostname == "hentai-img.com" and re.fullmatch(r"/image/[^/]+/?", parsed.path):
            slug = parsed.path.strip("/").split("/")[1]
            thumbnail = element.findtext(IMAGE + "image/" + IMAGE + "loc", "")
            yield [slug, thumbnail]
        element.clear()

def main():
    index = ET.fromstring(fetch(BASE + "/sitemap_index_images_detail.xml"))
    urls = [node.text for node in index.findall(".//" + NS + "loc")]
    if not urls or any(not re.fullmatch(r"https://hentai-img\.com/sitemap_images_detail_[0-9]+\.xml\.gz", url) for url in urls):
        raise ValueError("Unexpected sitemap index")
    buckets = defaultdict(list)
    seen = set()
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for count, records in enumerate(pool.map(lambda url: list(entries(fetch(url))), urls), 1):
            for record in records:
                if record[0] in seen:
                    continue
                seen.add(record[0])
                for prefix in prefixes(record[0]):
                    buckets[prefix].append(record)
            if count % 20 == 0 or count == len(urls):
                print(f"Sitemaps {count}/{len(urls)}; galleries {len(seen)}", flush=True)
    if len(seen) < 10000 or not any("mimiya" in slug for slug in seen):
        raise ValueError("Incomplete index or missing regression creator")
    destination = Path("public/search/hentaiimg/v1")
    destination.mkdir(parents=True, exist_ok=True)
    sizes = []
    for prefix, records in sorted(buckets.items()):
        records.sort(key=lambda record: record[0])
        data = json.dumps(records, ensure_ascii=True, separators=(",", ":")).encode()
        encoded = gzip.compress(data, mtime=0)
        (destination / (prefix + ".json.gz")).write_bytes(encoded)
        sizes.append(len(encoded))
    metadata = {"schema": 1, "builtAt": datetime.now(timezone.utc).isoformat(), "galleries": len(seen), "sitemaps": len(urls), "shards": len(buckets), "bytes": sum(sizes), "maxShardBytes": max(sizes), "mimiyaGalleries": sum("mimiya" in slug for slug in seen), "titleSource": "Public gallery URL slugs; display titles are approximations"}
    (destination / "manifest.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata), flush=True)

if __name__ == "__main__":
    main()
