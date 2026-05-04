import hashlib
import html
import json
import os
import re
from datetime import datetime, timezone

import feedparser
from bs4 import BeautifulSoup

import firebase_admin
from firebase_admin import credentials, firestore


PROJECT_ID = "klimate-se"
COLLECTION_NAME = "news_article"
MAX_ARTICLES = 5

RSS_FEEDS = [
    {
        "source": "BBC",
        "category": "Nature",
        "url": "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml",
    },
    {
        "source": "The Guardian",
        "category": "Environment",
        "url": "https://www.theguardian.com/environment/rss",
    },
    {
        "source": "NASA Earth Observatory",
        "category": "Earth",
        "url": "https://earthobservatory.nasa.gov/feeds/earth-observatory.rss",
    },
    {
        "source": "Mongabay",
        "category": "Nature",
        "url": "https://news.mongabay.com/feed/",
    },
]

NATURE_KEYWORDS = [
    "nature", "forest", "wildlife", "biodiversity", "species", "animal",
    "animals", "bird", "birds", "ocean", "river", "climate", "environment",
    "conservation", "tree", "trees", "earth", "habitat", "pollution",
    "ecosystem", "planet", "rainforest", "coral", "marine", "green"
]


def clean_text(value, max_len=230):
    if not value:
        return ""

    value = html.unescape(value)
    value = BeautifulSoup(value, "html.parser").get_text(" ", strip=True)
    value = re.sub(r"\s+", " ", value).strip()

    if len(value) <= max_len:
        return value

    trimmed = value[:max_len].rsplit(" ", 1)[0].strip()
    return trimmed + "…"


def parse_datetime(entry):
    parsed = getattr(entry, "published_parsed", None) or getattr(entry, "updated_parsed", None)
    if parsed:
        return datetime(*parsed[:6], tzinfo=timezone.utc)
    return datetime.now(timezone.utc)


def is_nature_article(title, summary):
    text = f"{title} {summary}".lower()
    return any(keyword in text for keyword in NATURE_KEYWORDS)


def stable_doc_id(url):
    return hashlib.sha1(url.encode("utf-8")).hexdigest()[:20]


def fetch_articles():
    articles = []
    seen_urls = set()

    for feed in RSS_FEEDS:
        parsed_feed = feedparser.parse(feed["url"])

        for entry in parsed_feed.entries:
            title = clean_text(getattr(entry, "title", ""), max_len=120)
            url = getattr(entry, "link", "")
            summary = clean_text(
                getattr(entry, "summary", "") or getattr(entry, "description", ""),
                max_len=230,
            )

            if not title or not url:
                continue

            if url in seen_urls:
                continue

            if not is_nature_article(title, summary):
                continue

            seen_urls.add(url)

            articles.append({
                "approved": True,
                "title": title,
                "summary": summary if summary else "Tap to read this nature story.",
                "url": url,
                "source": feed["source"],
                "category": feed["category"],
                "publishedAt": parse_datetime(entry),
                "refreshedAt": datetime.now(timezone.utc),
            })

    articles.sort(key=lambda item: item["publishedAt"], reverse=True)
    return articles[:MAX_ARTICLES]


def delete_existing_articles(db):
    docs = list(db.collection(COLLECTION_NAME).stream())
    if not docs:
        return

    batch = db.batch()
    count = 0

    for doc in docs:
        batch.delete(doc.reference)
        count += 1

        if count % 450 == 0:
            batch.commit()
            batch = db.batch()

    batch.commit()


def write_articles(db, articles):
    batch = db.batch()

    for article in articles:
        doc_id = stable_doc_id(article["url"])
        ref = db.collection(COLLECTION_NAME).document(doc_id)
        batch.set(ref, article)

    batch.commit()


def main():
    service_account_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT_KEY")

    if not service_account_json:
        raise RuntimeError("Missing FIREBASE_SERVICE_ACCOUNT_KEY GitHub secret.")

    service_account_info = json.loads(service_account_json)

    if not firebase_admin._apps:
        cred = credentials.Certificate(service_account_info)
        firebase_admin.initialize_app(cred, {"projectId": PROJECT_ID})

    db = firestore.client()

    articles = fetch_articles()

    if not articles:
        raise RuntimeError("No nature articles found. Keeping Firestore unchanged.")

    delete_existing_articles(db)
    write_articles(db, articles)

    print(f"Uploaded {len(articles)} articles to Firestore collection '{COLLECTION_NAME}'.")
    for article in articles:
        print(f"- {article['source']}: {article['title']}")


if __name__ == "__main__":
    main()
