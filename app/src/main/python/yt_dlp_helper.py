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
        "user_agent": "Mozilla/5.0",
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            # 4. DEBUG LOGGING (MANDATORY)
            print(f"DEBUG: yt-dlp JSON Response: {json.dumps(info)}")

            media_items = []

            def parse_item(item):
                media_url = None
                source = "none"

                # CASE: url exists
                if item.get("url"):
                    media_url = item.get("url")
                    source = "url"

                # CASE: formats exists
                elif item.get("formats"):
                    formats = item.get("formats")
                    # Prefer width, fallback to filesize
                    best_f = None
                    for f in formats:
                        if not best_f:
                            best_f = f
                            continue

                        f_w = f.get("width") or 0
                        b_w = best_f.get("width") or 0

                        if f_w > b_w:
                            best_f = f
                        elif f_w == b_w:
                            f_s = f.get("filesize") or f.get("filesize_approx") or 0
                            b_s = best_f.get("filesize") or best_f.get("filesize_approx") or 0
                            if f_s > b_s:
                                best_f = f

                    if best_f:
                        media_url = best_f.get("url")
                        source = "formats"

                if media_url:
                    # 5. DATA MODEL FIX (Type detection)
                    ext = item.get("ext", "").lower()
                    is_image = ext in ["jpg", "jpeg", "png", "webp"] or item.get("vcodec") == "none"
                    is_video = ext == "mp4" or (item.get("vcodec") != "none" and item.get("vcodec") is not None)

                    final_type = "video" if is_video and not is_image else "image"

                    print(f"DEBUG: Extraction Strategy: {source} used. Final URL: {media_url[:50]}..., Type: {final_type}")

                    return {
                        "type": final_type,
                        "url": media_url,
                        "thumbnail": item.get("thumbnail") or item.get("display_url"),
                        "ext": ext or ("mp4" if final_type == "video" else "jpg")
                    }
                return None

            # CASE 1: Carousel post
            if "entries" in info:
                print(f"DEBUG: CASE 1: Carousel detected. Entries: {len(info['entries'])}")
                for entry in info["entries"]:
                    parsed = parse_item(entry)
                    if parsed:
                        media_items.append(parsed)

            # CASE 2: Single post
            else:
                print("DEBUG: CASE 2: Single post detected.")
                parsed = parse_item(info)
                if parsed:
                    media_items.append(parsed)

            if not media_items:
                return json.dumps({"error": "Unable to fetch media"})

            return json.dumps({
                "title": info.get("title") or "Instagram Media",
                "thumbnail": info.get("thumbnail"),
                "media_items": media_items
            })

    except Exception as e:
        return json.dumps({
            "error": "Unable to fetch media",
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
