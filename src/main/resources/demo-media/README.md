# Authored demo fixtures — not AI generated

These two committed files were authored for Cadence17 on2026-10-16. Both visibly display
DEMO / NOT AI GENERATED. They are safe fixed demo inputs, not evidence of an AI provider result.

| File | Descriptor | SHA256 |
| --- | --- | --- |
| demo-image-v1.png | PNG512×512,6688bytes |6318b17380802c6bf6ecc6b0333af1a064ea70b5944ddbc36da4319d671ffc99|
| demo-video-v1.mp4 | MP4/H.264/yuv420p360×640,2seconds/12fps,6288bytes |ac056108384f7da890c0c38f7171ea46447e69d4536b9ef4405a35f7296be9f2|

PNG authored using Pillow RGB canvas/background#24304a, yellow#ffe66d text, Windows Arial Bold:
DEMO42px at one-third height, NOT AI GENERATED21px at half height, horizontally centered. Video
uses an equivalent360×640 static frame. One-time encoder: Ubuntu FFmpeg6.1.1-3ubuntu5 in a Docker
tool image based on the existing reviewed renderer; encoding ran network-disabled/read-only with
capabilities dropped. No FFmpeg binary or codec runtime dependency is shipped in the backend.

```text
ffmpeg -hide_banner -loglevel error -loop 1 -i demo-video-frame.png -t 2 -r 12 -c:v libx264 -pix_fmt yuv420p -movflags +faststart -map_metadata -1 -fflags +bitexact -flags:v +bitexact demo-video-v1.mp4
ffprobe -v error -show_entries stream=codec_name,width,height,pix_fmt,duration -of json demo-video-v1.mp4
```

Actual ffprobe confirmed h264/360/640/yuv420p/2.000000. The committed golden bytes, domain catalog,
SQL checksum/descriptor constraint and fixture tests define v1. Regeneration on different font/tool
versions may change bytes: use a new fixture version and forward migration rather than overwrite
v1 hashes/history. No general uploaded-video decoding or provider moderation is implied.
