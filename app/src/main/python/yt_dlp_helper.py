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


def get_instagram_info(url):
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "nocheckcertificate": True,
        "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            # DEBUG LOGGING
            print(f"DEBUG: Instagram keys: {list(info.keys())}")

            media_items = []

            # STEP 1: HANDLE CAROUSEL
            entries = info.get("entries")
            if entries and len(entries) > 0:
                print(f"DEBUG: Carousel detected. Size: {len(entries)}")
                for i, entry in enumerate(entries):
                    is_video = entry.get("is_video", False)
                    print(f"DEBUG: Entry {i} is_video={is_video}, keys={list(entry.keys())}")

                    media_url = None
                    if is_video:
                        media_url = entry.get("url")
                        if not media_url and entry.get("formats"):
                            # Pick best progressive format
                            for f in entry.get("formats", []):
                                if f.get("vcodec") != "none" and f.get("acodec") != "none":
                                    if not media_url or (f.get("height") or 0) > 0: # simplified best logic
                                        media_url = f.get("url")

                        if media_url:
                            media_items.append({"type": "video", "url": media_url, "ext": "mp4", "thumbnail": entry.get("thumbnail") or entry.get("display_url")})
                    else:
                        media_url = entry.get("display_url")
                        if not media_url:
                            media_url = entry.get("url")

                        if media_url:
                            media_items.append({"type": "image", "url": media_url, "ext": "jpg", "thumbnail": media_url})

                if media_items:
                    return json.dumps({
                        "title": info.get("title") or info.get("description") or "Instagram Media",
                        "thumbnail": info.get("thumbnail") or media_items[0].get("thumbnail"),
                        "media_items": media_items
                    })

            # STEP 2: HANDLE SINGLE POST
            is_video = info.get("is_video", False)
            print(f"DEBUG: Single post. is_video={is_video}")

            media_url = None
            if is_video:
                media_url = info.get("url")
                if not media_url and info.get("formats"):
                    for f in info.get("formats", []):
                        if f.get("vcodec") != "none" and f.get("acodec") != "none":
                            media_url = f.get("url")

                if media_url:
                    media_items.append({"type": "video", "url": media_url, "ext": "mp4", "thumbnail": info.get("thumbnail") or info.get("display_url")})
            else:
                media_url = info.get("display_url")
                if not media_url:
                    media_url = info.get("url")

                if media_url:
                    media_items.append({"type": "image", "url": media_url, "ext": "jpg", "thumbnail": media_url})

            # STEP 3: FINAL SAFETY FALLBACK
            if not media_items:
                print("DEBUG: Final safety fallback")
                if info.get("thumbnail"):
                    media_items.append({"type": "image", "url": info["thumbnail"], "ext": "jpg", "thumbnail": info["thumbnail"]})
                else:
                    return json.dumps({"error": "Unable to extract media from this Instagram post"})

            return json.dumps({
                "title": info.get("title") or info.get("description") or "Instagram Media",
                "thumbnail": info.get("thumbnail") or media_items[0].get("thumbnail"),
                "media_items": media_items
            })

    except Exception as e:
        return json.dumps({
            "error": str(e),
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