# ytw

A command-line tool that downloads audio from YouTube videos and transcribes them using [whisper.cpp](https://github.com/ggerganov/whisper.cpp). Built with Scala CLI.

## Prerequisites

- [Scala CLI](https://scala-cli.virtuslab.org/)
- [yt-dlp](https://github.com/yt-dlp/yt-dlp)
- [ffmpeg](https://ffmpeg.org/)
- [whisper.cpp](https://github.com/ggerganov/whisper.cpp) (`whisper-cli`, `whisper-cpp`, or `whisper` in PATH)

On macOS:

```sh
brew install scala-cli yt-dlp ffmpeg whisper-cpp
```

## Usage

```sh
scala-cli run . -- <youtube_url> [options]
```

### Examples

```sh
# Basic transcription
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID"

# Specify language and model
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID" --lang ja --model medium

# Translate to English
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID" --task translate

# Use browser cookies (for age-restricted / private videos)
scala-cli run . -- "https://www.youtube.com/watch?v=VIDEO_ID" --cookies-from-browser chrome
```

### Options

| Option | Default | Description |
|---|---|---|
| `--out-root` | `runs` | Output directory |
| `--cookies-from-browser` | — | Browser to read cookies from (e.g. `chrome`, `firefox`) |
| `--player-client` | `android` | yt-dlp player client (`android`, `tv`, `web`) |
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

Transcripts are written to `runs/<video_id>/` (or the directory specified by `--out-root`) in SRT, VTT, and TXT formats by default.

## Models

On first run, the required whisper.cpp model is automatically downloaded from HuggingFace to `./models/`. Use `--no-auto-model-download` to disable this and manage models manually.