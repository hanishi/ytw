package ytw

import YtwError.Result

object Pipeline:

  private val IdRe = raw"""(?:v=|youtu\.be/)([A-Za-z0-9_-]{11})""".r

  def extractVideoId(url: String): Result[String] =
    IdRe.findFirstMatchIn(url).map(_.group(1))
      .toRight(YtwError.InvalidUrl(url))

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
        "-o", outtmpl,
        "--extractor-args", s"youtube:player_client=$client",
        opts.url
      )

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
