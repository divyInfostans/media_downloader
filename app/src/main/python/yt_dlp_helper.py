import yt_dlp
import json
import os
import shutil

def get_video_info(url):
    ydl_opts = {
        "quiet": True,
        "no_warnings": True
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

                if vcodec != "none" and acodec == "none":
                    video_only_formats.append(f)
                elif acodec != "none" and vcodec == "none":
                    audio_only_formats.append(f)
                elif vcodec != "none" and acodec != "none":
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
                    "type": "video"
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
        return json.dumps({"error": str(e)})

def download_video(url, format_id, output_path, is_audio, progress_callback):
    final_file_path = None

    def progress_hook(d):
        nonlocal final_file_path
        if d['status'] == 'downloading':
            p = d.get('_percent_str', '0%').replace('%', '').strip()
            s = d.get('_speed_str', '0B/s')
            try:
                progress_callback.onProgress(float(p) / 100.0, s)
            except:
                pass
        elif d['status'] == 'finished':
            final_file_path = d.get('filename')
            try:
                progress_callback.onProgress(1.0, "Finished")
            except:
                pass

    ffmpeg_available = shutil.which("ffmpeg") is not None

    download_format = format_id
    if not is_audio:
        if ffmpeg_available:
            download_format = f"{format_id}+bestaudio/best"
        else:
            # If ffmpeg is missing, we must use a progressive format.
            # We try to find the best progressive format that matches or is close to the resolution.
            # But the simplest fallback is 'best' which is usually progressive if no merging allowed.
            download_format = "best"

    ydl_opts = {
        "format": download_format,
        "outtmpl": f"{output_path}/%(title)s.%(ext)s",
        "merge_output_format": "mp4",
        "progress_hooks": [progress_hook],
        "quiet": True,
        "no_warnings": True,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])

        if final_file_path and os.path.exists(final_file_path):
            return json.dumps({"status": "success", "file_path": final_file_path})
        else:
            return json.dumps({"status": "error", "error": "Download finished but file not found"})

    except Exception as e:
        error_msg = str(e)
        if "ffmpeg" in error_msg.lower():
             error_msg = "FFmpeg is not installed on this device. High quality merging is not supported. Please provide FFmpeg binary in assets."
        return json.dumps({"status": "error", "error": error_msg})
