import weibo.api.getAlbum
import util.log

@main
def main(uid: String) =
  val cnt = getAlbum(uid)
  log.info(s"$cnt intotal")
