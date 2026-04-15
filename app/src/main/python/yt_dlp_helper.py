import yt_dlp
import json
import os
import traceback


def get_video_info(url):
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "nocheckcertificate": True,
        "user_agent": "Mozilla/5.0",
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            formats = info.get("formats", [])
            video_formats = []
            audio_formats = []

            seen_resolutions = set()
            seen_bitrates = set()

            for f in formats:
                vcodec = f.get("vcodec")
                acodec = f.get("acodec")

                # ❌ skip invalid
                if vcodec == "none" and acodec == "none":
                    continue

                # ✅ ONLY progressive video (video + audio)
                if vcodec != "none" and acodec != "none":
                    height = f.get("height")
                    if not height or height in seen_resolutions:
                        continue

                    video_formats.append({
                        "format_id": f.get("format_id"),
                        "resolution": f"{height}p",
                        "height": height,
                        "ext": f.get("ext"),
                        "filesize": f.get("filesize") or 0,
                        "is_progressive": True
                    })
                    seen_resolutions.add(height)

                # ✅ audio only
                elif vcodec == "none" and acodec != "none":
                    abr = f.get("abr")
                    if not abr or abr in seen_bitrates:
                        continue

                    audio_formats.append({
                        "format_id": f.get("format_id"),
                        "bitrate": f"{int(abr)} kbps",
                        "abr": abr,
                        "ext": f.get("ext"),
                        "filesize": f.get("filesize") or 0
                    })
                    seen_bitrates.add(abr)

            # ✅ sort properly
            video_formats.sort(key=lambda x: x["height"], reverse=True)
            audio_formats.sort(key=lambda x: x["abr"], reverse=True)

            return json.dumps({
                "title": info.get("title"),
                "thumbnail": info.get("thumbnail"),
                "duration": info.get("duration"),
                "uploader": info.get("uploader"),
                "view_count": str(info.get("view_count", 0)),
                "video_formats": video_formats,
                "audio_formats": audio_formats
            })

    except Exception as e:
        return json.dumps({
            "error": str(e),
            "traceback": traceback.format_exc()
        })


import urllib.request
import re

def clean_url(url):
    if not url:
        return url
    return (
        url.replace("&amp;", "&")
           .replace("\\u0026", "&")
           .replace("\\/", "/")
    )

def get_instagram_info(url):
    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'extract_flat': False,
        'skip_download': True,
    }

    try:
        # STEP 1: Try yt-dlp
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            if info and (info.get("is_video") or info.get("formats")):
                print("INSTA_DEBUG: yt-dlp success for video")
                return json.dumps({
                    "is_ytdlp": True,
                    "raw": info
                })
            else:
                raise Exception("There is no video in this post")

    except Exception as e:
        error_msg = str(e)
        print("INSTA_ERROR (yt-dlp):", error_msg)

        # STEP 2: FALLBACK to HTML scraping
        print("INSTA_DEBUG: Falling back to HTML scraping")
        try:
            headers = { "User-Agent": "Mozilla/5.0" }
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req) as response:
                html = response.read().decode('utf-8')

                # 1. Extract Media
                media_list = []

                # Try Carousel first (JSON-like scraping from HTML)
                display_urls = re.findall(r'"display_url":"(.*?)"', html)
                if display_urls:
                    for d_url in list(dict.fromkeys(display_urls)): # unique urls
                        cleaned = clean_url(d_url)
                        print("FINAL_URL (carousel):", cleaned)
                        media_list.append({"url": cleaned, "ext": "jpg"})

                # 2. Extract og: tags for single post
                og_image = re.search(r'<meta property="og:image" content="(.*?)"', html)
                og_video = re.search(r'<meta property="og:video" content="(.*?)"', html)
                og_title = re.search(r'<meta property="og:title" content="(.*?)"', html)

                final_type = "image"
                if not media_list:
                    if og_video:
                        final_type = "video"
                        cleaned = clean_url(og_video.group(1))
                        print("FINAL_URL (video):", cleaned)
                        media_list.append({"url": cleaned, "ext": "mp4"})
                    elif og_image:
                        cleaned = clean_url(og_image.group(1))
                        print("FINAL_URL (image):", cleaned)
                        media_list.append({"url": cleaned, "ext": "jpg"})

                if not media_list:
                    return json.dumps({"error": "Unable to fetch Instagram media"})

                return json.dumps({
                    "is_ytdlp": False,
                    "type": final_type,
                    "media": media_list,
                    "thumbnail": og_image.group(1) if og_image else (media_list[0]["url"] if media_list else None),
                    "title": og_title.group(1) if og_title else "Instagram Media"
                })

        except Exception as e2:
            print("INSTA_ERROR (Scraper):", str(e2))
            return json.dumps({"error": "Unable to fetch media from this post"})


def download_instagram_image(url, output_path):
    try:
        url = clean_url(url)
        urllib.request.urlretrieve(url, output_path)
        return json.dumps({"status": "success", "file_path": output_path})
    except Exception as e:
        return json.dumps({"status": "error", "error": str(e)})


# ✅ SIMPLE FORMAT (NO MERGE EVER)
def get_format(format_id, is_audio, is_progressive):
    return format_id


def download_video(url, format_id, output_path, is_audio, is_progressive, progress_callback):
    def progress_hook(d):
        if d['status'] == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate')
            downloaded = d.get('downloaded_bytes', 0)

            if total:
                percent = int(downloaded * 100 / total)
                print(f"PROGRESS:{percent}")

                try:
                    progress_callback.onProgress(
                        downloaded / total,
                        d.get('_speed_str', '0B/s')
                    )
                except:
                    pass

        elif d['status'] == 'finished':
            print("PROGRESS:100")
            try:
                progress_callback.onProgress(1.0, "Finalizing...")
            except:
                pass

    ydl_opts = {
        "format": format_id,  # ✅ ALWAYS direct (no merge)
        "outtmpl": f"{output_path}/%(title)s.%(ext)s",
        "progress_hooks": [progress_hook],
        "quiet": False,
        "no_warnings": True,
        "nocheckcertificate": True,
        "noplaylist": True,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)

            final_path = ydl.prepare_filename(info)

            return json.dumps({
                "status": "success",
                "type": "single",
                "file_path": final_path
            })

    except Exception as e:
        return json.dumps({
            "status": "error",
            "error": str(e),
            "traceback": traceback.format_exc()
        })
