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

def download_video(url, format_id, output_path, is_audio, is_progressive, fast_mode, progress_callback):
    final_file_path = None

    def progress_hook(d):
        nonlocal final_file_path
        if d['status'] == 'downloading':
            total = d.get('total_bytes') or d.get('total_bytes_estimate')
            downloaded = d.get('downloaded_bytes', 0)
            if total:
                percent = int(downloaded * 100 / total)
                print(f"PROGRESS:{percent}")

                # Keep original callback for compatibility or secondary tracking
                try:
                    progress_callback.onProgress(downloaded / total, d.get('_speed_str', '0B/s'))
                except:
                    pass
        elif d['status'] == 'finished':
            final_file_path = d.get('filename')
            print("PROGRESS:100")
            try:
                progress_callback.onProgress(1.0, "Finished")
            except:
                pass

    if fast_mode:
        download_format = "best[ext=mp4]"
    else:
        download_format = format_id

    ydl_opts = {
        "format": download_format,
        "outtmpl": f"{output_path}/%(title)s.%(ext)s",
        "progress_hooks": [progress_hook],
        "quiet": False,
        "no_warnings": True,
        "nocheckcertificate": True,
        "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        "referer": "https://www.youtube.com/",
        "noplaylist": True,
        "merge_output_format": "mp4",
    }

    try:
        if not is_audio and not is_progressive and not fast_mode:
            # High quality DASH: Return both stream URLs for Kotlin-side merging
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                video_format = next((f for f in info['formats'] if f['format_id'] == format_id), None)
                audio_format = next((f for f in info['formats'] if f.get('acodec') != 'none' and f.get('vcodec') == 'none'), None)

                if video_format and audio_format:
                    return json.dumps({
                        "status": "dash_info",
                        "video_url": video_format['url'],
                        "audio_url": audio_format['url'],
                        "title": info.get("title", "video"),
                        "ext": video_format.get("ext", "mp4"),
                        "audio_ext": audio_format.get("ext", "m4a")
                    })

        # Fast mode, progressive, or audio-only
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])

        if final_file_path and os.path.exists(final_file_path):
            return json.dumps({"status": "success", "file_path": final_file_path})
        else:
            return json.dumps({"status": "error", "error": "Download finished but file not found"})

    except Exception as e:
        return json.dumps({"status": "error", "error": str(e), "traceback": traceback.format_exc()})
