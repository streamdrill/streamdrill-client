/*
 * Copyright (c) 2012 TWIMPACT UG (haftungsbeschraenkt). All rights reserved.
 */

/*
 * Scala wrapper around JSONObjects
 */

package streamdrill.client

import java.text.SimpleDateFormat
import java.util.{TimeZone, Locale, Date}

class JSONObject(obj: Any) {
  def isMap = obj.isInstanceOf[java.util.Map[String, Any]]

  def toMap = obj.asInstanceOf[java.util.Map[String, Any]]

  def toArray = obj.asInstanceOf[java.util.List[Any]]

  def isArray = obj.isInstanceOf[java.util.List[Any]]

  def isBoolean = obj.isInstanceOf[Boolean]

  def toBoolean = obj.asInstanceOf[Boolean]

  def isNumber = obj.isInstanceOf[Number]

  def toNumber = obj.asInstanceOf[Number]

  def toDouble: Double = obj match {
    case s: String => s.toDouble
    case d: Double => d
    case b: java.math.BigDecimal => b.doubleValue()
    case _ => throw new ClassCastException("Cannot cast " + obj + " to Double")
  }

  def toLong: Long = obj match {
    case s: String => s.toLong
    case i: Int => i
    case l: Long => l
    case _ => throw new ClassCastException("Cannot cast " + obj + " to Long")
  }

  def toInt: Int = obj match {
    case s: String => s.toInt
    case l: Long => l.toInt
    case i: Int => i
    case _ => throw new ClassCastException("Cannot cast " + obj + " to Int")
  }

  // Introduced new String() here to make sure we get a copy and not just
  // a substring to a much larger string.
  override def toString: String = if (obj == null)
    "null"
  else
    new String(obj.toString)

  def get(key: String): JSONObject = new JSONObject(toMap.get(key))

  def getOrElse(key: String, value: Any): JSONObject = {
    val o = toMap.get(key)
    if (o == null)
      new JSONObject(value)
    else
      new JSONObject(o)
  }

  def getIf(key: String): Option[JSONObject] = {
    val o = toMap.get(key)
    if (o == null)
      None
    else
      Some(new JSONObject(o))
  }

  def get(idx: Int): JSONObject = new JSONObject(toArray.get(idx))

  def getString(key: String) = get(key).toString

  def getStringOrElse(key: String, v: String) = getOrElse(key, v).toString

  def getStringIf(key: String): Option[String] = getIf(key).map(_.toString)

  def getLong(key: String) = get(key).toLong

  def getLongOrElse(key: String, v: Long) = getOrElse(key, v).toLong

  def getLongIf(key: String): Option[Long] = getIf(key).map(_.toLong)

  def getInt(key: String) = get(key).toInt

  def getIntOrElse(key: String, v: Int) = getOrElse(key, v).toInt

  def getIntIf(key: String): Option[Int] = getIf(key).map(_.toInt)

  def getBoolean(key: String) = get(key).toBoolean

  def getBooleanOrElse(key: String, v: Boolean) = getOrElse(key, v).toBoolean

  def getBooleanIf(key: String): Option[Boolean] = getIf(key).map(_.toBoolean)

  def getArray(key: String) = get(key).toArray

  def getNumber(key: String): Number = get(key).toNumber

  def getDouble(key: String): Double = get(key).toDouble

  private val dateFormat = new ThreadLocal[SimpleDateFormat]() {
    override def initialValue() = {
      val df = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy", Locale.ENGLISH)
      df.setTimeZone(TimeZone.getTimeZone("UTC"))
      df
    }
  }

  def getDate(key: String): Date = dateFormat.get.parse(getString(key))

  def has(key: String): Boolean = toMap.containsKey(key)

  def keys: Seq[String] = if (isMap) {
    var r = Seq[String]()
    val it = toMap.keySet.iterator
    while (it.hasNext)
      r :+= it.next
    r
  } else Seq()

  def length: Int = if (isArray)
    toArray.size
  else if (isMap)
    toMap.size
  else
    0

  def ifNonEmpty(stringField: String)(block: => Unit) {
    if (has(stringField) && getStringOrElse(stringField, "") != "")
      block
  }
}
