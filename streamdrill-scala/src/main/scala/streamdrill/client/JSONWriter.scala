/*
 * Copyright (c) 2012 TWIMPACT UG (haftungsbeschraenkt). All rights reserved.
 */

package streamdrill.client

import java.text.SimpleDateFormat
import scala.collection.JavaConverters._
import java.util.{TimeZone, Date, UUID, Locale}

/**
 * A simple json serializer that serialized scala and java types to JSON.
 *
 * @author Matthias L. Jugel
 */
object JSONWriter {
  //val dateFormat = "EEE, dd MMM yyyy HH:mm:ss Z"
  val dateFormat = new ThreadLocal[SimpleDateFormat]() {
    override def initialValue() = {
      val df = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy", Locale.ENGLISH)
      df.setTimeZone(TimeZone.getTimeZone("UTC"))
      df
    }
  }

  def toJSON[T](v: T)(implicit manifest: Manifest[T]): String = {
    v match {
      case b: Boolean => if (b) "true" else "false"
      case i: Int => i.toString
      case l: Long => l.toString
      case x: Double => if(x == Double.PositiveInfinity || x == Double.NegativeInfinity) "0.0" else java.lang.Double.toString(x)
      case s: String => formatString(s)
      case d: Date => formatDate(d)
      case m: Map[String, Any] => formatMap(m)
      case m: java.util.Map[String, Any] => formatMap(m.asScala.toMap)
      case l: List[_] => formatList(l)
      case l: java.util.List[_] => formatSeq(l.asScala.toSeq)
      case s: Seq[_] => formatSeq(s)
      case st: Set[_] => formatSeq(st.toSeq)
      case a: Array[Byte] => formatByteArray(a)
      case ia: Array[Int] => formatIntArray(ia)
      case la: Array[Long] => formatLongArray(la)
      case da: Array[Double] => formatDoubleArray(da)
      case sa: Array[String] => formatStringArray(sa)
      case u: UUID => formatString(u.toString)
      case t: Product => formatSeq(t.productIterator.toSeq)
      case j: JSONObject => j match {
        case _ if(j.isMap) => toJSON(j.toMap)
        case _ if(j.isArray) => toJSON(j.toArray)
        case _ if(j.isBoolean) => toJSON(j.toBoolean)
        case _ if(j.isNumber) => toJSON(j.toNumber)
        case _ => toJSON(j.toString)
      }
      case _ => if (v == null)
        "null"
      else
        throw new IllegalArgumentException("Don't know how to format '%s' in JSON".format(v.toString))
    }
  }

  def formatString(s: String): String = {
    if (s == "\\N") {
      "null"
    } else {
      "\"" + escapeString(s) + "\""
    }
  }

  def isPrint(c: String): Boolean = """(\p{Print})""".r.findFirstIn(c).isDefined

  def escapeString(s: String): String = {
    s.map {
      case '\"' => "\\\""
      case '\\' => "\\\\"
      case c: Char if isPrint(c.toString) && c < 128 => c
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\t' => "\\t"
      case '\r' => "\\r"
      case c: Char => "\\u%04x".format(c.toInt)
    }.mkString
  }

  def formatDate(d: Date): String = {
    "\"" + dateFormat.get.format(d) + "\""
  }

  def formatMap(m: Map[String, Any]): String = {
    "{" + m.map {kv => formatString(kv._1) + ":" + toJSON(kv._2)}.mkString(",") + "}"
  }

  def formatList(l: List[Any]): String = {
    "[" + l.map {e => toJSON(e)}.mkString(",") + "]"
  }

  def formatSeq(s: Seq[Any]): String = {
    "[" + s.map {e => toJSON(e)}.mkString(",") + "]"
  }

  def formatByteArray(array: Array[Byte]): String = {
    val out: StringBuilder = new StringBuilder()
    out.append("\"")
    for (b <- array) {
      out.append("%02x" format b)
    }
    out.append("\"")
    out.toString()
  }

  def formatIntArray(array: Array[Int]): String = "[" + array.map(i => toJSON(i)).mkString(",") + "]"

  def formatLongArray(array: Array[Long]): String = "[" + array.map(i => toJSON(i)).mkString(",") + "]"

  def formatDoubleArray(array: Array[Double]): String = "[" + array.map(i => toJSON(i)).mkString(",") + "]"

  def formatStringArray(array: Array[String]): String = "[" + array.map(i => toJSON(i)).mkString(",") + "]"
}
