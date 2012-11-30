package streamdrill.examples

import java.io.{BufferedReader, InputStreamReader}
import java.net.{HttpURLConnection, URL}
import scala.Some
import org.apache.commons.codec.binary.Base64
import streamdrill.client.{JSONParser, StreamDrillClient}

/**
 * A simple example how to analyse retweets from Twitter.
 * This example uses our own internal proxy for retweets. In case you would like to try it alive,
 * you should try a Twitter4j-example.
 *
 * @author Matthias L. Jugel <matthias.jugel@twimpact.com>
 */

object TwitterRetweetAnalysis extends App {
  // these are our demo access key and secret
  val ACCESS_KEY = "f9aaf865-b89a-444d-9070-38ec6666e539"
  val ACCESS_SECRET = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

  // create a new client to access the streamdrill instance
  val client = new StreamDrillClient("localhost:9669", ACCESS_KEY, ACCESS_SECRET)

  // create the trend
  client.create("twitter-retweets", "user:tweetid", 100000, Seq("day", "hour", "minute"))
  // we also set a link template to directly link to the tweets
  client.setMeta("twitter-retweets", "linkTemplate", "http://twitter.com/$1/status/$2")
  val stream = client.stream()

  val auth = new String(Base64.encodeBase64("%s:%s".format(args(0), args(1)).getBytes("UTF-8")))
  val c = new URL("http://localhost:9999/1/stream/retweets/range.json?start=%s".format(args(2))).openConnection
      .asInstanceOf[HttpURLConnection]
  c.setRequestMethod("POST")
  c.setRequestProperty("Authorization", "Basic %s".format(auth))
  val reader = new BufferedReader(new InputStreamReader(c.getInputStream))

  var line = reader.readLine()
  while (null != line) {
    val jsonString = line.trim()
    if (jsonString.length() > 0) try {
      val json = JSONParser.parse(jsonString)
      if (json.has("retweeted_status")) {
        val retweet = json.get("retweeted_status")
        val user = retweet.get("user").getString("screen_name")
        val id = retweet.get("id").toString
        val now = json.getDate("created_at")
        stream.update("twitter-retweets", Seq(user, id), ts = Some(now))
      }
    } catch {
      case e: Exception => println(jsonString)
    }
    line = reader.readLine()
  }

  // the result is a json string with the number of updates and a rate of updates/s
  val (updates, rate) = stream.done()
  println("%d updates at %.0f keys/s".format(updates, rate))
}
