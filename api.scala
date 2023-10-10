package weibo.api

import sttp.client4.quick.*
import ujson.Value
import scala.annotation.tailrec
import sttp.client4.DefaultSyncBackend
import sttp.client4.logging.slf4j.Slf4jLoggingBackend
import org.slf4j.LoggerFactory
import scala.collection.parallel.CollectionConverters.*
import java.util.concurrent.TimeUnit
import util.*
import scala.util.Try
import os.Path

private val regx = raw"(?<=\()[\s\S]*(?=\))".r
private val vistorUrl = "https://passport.weibo.com/visitor/visitor"
private val genVistorUrl = "https://passport.weibo.com/visitor/genvisitor"
// private val backend = Slf4jLoggingBackend(DefaultSyncBackend())
private val backend = DefaultSyncBackend()

private def genVistor: Option[Value] =
  val resp = quickRequest
    .post(uri"$genVistorUrl")
    .body(("cb", "gen_callback"))
    .send(backend)
  regx.findFirstIn(resp.body).map(ujson.read(_))

private def incarnate(t: String): Seq[(String, String)] =
  val resp = quickRequest
    .get(uri"$vistorUrl?a=incarnate&t=$t&cb=cross_domain")
    .send(backend)

  resp.headers
    .filter(_.name == "set-cookie")
    .flatMap(_.value.split(";"))
    .map(_.split("=").map(_.trim()))
    .filter(_.length == 2)
    .map { case Array(k, v) => (k, v) }
    .filter((k, _) => Seq("SUB", "SUBP").contains(k))

private def getImageWall(
    uid: String,
    p: Path,
    sinceId: String,
    cookies: Seq[(String, String)],
    byteEater: (String, Path, String, => Array[Byte]) => Int = save
): (String, Int) =
  val resp = quickRequest
    .get(
      uri"https://weibo.com/ajax/profile/getImageWall?uid=$uid&sinceid=$sinceId"
    )
    .cookies(cookies: _*)
    .send(backend)

  val body = ujson.read(resp.body)

  val cnt = body("data")("list").arr
    .filter(_ != null)
    .par
    .flatMap(pic => {

      val pid = pic("pid").str

      val video =
        if Try(pic("type").str).filter(t => t == "livephoto").isSuccess
        then
          Some(byteEater(uid, p, s"${pid}.mov", getRawBytes(pic("video").str)))
        else None

      val photo = byteEater(uid, p, s"${pid}.jpg", getImage(s"${pid}.jpg"))

      photo :: video.toList
    })
    .sum

  val sid = body("data")("since_id") match
    case ujson.Str(value) => value
    case _                => log.info(body("bottom_tips_text").str); "0"

  (sid, cnt)

def getImageWall_(
    uid: String,
    p: Path,
    sinceId: String,
    cookies: Seq[(String, String)]
): (String, Int) =
  val start = System.nanoTime()
  val (nextId, cnt) = getImageWall(uid, p, sinceId, cookies)
  val end = System.nanoTime()
  log.info(
    s"finished batch $sinceId, $cnt files in ${TimeUnit.NANOSECONDS.toSeconds(end - start)} sec"
  )
  (nextId, cnt)

def sinaVistorSystem: Seq[(String, String)] =
  genVistor.toSeq.map(_("data")("tid").str).flatMap(incarnate(_))

@tailrec
def getAlbum(
    uid: String,
    p: Path,
    sinceId: String = "0",
    cookies: Seq[(String, String)] = sinaVistorSystem,
    cnt: Int = 0
): Int =
  retry(getImageWall_(uid, p, sinceId, cookies)) match
    case None =>
      log.info(s"batch $sinceId failed after 3 retries")
      cnt
    case Some((nextId, c)) =>
      if nextId == "0" then c + cnt
      else getAlbum(uid, p, nextId, cookies, c + cnt)

def getImage(filename: String): Array[Byte] = getRawBytes(
  s"https://wx2.sinaimg.cn/large/$filename"
)

def getRawBytes(url: String): Array[Byte] =
  quickRequest
    .get(uri"$url")
    .response(asByteArray)
    .send(backend)
    .body
    .toOption
    .get
