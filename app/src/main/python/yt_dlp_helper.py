import yt_dlp
import json
import os

def get_video_info(url):
    ydl_opts = {
        "quiet": True,
        "no_warnings": True
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)

            formats = []
            for f in info.get("formats", []):
                vcodec = f.get("vcodec")
                acodec = f.get("acodec")

                formats.append({
                    "format_id": f.get("format_id"),
                    "ext": f.get("ext"),
                    "resolution": f.get("resolution") or f"{f.get('height')}p" if f.get('height') else "audio",
                    "filesize": f.get("filesize") or 0,
                    "vcodec": vcodec,
                    "acodec": acodec
                })

            return json.dumps({
                "title": info.get("title"),
                "thumbnail": info.get("thumbnail"),
                "duration": info.get("duration"),
                "uploader": info.get("uploader"),
                "view_count": str(info.get("view_count", 0)),
                "formats": formats
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

    download_format = format_id
    if not is_audio and "+bestaudio" not in format_id:
         download_format = f"{format_id}+bestaudio/best"

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
        return final_file_path
    except Exception as e:
        print(f"Download error: {e}")
        return None
