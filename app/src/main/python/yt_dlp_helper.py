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

def get_instagram_info(url):
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "nocheckcertificate": True,
        "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    }

    try:
        info = None
        media_items = []

        # STEP 1: ATTEMPT YT-DLP FOR VIDEOS ONLY
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                if info and info.get("is_video"):
                    media_url = info.get("url")
                    if not media_url and info.get("formats"):
                        best_f = None
                        for f in info.get("formats", []):
                            if f.get("vcodec") != "none" and f.get("acodec") != "none":
                                if not best_f or (f.get("height") or 0) > (best_f.get("height") or 0):
                                    best_f = f
                        if best_f:
                            media_url = best_f.get("url")

                    if media_url:
                        media_items.append({
                            "type": "video",
                            "url": media_url,
                            "thumbnail": info.get("thumbnail") or info.get("display_url"),
                            "ext": "mp4"
                        })
        except:
            pass

        # STEP 2: FIX HTML PARSING (PRIMARY SOURCE FOR IMAGES)
        if not media_items:
            try:
                headers = {"User-Agent": "Mozilla/5.0"}
                req = urllib.request.Request(url, headers=headers)
                with urllib.request.urlopen(req) as response:
                    html = response.read().decode('utf-8')

                    # STEP 3: PARSE HTML (og:image primary)
                    image_match = re.search(r'<meta property="og:image" content="(.*?)"', html)
                    video_match = re.search(r'<meta property="og:video" content="(.*?)"', html)

                    if image_match:
                        raw_image_url = image_match.group(1)
                        print(f"DEBUG: RAW extracted URL: {raw_image_url}")

                        # STEP 4: CLEAN URL (CRITICAL)
                        cleaned_image_url = raw_image_url.replace("&amp;", "&")
                        print(f"DEBUG: FINAL cleaned URL: {cleaned_image_url}")

                        # STEP 5: VALIDATE URL
                        if "cdninstagram" in cleaned_image_url or "fbcdn" in cleaned_image_url:
                            media_items.append({
                                "type": "image",
                                "url": cleaned_image_url,
                                "thumbnail": cleaned_image_url,
                                "ext": "jpg"
                            })
                        else:
                            print("DEBUG: Invalid image URL extracted (CDN check failed)")

                    # FALLBACK TO VIDEO IF IMAGE FAILED
                    if not media_items and video_match:
                        raw_video_url = video_match.group(1).replace("&amp;", "&")
                        media_items.append({
                            "type": "video",
                            "url": raw_video_url,
                            "thumbnail": None,
                            "ext": "mp4"
                        })
            except Exception as e:
                print(f"DEBUG: HTML scraping failed: {str(e)}")

        if not media_items:
            return json.dumps({"error": "Unable to fetch media from this post"})

        return json.dumps({
            "title": (info.get("title") if info else None) or "Instagram Media",
            "thumbnail": media_items[0]["thumbnail"],
            "media_items": media_items
        })

    except Exception as e:
        return json.dumps({
            "error": "Unable to fetch media from this post",
            "traceback": traceback.format_exc()
        })


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
