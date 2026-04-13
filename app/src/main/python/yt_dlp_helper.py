import yt_dlp
import json
import os
import shutil
import traceback

def get_video_info(url):
    ydl_opts = {
        "quiet": True,
        "no_warnings": True,
        "nocheckcertificate": True,
        "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "referer": "https://www.youtube.com/"
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            formats = info.get("formats", [])
            video_only_formats = []
            audio_only_formats = []

            for f in formats:
                vcodec = f.get("vcodec")
                acodec = f.get("acodec")

                if vcodec == "none" and acodec == "none":
                    continue

                is_progressive = vcodec != "none" and acodec != "none"

                if vcodec != "none" and acodec == "none":
                    f['is_progressive'] = False
                    video_only_formats.append(f)
                elif acodec != "none" and vcodec == "none":
                    audio_only_formats.append(f)
                elif is_progressive:
                    f['is_progressive'] = True
                    video_only_formats.append(f)

            merged_video_list = []
            seen_resolutions = set()
            for v in video_only_formats:
                res = v.get("height")
                if not res or res in seen_resolutions:
                    continue

                merged_video_list.append({
                    "format_id": v.get("format_id"),
                    "resolution": f"{res}p",
                    "height": res,
                    "ext": v.get("ext"),
                    "filesize": v.get("filesize") or 0,
                    "type": "video",
                    "is_progressive": v.get('is_progressive', False)
                })
                seen_resolutions.add(res)

            merged_video_list.sort(key=lambda x: x["height"] or 0, reverse=True)

            audio_list = []
            seen_bitrates = set()
            for a in audio_only_formats:
                abr = a.get("abr")
                if not abr or abr in seen_bitrates:
                    continue

                audio_list.append({
                    "format_id": a.get("format_id"),
                    "bitrate": f"{int(abr)} kbps",
                    "abr": abr,
                    "ext": a.get("ext"),
                    "filesize": a.get("filesize") or 0,
                    "type": "audio"
                })
                seen_bitrates.add(abr)

            audio_list.sort(key=lambda x: x["abr"] or 0, reverse=True)

            return json.dumps({
                "title": info.get("title"),
                "thumbnail": info.get("thumbnail"),
                "duration": info.get("duration"),
                "uploader": info.get("uploader"),
                "view_count": str(info.get("view_count", 0)),
                "video_formats": merged_video_list,
                "audio_formats": audio_list
            })
    except Exception as e:
        return json.dumps({"error": str(e), "traceback": traceback.format_exc()})

def get_format(format_id, is_audio, is_progressive):
    # If it's audio only, just return the format_id
    if is_audio:
        return format_id

    # Extract height from format_id (e.g. "137" -> 1080p, "22" -> 720p)
    # This is a bit simplified, but yt-dlp's format selection logic is flexible.
    # If the user specifically asked for a progressive format (already has audio), use it.
    if is_progressive:
        return format_id

    # If it's a DASH format (video only), we need bestvideo + bestaudio
    return f"{format_id}+bestaudio/best"

def download_video(url, format_id, output_path, is_audio, is_progressive, progress_callback):
    final_files = []

    def progress_hook(d):
        if d['status'] == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate')
            downloaded = d.get('downloaded_bytes', 0)
            if total:
                percent = int(downloaded * 100 / total)
                print(f"PROGRESS:{percent}")
                try:
                    progress_callback.onProgress(downloaded / total, d.get('_speed_str', '0B/s'))
                except:
                    pass
        elif d['status'] == 'finished':
            final_files.append(d.get('filename'))
            print("PROGRESS:100")
            try:
                progress_callback.onProgress(1.0, "Finished")
            except:
                pass

    ydl_opts = {
        "format": get_format(format_id, is_audio, is_progressive),
        "outtmpl": f"{output_path}/%(title)s.%(ext)s",
        "progress_hooks": [progress_hook],
        "quiet": False,
        "no_warnings": True,
        "nocheckcertificate": True,
        "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "referer": "https://www.youtube.com/",
        "noplaylist": True,
        # IMPORTANT: Disable internal merging so we can handle it in Android
        "merge_output_format": None,
        "postprocessors": [],
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=True)

            if is_audio or is_progressive:
                return json.dumps({
                    "status": "success",
                    "type": "single",
                    "file_path": ydl.prepare_filename(info)
                })
            else:
                # High quality DASH
                requested_downloads = info.get("requested_downloads", [])
                if len(requested_downloads) >= 2:
                    return json.dumps({
                        "status": "success",
                        "type": "merge",
                        "video_path": requested_downloads[0]['filepath'],
                        "audio_path": requested_downloads[1]['filepath'],
                        "output_path": os.path.join(output_path, f"{info.get('title', 'video')}.mp4")
                    })
                else:
                    # Fallback if only one file was downloaded (e.g. it was actually progressive)
                    return json.dumps({
                        "status": "success",
                        "type": "single",
                        "file_path": ydl.prepare_filename(info)
                    })

    except Exception as e:
        return json.dumps({"status": "error", "error": str(e), "traceback": traceback.format_exc()})
