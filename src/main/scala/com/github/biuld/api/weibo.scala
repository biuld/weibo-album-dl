package com.github.biuld.api.weibo

import sttp.client4.quick.*
import ujson.Value
import scala.annotation.tailrec
import sttp.client4.DefaultSyncBackend
import sttp.client4.logging.slf4j.Slf4jLoggingBackend
import org.slf4j.LoggerFactory
import scala.collection.parallel.CollectionConverters.*
import java.util.concurrent.TimeUnit
import com.github.biuld.util.{log, save, retry, path, scheduler}
import scala.util.Try
import os.Path
import java.{util => ju}
import scala.util.Success
import java.io.FileNotFoundException

object Weibo {
  private val regx = raw"(?<=\()[\s\S]*(?=\))".r
  private val vistorUrl = "https://passport.weibo.com/visitor/visitor"
  private val genVistorUrl = "https://passport.weibo.com/visitor/genvisitor"
  // private val backend = Slf4jLoggingBackend(DefaultSyncBackend())
  private val backend = DefaultSyncBackend()

  private def getEditHis(
      uid: String,
      p: Path,
      mid: String,
      cookies: Seq[(String, String)]
  ): List[Int] =
    val resp = quickRequest
      .get(uri"https://weibo.com/ajax/statuses/editHistory?mid=$mid&page=1")
      .cookies(cookies*)
      .send(backend)

    log.info(s"getting his for mid: $mid, status: ${resp.code}")

    if !resp.is200 then return List.empty

    val body = ujson.read(resp.body).obj

    if body.contains("statuses") then
      val statuses = body("statuses").arr.toList
      for
        s <- statuses
        if s.obj.contains("pic_infos")
        s1 <- s("pic_infos").obj.values
        cnt <- download(uid, p, s1)
      yield cnt
    else List.empty

  private def genVisitor: Option[Value] =
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

  def download(uid: String, p: Path, obj: Value) =
    var cnt = for
      pid <- Try(obj("pid").str).orElse(Try(obj("pic_id").str))
      photoCnt = save(uid, p, s"${pid}.jpg", getImage(s"${pid}.jpg"))
      videoCnt = for
        ty <- Try(obj("type").str)
        if ty == "livephoto"
        url <- Try(obj("video").str)
      yield save(uid, p, s"${pid}.mov", getRawBytes(url))
    yield photoCnt :: videoCnt.toOption.toList

    cnt.getOrElse(Nil)

  def getImageWall(
      uid: String,
      p: Path,
      sinceId: String,
      cookies: Seq[(String, String)]
  ): (String, Int) =
    val resp = quickRequest
      .get(
        uri"https://weibo.com/ajax/profile/getImageWall?uid=$uid&sinceid=$sinceId"
      )
      .cookies(cookies*)
      .send(backend)

    val body = ujson.read(resp.body)

    val cnt = body("data")("list").arr
      .filter(_ != null)
      .par
      .flatMap(pic => {
        val mid = pic("mid").str
        // val his = getEditHis(uid, p, mid, cookies)

        // his ::: download(uid, p, pic)
        download(uid, p, pic)
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

  def sinaVisitorSystem: Seq[(String, String)] =
    genVisitor.toSeq.map(_("data")("tid").str).flatMap(incarnate)

  @tailrec
  def getAlbum(
      uid: String,
      p: Path,
      cookies: Seq[(String, String)],
      sinceId: String = "0",
      cnt: Int = 0
  ): Int =
    retry(getImageWall_(uid, p, sinceId, cookies)) match
      case None =>
        log.info(s"batch $sinceId failed after 3 retries")
        cnt
      case Some((nextId, c)) =>
        if nextId == "0" then c + cnt
        else getAlbum(uid, p, cookies, nextId, c + cnt)

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

  // New methods for common functionality
  def walkDirectory(dir: Path): Unit = {
    if !os.exists(dir) then throw FileNotFoundException(s"$dir does not exist")
    if os.list(dir).isEmpty then
      throw FileNotFoundException(
        s"$dir is empty, try to create an empty folder under $dir, then name it after your uid. All the images will be downloaded into it!"
      )

    val cookies = sinaVisitorSystem
    os.list(dir)
      .filter(os.isDir(_))
      .sortBy(_.baseName)
      .foreach(p =>
        log.info(s"fetching ${p.baseName}")
        val cnt = getAlbum(p.baseName, dir, cookies)
        log.info(s"${p.baseName}: $cnt in total")
        Thread.sleep(60_000)
      )
    log.info("finished walking through directory")
  }

  def downloadForUid(uid: String, dir: Path): Int = {
    if !os.exists(dir) then throw FileNotFoundException(s"$dir does not exist")
    val cookies = sinaVisitorSystem
    getAlbum(uid, dir, cookies)
  }

  def scheduleWalkDirectory(dir: Path): Unit = {
    if !os.exists(dir) then throw FileNotFoundException(s"$dir does not exist")
    com.github.biuld.util.scheduler.scheduleWithFixedDelay(() => 
      try
        val cookies = sinaVisitorSystem
        os.list(dir)
          .filter(os.isDir(_))
          .sortBy(_.baseName)
          .foreach(p =>
            log.info(s"fetching ${p.baseName}")
            val cnt = getAlbum(p.baseName, dir, cookies)
            log.info(s"${p.baseName}: $cnt in total")
            Thread.sleep(60_000)
          )
      catch
        case e: Exception => log.error(s"Error during scheduled walk: ${e.getMessage}")
    , 0, 1, TimeUnit.DAYS)
  }
}
