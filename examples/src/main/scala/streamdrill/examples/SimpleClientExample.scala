package streamdrill.examples

import java.util.Date
import streamdrill.client.StreamDrillClient
import util.Random

/**
 * This is a very simple example that shows how to set up a trend,
 * send simple updates and streaming updates and how to query a trend.
 *
 * @author Matthias L. Jugel <matthias.jugel@twimpact.com>
 */

object SimpleClientExample extends App {
  // these are our demo access key and secret
  val ACCESS_KEY = "f9aaf865-b89a-444d-9070-38ec6666e539"
  val ACCESS_SECRET = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"
  val TREND = "users"

  // create a new client to access the streamdrill instance
  val client = new StreamDrillClient("localhost:9669", ACCESS_KEY, ACCESS_SECRET)

  // create a new trend "user_retweet" with a singel entity "user" that
  // has a size of 10.000 and a single time scale of one week
  // the response is the access token for this trend and whether it's new
  val (token, isNew) = client.create(TREND, "user", 10000, Seq("week"))
  println("received access token '%s', trend is new: %s".format(token, isNew))

  // send a simple hit and run update to the trend
  println(client.update(TREND, Seq("justin")))

  // now let's try and send updates in a single connection
  println("sending update stream")
  val stream = client.stream()

  // send an update for a random name with the current time
  val names = Seq("frank", "paul", "justin", "paula", "adele")
  for (i <- 0 until 10000) {
    stream.update(TREND, Seq(names(Random.nextInt(names.length))), ts = Some(new Date()))
  }

  // the result is a json string with the number of updates and a rate of updates/s
  val (updates, rate) = stream.done()
  println("%d updates at %.2f keys/s".format(updates, rate))

  // let's query the data now (this may also be done concurrently during updating)
  val trend = client.query(TREND)
  println(trend.map(entry => "%10d - %s".format(math.round(entry._2), entry._1.mkString)).mkString("\r\n"))
}
