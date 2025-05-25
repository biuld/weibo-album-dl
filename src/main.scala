import util.log
import util.scheduler
import weibo.api.getAlbum
import weibo.api.sinaVisitorSystem
import Repl.run as runRepl

import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

def run(p: String) =
  val dir = util.path(p)
  if !os.exists(dir) then throw FileNotFoundException(s"$p does not exists")
  if os.list(dir).isEmpty then
    throw FileNotFoundException(
      s"$p is empty, try to create an empty folder under $p, then name it after your uid. All the images will be downloaded into it!"
    )

  val cookies = sinaVisitorSystem

  os.list(dir)
    .filter(os.isDir(_))
    .sortBy(_.baseName)
    .foreach(path =>
      log.info(s"fetching ${path.baseName}")
      val cnt = getAlbum(path.baseName, dir, cookies)
      log.info(s"${path.baseName}: $cnt in total")
      Thread.sleep(60_000)
    )
  log.info("finished term")

def logHelp(args: Seq[String]) = log.info(s"""
  unsupported args: ${args.mkString(" ")}!
  try these blow:
    -w `path` to walk through uids inside `path`
    -s `path` to walk through uids inside `path` periodically (once a day)
    -u `uid` `path` to download all images of `uid` into `path`
    -r         to start interactive REPL mode
  """)

@main
def main(args: String*): Unit =
  args match
    case _ if args.size == 0 =>
      val argsStr = System.getenv("WB_DL_ARGS")
      argsStr match
        case _ if argsStr != null && argsStr.length() != 0 =>
          val argsFromEnv = argsStr.split(" ")
          wrappedMain(argsFromEnv*)
        case _ => logHelp(Seq("empty char"))
    case _ => wrappedMain(args*)

def wrappedMain(args: String*): Unit =
  args match
    case Seq("-w", dir) => run(dir)
    case Seq("-s", dir) =>
      scheduler.scheduleWithFixedDelay(() => run(dir), 0, 1, TimeUnit.DAYS)
    case Seq("-u", uid, dir) =>
      val p = util.path(dir)
      if !os.exists(p) then throw FileNotFoundException(s"$p does not exists")
      val cookies = sinaVisitorSystem
      getAlbum(uid, p, cookies)
    case Seq("-r") => runRepl()
    case _ => logHelp(args)
