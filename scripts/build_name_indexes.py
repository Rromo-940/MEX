"""Build incremental name-index shards from captured HTML or explicit catalogue exports.
No access-control bypasses or phone-side catalogue crawling.
"""
import argparse
import hashlib
import json
import re
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin, urlparse
from bs4 import BeautifulSoup
SOURCES = {"hentaidad":"https://hentaidad.com", "truyenhentai":"https://www.truyen-hentai.com", "thehentai":"https://en.thehentai.net", "hentaiyoga":"https://hentai.yoga", "porncomic":"https://porn-comic.com"}
STOP=set("girl girls images image erotic hentai generated sex porn part collection the and with from for full set patreon fanbox pixiv artist".split())
def words(text):
 return re.findall("[a-z0-9]+",unicodedata.normalize("NFKD",text).encode("ascii","ignore").decode().lower())
def emit(out,source,href,title,thumb,country=""):
 base=SOURCES[source];u=urlparse(urljoin(base,href))
 if u.hostname!=urlparse(base).hostname or not title.strip():return
 out[source][u.path]=[u.path,title.strip(),urljoin(base,thumb) if thumb else "",country]
def capture(out,path):
 s=BeautifulSoup(path.read_text(encoding="utf-8",errors="replace"),"html.parser")
 for c in s.select("article.archive-card"):
  if c.select_one(".archive-card-type") is None or c.select_one(".archive-card-type").text.strip()!="Gallery":continue
  a=c.select_one("a.archive-card-link");t=c.select_one(".archive-card-title");im=c.select_one("img")
  if a and t:emit(out,"hentaidad",a.get("href",""),t.text,im.get("src","") if im else "")
 for c in s.select("#wall a.grid-card"):
  if not c.select_one(".grid-card__format--gallery"):continue
  t=c.select_one(".grid-card__title");im=c.select_one("img")
  if t:emit(out,"truyenhentai",c.get("href",""),t.text,im.get("src","") if im else "")
 for c in s.select(".gridPosts"):
  a=c.select_one("h3 a[href]");im=c.select_one(".img_cage img")
  if a and a.get("href","").startswith("/"):emit(out,"thehentai",a["href"],a.text,im.get("src","") if im else "")
 for c in s.select("article.gallery-card, article.mcard"):
  a=c.select_one(".gallery-title-link, .mcard-title-link");im=c.select_one("img");lang=c.select_one('[itemprop="inLanguage"]')
  if a:emit(out,"hentaiyoga",a.get("href",""),a.text,im.get("src","") if im else "",lang.get("content","") if lang else "")
 for c in s.select("#list > li, #doujin_album > li"):
  a=c.select_one("a.thumb");im=a.select_one("img") if a else None
  if not a or not im:continue
  href=a.get("href","");title=im.get("alt","")
  if not re.fullmatch(r"/(h|hentai|cos|western)/(comic-)?[0-9]+\.html",urlparse(href).path):continue
  if re.search(r"\b(video|mp4)\b",title,re.I) or (c.select_one(".category") and "video" in c.select_one(".category").text.lower()):continue
  emit(out,"porncomic",href,title,im.get("src",""))
def main():
 ap=argparse.ArgumentParser();ap.add_argument("--input-dir",type=Path,required=True);ap.add_argument("--root",type=Path,default=Path.cwd());args=ap.parse_args();out={x:{} for x in SOURCES}
 for source in SOURCES:
  for p in (args.root/f"public/search/{source}/v1").glob("*.json"):
   if p.name=="manifest.json":continue
   for row in json.loads(p.read_text()):out[source][row[0]]=row
 for p in args.input_dir.glob("*.html"):capture(out,p)
 for source in SOURCES:
  export=args.input_dir/f"{source}.records.json"
  if export.exists():
   for row in json.loads(export.read_text(encoding="utf-8-sig")):emit(out,source,row[0],row[1],row[2] if len(row)>2 else "",row[3] if len(row)>3 else "")
  buckets={i:[] for i in range(64)}
  for row in out[source].values():
   prefixes={w[i:i+3] for w in words(row[1]+" "+row[0]) if len(w)>=3 and w not in STOP for i in range(len(w)-2)}
   for b in {hashlib.sha256(pre.encode()).digest()[0]&63 for pre in prefixes}:buckets[b].append(row)
  dest=args.root/f"public/search/{source}/v1";dest.mkdir(parents=True,exist_ok=True)
  for b,rows in buckets.items():(dest/f"{b:02x}.json").write_text(json.dumps(sorted(rows,key=lambda x:x[0]),ensure_ascii=True,separators=(",",":")))
  meta={"schema":1,"builtAt":datetime.now(timezone.utc).isoformat(),"galleries":len(out[source]),"coverage":"partial","origin":("Public gallery listing captures; full catalogue export not found" if source=="porncomic" else "Previously captured public HTML and explicit exports; automated catalogue access currently blocked"),"shards":64}
  (dest/"manifest.json").write_text(json.dumps(meta,indent=2)+"\n");print(source,len(out[source]))
if __name__=="__main__":main()
