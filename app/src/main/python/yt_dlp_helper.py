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

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Accept-Language": "en-US,en;q=0.9"
    }

    try:
        # STEP 1: yt-dlp (VIDEO ONLY)
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if info and (info.get("is_video") or info.get("formats")):
                print("SOURCE: yt-dlp")
                print(f"MEDIA_TYPE: video")
                print(f"MEDIA_COUNT: 1")
                return json.dumps({
                    "is_ytdlp": True,
                    "raw": info
                })
            else:
                raise Exception("There is no video in this post")

    except Exception as e:
        error_msg = str(e)
        if "There is no video in this post" not in error_msg:
             print("INSTA_ERROR (yt-dlp):", error_msg)

        # STEP 2: Extract JSON from HTML (PRIMARY SOLUTION)
        print("INSTA_DEBUG: Falling back to sharedData extraction")
        try:
            cleaned_url = url.split("?")[0].rstrip("/")
            req = urllib.request.Request(cleaned_url, headers=headers)
            with urllib.request.urlopen(req) as response:
                html = response.read().decode('utf-8')

                # Regex for window._sharedData
                json_match = re.search(r'window\._sharedData\s*=\s*(\{.*?\});', html)

                media = None
                if json_match:
                    data = json.loads(json_match.group(1))
                    try:
                        media = data["entry_data"]["PostPage"][0]["graphql"]["shortcode_media"]
                    except:
                        pass

                if media:
                    media_list = []
                    typename = media.get("__typename")

                    # STEP 3: PARSE MEDIA (CRITICAL)
                    if typename == "GraphSidecar":
                        edges = media.get("edge_sidecar_to_children", {}).get("edges", [])
                        for edge in edges:
                            node = edge.get("node", {})
                            if node.get("is_video"):
                                media_list.append({"url": clean_url(node.get("video_url")), "ext": "mp4"})
                            else:
                                media_list.append({"url": clean_url(node.get("display_url")), "ext": "jpg"})
                        final_type = "carousel"
                    elif typename == "GraphVideo":
                        media_list.append({"url": clean_url(media.get("video_url")), "ext": "mp4"})
                        final_type = "video"
                    elif typename == "GraphImage":
                        media_list.append({"url": clean_url(media.get("display_url")), "ext": "jpg"})
                        final_type = "image"
                    else:
                        # Typename missing, try is_video
                        if media.get("is_video"):
                            media_list.append({"url": clean_url(media.get("video_url")), "ext": "mp4"})
                            final_type = "video"
                        else:
                            media_list.append({"url": clean_url(media.get("display_url")), "ext": "jpg"})
                            final_type = "image"

                    print("SOURCE: sharedData")
                    print(f"MEDIA_TYPE: {final_type}")
                    print(f"MEDIA_COUNT: {len(media_list)}")
                    print(f"FIRST_URL: {media_list[0]['url'][:50]}...")

                    return json.dumps({
                        "is_ytdlp": False,
                        "type": final_type,
                        "media": media_list,
                        "thumbnail": media_list[0]["url"],
                        "title": media.get("owner", {}).get("username") or "Instagram Media"
                    })

                # STEP 6: LAST RESORT FALLBACK
                print("INSTA_DEBUG: Falling back to og:tags")
                og_image = re.search(r'<meta property="og:image" content="(.*?)"', html)
                if og_image:
                    img_url = clean_url(og_image.group(1))
                    if "static.cdninstagram.com" not in img_url:
                        print("SOURCE: fallback")
                        print(f"MEDIA_TYPE: image")
                        print(f"MEDIA_COUNT: 1")
                        return json.dumps({
                            "is_ytdlp": False,
                            "type": "image",
                            "media": [{"url": img_url, "ext": "jpg"}],
                            "thumbnail": img_url,
                            "title": "Instagram Media"
                        })

                return json.dumps({"error": "Unable to fetch Instagram media"})

        except Exception as e2:
            print("INSTA_ERROR (Final):", str(e2))
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
