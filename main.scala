import weibo.api.getAlbum
import util.log
import util.scheduler
import java.util.concurrent.TimeUnit
import weibo.api.getImageWall
import weibo.api.sinaVisitorSystem
import java.io.FileNotFoundException
import os.Path

def run(p: String, since: String = "0") =
  val dir = os.pwd / p
  if !os.exists(dir) then throw FileNotFoundException(s"$p does not exists")
  if os.list(dir).isEmpty then
    throw FileNotFoundException(
      s"$p is empty, try to create an empty folder under $p, then name it after your uid. All the images will be downloaded into it!"
    )

  val cookies = sinaVisitorSystem  

  os.list(dir)
    .filter(os.isDir(_))
    .sortBy(_.baseName)
    .dropWhile(_.baseName < since)
    .foreach(path =>
      log.info(s"fetching ${path.baseName}")
      val cnt = getAlbum(path.baseName, dir, cookies)
      log.info(s"${path.baseName}: $cnt in total")
      Thread.sleep(60_000)
    )
  log.info("finished term")

@main
def main(switch: String, dir: String, others: String*) =
  switch match
    case "-w" =>
      others match
        case _ if others.length == 0 => run(dir)
        case _                       => run(dir, others(0))
    case "-s" =>
      scheduler.scheduleAtFixedRate(() => run(dir), 0, 1, TimeUnit.HOURS)
    case "-u" => 
      val cookies = sinaVisitorSystem  
      getAlbum(dir, os.pwd / "dl", cookies)
    case _ =>
      log.info(s"""
      unsupported switch $switch!
      try these blow:
        -w path [sinceUid] to walk through ids inside path, and optionally skip ids which < sinceUid
        -s path periodically watch uid folders under `path`
        -u uid download all images of uid
      """)
