package util

import scala.annotation.tailrec
import org.slf4j.LoggerFactory
import java.util.concurrent.ScheduledThreadPoolExecutor
import os.Path

val log = LoggerFactory.getLogger("weibo-dl")

val scheduler = ScheduledThreadPoolExecutor(1)

def save(
    uid: String,
    dir: Path,
    filename: String,
    data: => Array[Byte]
): Int =
  if !os.exists(dir) then os.makeDir(dir)

  val dest = dir / uid
  if !os.exists(dest) then os.makeDir(dest)

  if !os.exists(dest / filename) then
    os.write(dest / filename, data)
    log.info(s"${dest / filename} saved")
  return 1

@tailrec
def retry[T](f: => T, times: Int = 0): Option[T] =
  var t: Option[T] = None
  try t = Some(f)
  catch
    case e =>
      if times > 3 then return None
      log.info(s"encountered $e, retrying...")
      Thread.sleep(times * 5_000)
      return retry(f, times + 1)
  t
