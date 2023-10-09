import weibo.api.getAlbum
import util.log
import util.scheduler
import java.util.concurrent.TimeUnit
import weibo.api.getImageWall
import weibo.api.sinaVistorSystem
import java.io.FileNotFoundException
import os.Path

def run(p: String) =
  val dir = os.pwd / p
  if !os.exists(dir) then throw FileNotFoundException(s"$p does not exists")
  if os.list(dir).isEmpty then
    throw FileNotFoundException(
      s"$p is empty, try to create an empty folder under $p, then name it after your uid. All the images will be downloaded into it!"
    )

  os.list(dir)
    .filter(os.isDir(_))
    .foreach(path =>
      log.info(s"fetching ${path.baseName}")
      getAlbum(path.baseName, dir)
      Thread.sleep(60_000)
    )
  log.info("finished batch")

@main
def main(switch: String, v: String) =
  switch match
    case "-w" =>
      scheduler.scheduleAtFixedRate(() => run(v), 0, 1, TimeUnit.HOURS)
    case "-u" => getAlbum(v, os.pwd / "dl")
    case _ =>
      log.info(s"""
      unsupported switch $switch!
      try these blow:
        -w path periodically watch uid folders under `path`
        -u uid download all images of uid
      """)
