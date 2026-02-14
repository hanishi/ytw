package ytw

import YtwError.Result
import java.io.IOException

object Shell:

  def run(cmd: Seq[String]): Result[Unit] =
    try
      val r = os.proc(cmd).call(check = false, stdout = os.Inherit, stderr = os.Inherit)
      Either.cond(r.exitCode == 0, (), YtwError.CommandFailed(cmd.mkString(" "), r.exitCode))
    catch
      case _: IOException =>
        Left(YtwError.CommandFailed(cmd.mkString(" "), -1))

  def cmdExists(name: String): Boolean =
    os.proc("bash", "-lc", s"command -v $name >/dev/null 2>&1")
      .call(check = false)
      .exitCode == 0

  def requireCmd(name: String): Result[Unit] =
    Either.cond(cmdExists(name), (), YtwError.CommandNotFound(name))

  def findFirstCmd(candidates: Seq[String]): Result[String] =
    candidates.find(cmdExists)
      .toRight(YtwError.CommandNotFound(candidates.mkString(", ")))
