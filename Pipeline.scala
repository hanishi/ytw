package ytw

import YtwError.Result
import java.security.MessageDigest

object Pipeline:

  private val IdRe      = raw"""(?:v=|youtu\.be/)([A-Za-z0-9_-]{11})""".r
  private val HotmartRe = raw"""player\.hotmart\.com/embed/([^?/]+)""".r
  private val M3u8Re    = raw""""(https?://[^"]+\.m3u8[^"]*)"""".r

  def isLocalFile(input: String): Boolean =
    os.exists(os.Path(input, os.pwd))

  def isYouTube(url: String): Boolean =
    url.contains("youtube.com") || url.contains("youtu.be")

  def isHotmart(url: String): Boolean =
    url.contains("player.hotmart.com/embed/")

  def extractId(input: String): Result[String] =
    if isLocalFile(input) then
      Right(os.Path(input, os.pwd).baseName)
    else if isHotmart(input) then
      HotmartRe.findFirstMatchIn(input).map(_.group(1))
        .toRight(YtwError.InvalidUrl(input))
    else if isYouTube(input) then
      IdRe.findFirstMatchIn(input).map(_.group(1))
        .toRight(YtwError.InvalidUrl(input))
    else
      val digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes("UTF-8"))
      Right(digest.map("%02x".format(_)).mkString.take(11))

  def extractAudioFromFile(filePath: os.Path, audioFormat: String, outDir: os.Path): Result[os.Path] =
    if !os.exists(filePath) then Left(YtwError.FileNotFound(filePath.toString))
    else
      val outFile = outDir / s"${filePath.baseName}.$audioFormat"
      val cmd = List("ffmpeg", "-i", filePath.toString, "-vn", "-y", outFile.toString)
      Shell.run(cmd).map(_ => outFile)

  private val UnicodeEscRe = raw"""\\u([0-9a-fA-F]{4})""".r
  private def unescapeJson(s: String): String =
    UnicodeEscRe.replaceAllIn(s, m => Integer.parseInt(m.group(1), 16).toChar.toString)

  def fetchHlsUrl(pageUrl: String): Result[String] =
    Shell.capture(Seq("curl", "-s", "-L", pageUrl)).flatMap { html =>
      M3u8Re.findFirstMatchIn(html).map(m => unescapeJson(m.group(1)))
        .toRight(YtwError.HlsFetchFailed(pageUrl))
    }

  def downloadHlsAudio(hlsUrl: String, audioFormat: String, id: String, outDir: os.Path, referer: String): Result[os.Path] =
    val outFile = outDir / s"$id.$audioFormat"
    val cmd = List(
      "ffmpeg",
      "-headers", s"Referer: $referer\r\n",
      "-i", hlsUrl,
      "-vn", "-y", outFile.toString
    )
    Shell.run(cmd).map(_ => outFile)

  def downloadAudio(opts: Opts, outDir: os.Path): Result[os.Path] =
    val outtmpl = (outDir / "%(id)s.%(ext)s").toString
    val cmd = downloadCommand(opts, outtmpl)

    Shell.run(cmd).flatMap { _ =>
      os.list(outDir)
        .filter(_.ext == opts.audioFormat)
        .sortBy(p => os.stat(p).mtime.toMillis)
        .lastOption
        .toRight(YtwError.AudioNotFound(outDir, opts.audioFormat))
    }

  private def downloadCommand(opts: Opts, outtmpl: String): List[String] =
    val client =
      if opts.cookiesFromBrowser.isDefined && opts.playerClient == "android" then "web"
      else opts.playerClient

    List("yt-dlp") ++
      opts.cookiesFromBrowser.toList.flatMap(c => List("--cookies-from-browser", c)) ++
      List(
        "--no-playlist",
        "-x",
        "--audio-format", opts.audioFormat,
        "-o", outtmpl
      ) ++
      (if isYouTube(opts.url) then List("--extractor-args", s"youtube:player_client=$client") else Nil) ++
      List(opts.url)

  private val ModelAliases: Map[String, String] = Map(
    "large" -> "large-v3-turbo"
  )

  def ensureModel(modelName: String, autoDownload: Boolean): Result[os.Path] =
    val resolved = ModelAliases.getOrElse(modelName, modelName)
    val modelsDir = os.pwd / "models"
    os.makeDir.all(modelsDir)
    val modelFile = modelsDir / s"ggml-$resolved.bin"

    if os.exists(modelFile) then Right(modelFile)
    else if !autoDownload then Left(YtwError.ModelMissing(modelFile))
    else
      val url = s"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$resolved.bin"
      println(s"[ytw] model missing; downloading: $url")

      for
        dlCmd <- downloadModelCommand(modelFile, url)
        _     <- Shell.run(dlCmd)
        _     <- validateModel(modelFile)
      yield
        println(s"[ytw] model ready: $modelFile")
        modelFile

  private def downloadModelCommand(modelFile: os.Path, url: String): Result[List[String]] =
    if Shell.cmdExists("curl") then Right(List("curl", "-L", "-o", modelFile.toString, url))
    else if Shell.cmdExists("wget") then Right(List("wget", "-O", modelFile.toString, url))
    else Left(YtwError.NoDownloadTool)

  private def validateModel(modelFile: os.Path): Result[Unit] =
    Either.cond(
      os.exists(modelFile) && os.size(modelFile) >= 1024 * 1024,
      (),
      YtwError.ModelDownloadFailed(modelFile)
    )

  def transcribe(whisperBin: String, modelFile: os.Path, opts: Opts, outDir: os.Path, audioPath: os.Path): Result[Unit] =
    val prefix = (outDir / audioPath.baseName).toString

    val cmd = List(
      whisperBin,
      "-m", modelFile.toString,
      "-f", audioPath.toString,
      "-of", prefix,
      "-t", opts.threads.toString
    ) ++
      opts.lang.toList.flatMap(l => List("-l", l)) ++
      Option.when(opts.task == "translate")("-tr").toList ++
      Option.when(opts.srt)("-osrt").toList ++
      Option.when(opts.vtt)("-ovtt").toList

    println(s"[ytw] whisper: ${cmd.mkString(" ")}")
    Shell.run(cmd)
