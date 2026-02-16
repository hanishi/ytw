# ytw

A command-line tool that transcribes audio and video using [whisper.cpp](https://github.com/ggerganov/whisper.cpp). Supports local audio/video files, [Hotmart](https://hotmart.com/) embedded videos, and URLs from YouTube, Vimeo, and [hundreds of other sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md) via [yt-dlp](https://github.com/yt-dlp/yt-dlp). Built with Scala CLI.

## Prerequisites

- [Scala CLI](https://scala-cli.virtuslab.org/)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp) (only required for URL input; not needed for Hotmart or local files)
- [curl](https://curl.se/) (only required for Hotmart URLs)
- [ffmpeg](https://ffmpeg.org/)
- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) (`whisper-cli`, `whisper-cpp`, or `whisper` in PATH)

On macOS:

```sh
brew install scala-cli yt-dlp ffmpeg whisper-cpp
```

## Usage

```sh
scala-cli run . -- <url_or_file> [options]
```

### Examples

```sh
# Transcribe a local audio file
scala-cli run . -- /path/to/recording.mp3

# Transcribe a local video file (audio is extracted via ffmpeg)
scala-cli run . -- /path/to/lecture.mp4

# Basic transcription (YouTube)
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID"

# Non-YouTube URL (any yt-dlp supported site)
scala-cli run . -- "https://vimeo.com/123456789"

# Specify language and model
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID" --lang ja --model medium

# Translate to English
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID" --task translate

# Transcribe a Hotmart embedded video (no yt-dlp needed, requires curl)
scala-cli run . -- "https://player.hotmart.com/embed/MEDIA_CODE"

# Use browser cookies (for age-restricted / private videos)
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID" --cookies-from-browser chrome
```

### Options

| Option | Default | Description |
|---|---|---|
| `--out-root` | `runs` | Output directory |
| `--cookies-from-browser` | — | Browser to read cookies from (e.g. `chrome`, `firefox`) |
| `--player-client` | `android` | yt-dlp player client for YouTube (`android`, `tv`, `web`) |
| `--audio-format` | `mp3` | Audio format (`mp3`, `m4a`, `wav`) |
| `--model` | `small` | whisper.cpp model name (e.g. `tiny`, `base`, `small`, `medium`, `large`) |
| `--lang` | auto-detect | Language code (e.g. `en`, `ja`) |
| `--task` | `transcribe` | `transcribe` or `translate` (translate to English) |
| `--threads` | half of available CPUs | Number of CPU threads for whisper |
| `--no-srt` | — | Disable SRT output |
| `--no-vtt` | — | Disable VTT output |
| `--no-txt` | — | Disable plain text output |
| `--no-auto-model-download` | — | Don't auto-download missing models |

## Output

Transcripts are written to `runs/<id>/` (or the directory specified by `--out-root`) in SRT, VTT, and TXT formats by default. The ID is determined as follows:

- **Local files**: the filename without extension (e.g. `lecture.mp4` → `lecture`)
- **YouTube URLs**: the video ID
- **Hotmart URLs**: the media code from the embed URL
- **Other URLs**: a short hash of the URL

## Models

On first run, the required whisper.cpp model is automatically downloaded from HuggingFace to `./models/`. Use `--no-auto-model-download` to disable this and manage models manually.