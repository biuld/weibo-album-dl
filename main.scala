import os.Path
import os.PathConvertible.StringConvertible
import util.log
import util.scheduler
import weibo.api.getAlbum
import weibo.api.getImageWall
import weibo.api.sinaVisitorSystem

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

@main
def main(args: String*) =
  args match
    case "-w" :: dir :: _ => run(dir)
    case "-s" :: dir :: _ =>
      scheduler.scheduleAtFixedRate(() => run(dir), 0, 1, TimeUnit.DAYS)
    case "-u" :: uid :: dir :: _ =>
      val p = util.path(dir)
      if !os.exists(p) then throw FileNotFoundException(s"$p does not exists")
      val cookies = sinaVisitorSystem
      getAlbum(uid, p, cookies)
    case _ =>
      log.info(s"""
      unsupported args ${args.mkString(" ")}!
      try these blow:
        -w `path` to walk through uids inside `path`
        -s `path` to walk through uids inside `path` periodically (once a day)
        -u `uid`  to download all images of `uid`
      """)
