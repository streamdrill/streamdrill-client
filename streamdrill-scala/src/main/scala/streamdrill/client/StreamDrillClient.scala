package streamdrill.client

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac
import org.apache.commons.codec.binary.Base64
import java.net.{URLEncoder, HttpURLConnection, URL}
import io.Source
import java.net.HttpURLConnection.{HTTP_OK, HTTP_CREATED}
import grizzled.slf4j.Logging
import java.util.{TimeZone, Locale, Date}
import java.io.FileNotFoundException
import scala.collection.JavaConverters._
import java.text.{DateFormat, SimpleDateFormat}

/**
 * A streaming connection to the trend update.
 *
 * @param conn connection used for streaming the data
 */
class StreamDrillClientStream(conn: HttpURLConnection) {
  conn.addRequestProperty("Content-type", "application/json")
  conn.setChunkedStreamingMode(8192)

  private val os = conn.getOutputStream

  /**
   * Update an item
   *
   * @param trend  name of the trend
   * @param keys   sequence of keys
   * @param value  a predefined value to be used (optional)
   * @param ts     a time stamp for the object event (optional)
   */
  def update(trend: String, keys: Seq[String], value: Option[Long] = None, ts: Option[Date] = None) {
    val message = ts match {
      case Some(d) if (value.isDefined) =>
        JSONWriter.toJSON(Map("t" -> trend, "k" -> keys, "v" -> value.get, "ts" -> ts.get.getTime))
      case Some(d) if (value.isEmpty) =>
        JSONWriter.toJSON(Map("t" -> trend, "k" -> keys, "ts" -> ts.get.getTime))
      case None if (value.isDefined) =>
        JSONWriter.toJSON(Map("t" -> trend, "k" -> keys, "v" -> value.get))
      case None =>
        JSONWriter.toJSON(Map("t" -> trend, "k" -> keys))
    }
    os.write((message + "\n").getBytes("UTF-8"))
    os.flush()
  }

  /**
   * Call to close the stream
   *
   * @return some random information string, currently of the form "%d updates, %d updates/s".
   */
  def done(): (Long, Double) = {
    os.close()
    val result = JSONParser.parse(Source.fromInputStream(conn.getInputStream).mkString)
    (result.getLong("updates"), result.getDouble("rate"))
  }
}


/**
 * StreamDrill client
 */
class StreamDrillClient(host: String, apiKey: String, apiSecret: String) extends Logging {
  private val AUTHORIZATION = "Authorization"
  private val DATE_RFC1123 = new ThreadLocal[DateFormat]() {
    override def initialValue() = {
      val df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
      df.setTimeZone(TimeZone.getTimeZone("UTC"))
      df
    }
  }


  private[client] def sign(method: String, date: String, url: String, apiSecret: String) = {
    val message = method + "\n" + date + "\n" + url
    val secretKey = new SecretKeySpec(apiSecret.getBytes("UTF-8"), "HmacSHA1")
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(secretKey)
    Base64.encodeBase64String(mac.doFinal(message.getBytes("UTF-8")))
  }

  private def connectWithAuth(method: String, path: String, queryparams: String = ""): HttpURLConnection = {
    val date = DATE_RFC1123.get.format(new Date)
    val c = new URL("http://%s%s?%s".format(host, path, queryparams)).openConnection.asInstanceOf[HttpURLConnection]
    c.setRequestMethod(method)
    c.setRequestProperty("Date", date)
    c.setRequestProperty(AUTHORIZATION, "TPK %s:%s".format(apiKey, sign(method, date, path, apiSecret)))
    c
  }

  private def readResponseWithTimeouts(c: HttpURLConnection): String = {
    c.setConnectTimeout(2000)
    c.setReadTimeout(20000)
    Source.fromInputStream(c.getInputStream).getLines().mkString("\n")
  }

  private var tokens: Map[String, String] = Map()

  /**
   * Create a new table.
   *
   * @param trend   name of the table
   * @param entity  entitiy of the events
   * @param size    maximum size of the event
   * @return   A tuple of an API Token string and whether trend is new
   */
  def create(trend: String, entity: String, size: Int, timescales: Seq[String]): (String, Boolean) = {
    val path = "/1/create/%s/%s".format(trend, entity)
    val query = "size=%d&timescales=%s".format(size, URLEncoder.encode(timescales.mkString(","), "UTF-8"))
    val urlCon = connectWithAuth("GET", path, query)
    urlCon.setConnectTimeout(2000)
    urlCon.setReadTimeout(20000)

    val response = Source.fromInputStream(urlCon.getInputStream).getLines().mkString("\n")
    val status = urlCon.getResponseCode
    debug("create(%s, %s, %d) => %d, '%s'".format(trend, entity, size, status, response))
    val json = JSONParser.parse(response)
    if (status != HTTP_OK && status != HTTP_CREATED)
      throw new java.io.IOException("Return code %d on trend creation".format(status))

    val token = json.getString(trend)
    tokens += (trend -> token)

    (token, status == HTTP_CREATED)
  }

  /**
   * Hit and run update with a single HTTP GET.
   *
   * @param trend  name of the trend
   * @param keys   keys of the item to update
   * @param value  a predefined value to be used (optional)
   * @param ts     a time stamp for the object event (optional)
   * @return       return string (just some non-formatted text)
   */
  def update(trend: String, keys: Seq[String], value: Option[Long] = None, ts: Option[Date] = None) = {
    val base = "http://%s/1/update/%s/%s".format(host, trend, keys.map(URLEncoder.encode(_, "UTF-8")).mkString(":"))
    val url = new StringBuilder(base)
    ts match {
      case Some(d) if (value.isDefined) =>
        url.append("?").append("v=%d&ts=%d".format(value.get, ts.get.getTime))
      case Some(d) if (value.isEmpty) =>
        url.append("?").append("ts=%d".format(ts.get.getTime))
      case None if (value.isDefined) =>
        url.append("?").append("v=%d".format(value.get))
      case None =>
      // neither value nor timestamp declared
    }

    val urlCon = new URL(url.toString()).openConnection()
    urlCon.setConnectTimeout(2000)
    urlCon.setReadTimeout(20000)
    urlCon.setRequestProperty("Authorization", "APITOKEN " + tokens(trend))
    Source.fromInputStream(urlCon.getInputStream).getLines().mkString("\n")
  }

  /**
   * query the timescale
   */
  def query(trend: String, count: Int = 20, offset: Int = 0, timescale: Option[String] = None, filter: Map[String, String] = Map()): Seq[(Seq[String], Double)] = {
    val path = "/1/query/" + trend
    var qp = "count=" + count
    if (offset != 0) qp += "&offset=" + offset
    if (timescale.isDefined) qp += "&timescale=" + timescale.get
    if (!filter.isEmpty) qp += "&" + filter.map(kv => "%s=%s".format(kv._1, URLEncoder.encode(kv._2, "UTF-8")))
        .mkString("&")

    val c = connectWithAuth("GET", path, qp)
    val json = JSONParser.parse(readResponseWithTimeouts(c))
    (0 until json.length).map(i => (json.get(i).getArray("keys").asInstanceOf[java.util.List[String]].asScala, json.get(i).getDouble("score")))
  }

  /**
   * Stream the data to a client.
   *
   * @return a client stream object
   */
  def stream(): StreamDrillClientStream = {
    val c = connectWithAuth("POST", "/1/update".format(host))
    c.setDoOutput(true)

    new StreamDrillClientStream(c)
  }

  /**
   * Set meta-information for a trend
   *
   * @param trend name of the trend
   * @param property name of the meta-property to set
   * @param value value of the meta-property
   */
  def setMeta(trend: String, property: String, value: String) {
    val c = connectWithAuth("GET", "/1/meta/%s/%s".format(trend, property), "value=" + URLEncoder.encode(value, "UTF-8"))
    c.setConnectTimeout(2000)
    c.setReadTimeout(20000)

    Source.fromInputStream(c.getInputStream).getLines().mkString("\n") //c.disconnect()
  }

  /**
   * Get meta-information for a trend
   *
   * @param trend name of the trend
   * @param property name of the meta-property to get
   * @return returned value
   */
  def getMeta(trend: String, property: String): String = {
    val c = connectWithAuth("GET", "/1/meta/%s/%s".format(trend, property))
    c.setConnectTimeout(2000)
    c.setReadTimeout(20000)

    JSONParser.parse(Source.fromInputStream(c.getInputStream).getLines().mkString("\n")).getString("value")
  }


  def delete(trend: String) {
    try {
      val c = connectWithAuth("DELETE", "/1/delete/%s".format(trend))
      c.setRequestMethod("DELETE")
      readResponseWithTimeouts(c)
    } catch {
      case ex: FileNotFoundException => throw new NoSuchElementException("Trend %s does not exist".format(trend))
    }
  }

  def clear(trend: String) {
    try {
      val c = connectWithAuth("DELETE", "/1/clear/%s".format(trend))
      c.setRequestMethod("DELETE")
      readResponseWithTimeouts(c)
    } catch {
      case ex: FileNotFoundException => throw new NoSuchElementException("Trend %s does not exist".format(trend))
    }
  }
}