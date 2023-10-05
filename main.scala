import weibo.api.getAlbum

@main
def main(uid: String) =
  val cnt = getAlbum(uid)
  println(cnt)
