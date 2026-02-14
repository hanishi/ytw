//> using scala "3.6.2"
//> using dep "com.lihaoyi::os-lib:0.11.8"
//> using dep "com.github.scopt::scopt:4.1.0"

package ytw

import YtwError.Result

case class Opts(
  url: String = "",
  outRoot: String = "runs",
  cookiesFromBrowser: Option[String] = None,
  playerClient: String = "android",
  audioFormat: String = "mp3",
  model: String = "small",
  lang: Option[String] = None,
  task: String = "transcribe",
  threads: Int = math.max(2, Runtime.getRuntime.availableProcessors() / 2),
  srt: Boolean = true,
  vtt: Boolean = true,
  txt: Boolean = true,
  autoDownloadModel: Boolean = true
)

@main def ytwMain(args: String*): Unit =
  val parser = new scopt.OptionParser[Opts]("ytw") {
    head("ytw (scala-cli)", "0.2.0")

    opt[String]("out-root").action((v, o) => o.copy(outRoot = v))

    opt[String]("cookies-from-browser")
      .action((v, o) => o.copy(cookiesFromBrowser = Some(v)))
      .text("""e.g. "chrome" or "chrome:Profile 1" """)

    opt[String]("player-client")
      .action((v, o) => o.copy(playerClient = v))
      .validate(v => if Set("android","tv","web")(v) then success else failure("android|tv|web"))

    opt[String]("audio-format")
      .action((v, o) => o.copy(audioFormat = v))
      .validate(v => if Set("mp3","m4a","wav")(v) then success else failure("mp3|m4a|wav"))

    opt[String]("model")
      .action((v, o) => o.copy(model = v))
      .text("whisper.cpp model name; expects ./models/ggml-<model>.bin (auto-download on by default)")

    opt[String]("lang")
      .action((v, o) => o.copy(lang = Some(v)))
      .text("language code, e.g. en, ja (optional; auto-detect if omitted)")

    opt[String]("task")
      .action((v, o) => o.copy(task = v))
      .validate(v => if Set("transcribe","translate")(v) then success else failure("transcribe|translate"))

    opt[Int]("threads").action((v, o) => o.copy(threads = v))

    opt[Unit]("no-srt").action((_, o) => o.copy(srt = false))
    opt[Unit]("no-vtt").action((_, o) => o.copy(vtt = false))
    opt[Unit]("no-txt").action((_, o) => o.copy(txt = false))

    opt[Unit]("no-auto-model-download")
      .action((_, o) => o.copy(autoDownloadModel = false))
      .text("Do not auto-download missing ggml model")

    arg[String]("<youtube_url>").required().action((v, o) => o.copy(url = v))
  }

  parser.parse(args.toSeq, Opts()) match
    case Some(opts) =>
      runPipeline(opts) match
        case Right(_)  => ()
        case Left(err) =>
          System.err.println(s"[ytw] ERROR: ${err.message}")
          sys.exit(1)
    case None =>
      sys.exit(2)

private def runPipeline(opts: Opts): Result[Unit] =
  for
    _          <- Shell.requireCmd("yt-dlp")
    _          <- Shell.requireCmd("ffmpeg")
    whisperBin <- Shell.findFirstCmd(Seq("whisper-cli", "whisper-cpp", "whisper"))
    _          =  println(s"[ytw] whisper_bin=$whisperBin")
    vid        <- Pipeline.extractVideoId(opts.url)
    outDir     =  { val d = os.pwd / opts.outRoot / vid; os.makeDir.all(d); d }
    _          =  println(s"[ytw] video_id=$vid")
    _          =  println(s"[ytw] out=$outDir")
    audio      <- Pipeline.downloadAudio(opts, outDir)
    _          =  println(s"[ytw] audio=${audio.last}")
    modelFile  <- Pipeline.ensureModel(opts.model, opts.autoDownloadModel)
    _          <- Pipeline.transcribe(whisperBin, modelFile, opts, outDir, audio)
  yield
    println(s"[ytw] done: $outDir")
