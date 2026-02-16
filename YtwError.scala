package ytw

enum YtwError:
  case CommandNotFound(name: String)
  case CommandFailed(cmd: String, exitCode: Int)
  case InvalidUrl(url: String)
  case AudioNotFound(dir: os.Path, format: String)
  case ModelMissing(path: os.Path)
  case ModelDownloadFailed(path: os.Path)
  case NoDownloadTool
  case FileNotFound(path: String)
  case HlsFetchFailed(url: String)

  def message: String = this match
    case CommandNotFound(name) =>
      s"Required command not found in PATH: $name"
    case CommandFailed(cmd, code) =>
      s"Command failed (exit=$code): $cmd"
    case InvalidUrl(url) =>
      s"Could not extract video id from URL: $url"
    case AudioNotFound(dir, fmt) =>
      s"yt-dlp finished but no .$fmt found in $dir"
    case ModelMissing(path) =>
      s"Missing model file: $path\nDownload it (e.g. from whisper.cpp model hosts) and place it there."
    case ModelDownloadFailed(path) =>
      s"Model download failed or file too small: $path"
    case NoDownloadTool =>
      "Need curl or wget to auto-download models"
    case FileNotFound(path) =>
      s"File not found: $path"
    case HlsFetchFailed(url) =>
      s"Could not extract HLS stream URL from: $url"

object YtwError:
  type Result[+A] = Either[YtwError, A]
