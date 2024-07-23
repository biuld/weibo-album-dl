import weibo.api.getAlbum
import util.log
import util.scheduler
import java.util.concurrent.TimeUnit
import weibo.api.getImageWall
import weibo.api.sinaVistorSystem
import java.io.FileNotFoundException
import os.Path

def run(p: String, since: String = "0") =
  val dir = os.pwd / p
  if !os.exists(dir) then throw FileNotFoundException(s"$p does not exists")
  if os.list(dir).isEmpty then
    throw FileNotFoundException(
      s"$p is empty, try to create an empty folder under $p, then name it after your uid. All the images will be downloaded into it!"
    )

  os.list(dir)
    .filter(os.isDir(_))
    .sortBy(_.baseName)
    .dropWhile(_.baseName < since)
    .foreach(path =>
      log.info(s"fetching ${path.baseName}")
      val cnt = getAlbum(path.baseName, dir)
      log.info(s"${path.baseName}: $cnt in total")
      Thread.sleep(60_000)
    )
  log.info("finished term")

@main
def main(arg0: String, arg1: String, arg2: String) =
  arg0 match
    case "-w" =>
      run(arg1, arg2)
    case "-s" =>
      scheduler.scheduleAtFixedRate(() => run(arg1), 0, 1, TimeUnit.HOURS)
    case "-u" => getAlbum(arg1, os.pwd / "dl")
    case _ =>
      log.info(s"""
      unsupported switch $arg0!
      try these blow:
        -w path periodically watch uid folders under `path`
        -u uid download all images of uid
      """)
