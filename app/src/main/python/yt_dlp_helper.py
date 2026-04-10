import yt_dlp
import json

def get_video_info(url):
    ydl_opts = {
        'skip_download': True,
        'quiet': True,
        'no_warnings': True,
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            # Filter and simplify info for Kotlin
            simplified_info = {
                'title': info.get('title', 'Unknown Title'),
                'thumbnail': info.get('thumbnail', ''),
                'duration_string': info.get('duration_string', '0:00'),
                'uploader': info.get('uploader', 'Unknown Creator'),
                'view_count': str(info.get('view_count', 0)),
                'formats': []
            }

            raw_formats = info.get('formats', [])

            # Filter for progressive formats (video + audio) or audio-only
            filtered_formats = []
            for f in raw_formats:
                vcodec = f.get('vcodec', 'none')
                acodec = f.get('acodec', 'none')
                ext = f.get('ext', '')

                is_video = vcodec != 'none' and acodec != 'none'
                is_audio = vcodec == 'none' and acodec != 'none'

                if (is_video and ext == 'mp4') or (is_audio and ext in ['m4a', 'mp3']):
                    f['is_video_type'] = is_video
                    filtered_formats.append(f)

            # Sort video formats by height (descending)
            video_formats = [f for f in filtered_formats if f['is_video_type']]
            video_formats.sort(key=lambda x: x.get('height', 0) or 0, reverse=True)

            audio_formats = [f for f in filtered_formats if not f['is_video_type']]

            seen_labels = set()
            for f in video_formats + audio_formats:
                fid = f.get('format_id', '')
                ext = f.get('ext', '')
                note = f.get('format_note', '') or f.get('resolution', '')
                vcodec = f.get('vcodec', 'none')
                acodec = f.get('acodec', 'none')
                is_video = f['is_video_type']

                height = f.get('height')
                label = f"{height}p MP4 VIDEO" if is_video and height else f"{note} ({ext})"
                if not is_video:
                    label = f"Audio MP3 ({ext})" if ext == 'mp3' else f"Audio M4A ({ext})"

                if label not in seen_labels:
                    simplified_info['formats'].append({
                        'format_id': fid,
                        'ext': ext,
                        'format_note': label,
                        'vcodec': vcodec,
                        'acodec': acodec,
                        'type': 'video' if is_video else 'audio'
                    })
                    seen_labels.add(label)

            return json.dumps(simplified_info)
    except Exception as e:
        return json.dumps({'error': str(e)})

def download_video(url, format_id, output_path, is_audio, progress_callback):
    def progress_hook(d):
        if d['status'] == 'downloading':
            p = d.get('_percent_str', '0%').replace('%', '').strip()
            s = d.get('_speed_str', '0B/s')
            try:
                progress_callback.onProgress(float(p) / 100.0, s)
            except:
                pass
        elif d['status'] == 'finished':
            try:
                progress_callback.onProgress(1.0, "Finished")
            except:
                pass

    ydl_opts = {
        'format': format_id,
        'outtmpl': output_path,
        'progress_hooks': [progress_hook],
        'quiet': True,
        'no_warnings': True,
    }

    # If audio only, we might want to convert to mp3 but that needs ffmpeg
    # For now we use the selected format_id which should be m4a if audio

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
        return True
    except Exception as e:
        print(f"Download error: {e}")
        return False
