import util.log
import util.scheduler
import api.weibo.Weibo
import Repl.run as runRepl

import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

def run(p: String) =
  val dir = util.path(p)
  Weibo.walkDirectory(dir)

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
    case Seq("-s", dir) => Weibo.scheduleWalkDirectory(util.path(dir))
    case Seq("-u", uid, dir) => Weibo.downloadForUid(uid, util.path(dir))
    case Seq("-r") => runRepl()
    case _ => logHelp(args)
