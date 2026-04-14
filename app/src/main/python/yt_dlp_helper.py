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
        "format": "best",
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            def parse_item(item):
                # Instagram specific: check if it's a video
                is_video = item.get('vcodec') != 'none' and item.get('vcodec') is not None
                if not is_video:
                    # Fallback check
                    is_video = item.get('ext') == 'mp4' or item.get('acodec') != 'none'

                return {
                    "type": "video" if is_video else "image",
                    "url": item.get("url"),
                    "thumbnail": item.get("thumbnail"),
                    "ext": item.get("ext", "mp4" if is_video else "jpg")
                }

            media_items = []
            entries = info.get("entries")

            if entries:
                for entry in entries:
                    media_items.append(parse_item(entry))
            else:
                media_items.append(parse_item(info))

            return json.dumps({
                "title": info.get("title") or info.get("description") or "Instagram Media",
                "thumbnail": info.get("thumbnail"),
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