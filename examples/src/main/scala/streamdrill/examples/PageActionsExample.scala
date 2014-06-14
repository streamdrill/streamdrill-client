package streamdrill.examples

import util.Random
import streamdrill.client.StreamDrillClient

/**
 *
 * @author Matthias L. Jugel <matthias.jugel@twimpact.com>
 */

object PageActionsExample extends App {

  // these are our demo access key and secret
  val ACCESS_KEY = "f9aaf865-b89a-444d-9070-38ec6666e539"
  val ACCESS_SECRET = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

  // create a new client to access the streamdrill instance
  val client = new StreamDrillClient("http://localhost:9669", ACCESS_KEY, ACCESS_SECRET)

  val timescales = Seq("week", "day", "hour", "minute")
  val pageActions = Seq("read", "post", "poll", "share-twitter", "share-fb", "share-google")
  val domains = (1 until 100).map(i => "%x.com".format(Random.nextLong()))

  // create the trend
  client.create("PageActionDomainTrend", "page:action:domain", 100000, timescales)
  client.create("PageActionTrend", "page:action", 100000, timescales)
  client.create("PageDomainTrend", "page:domain", 100000, timescales)
  client.create("ActionDomainTrend", "action:domain", 100000, timescales)
  client.create("PageTrend", "page", 100000, timescales)
  client.create("DomainTrend", "domain", 100000, timescales)
  client.create("ActionTrend", "action", 100000, timescales)

  def randomPageName = "page-%d".format(Random.nextInt(100))
  def randomPageAction = pageActions(Random.nextInt(pageActions.length))
  def randomDomain = domains(Random.nextInt(domains.length))

  println("sending updates (10k per dot)")
  val stream = client.stream()
  for (i <- 0 until 1000000) {
    if (i % 10000 == 0) print(".")
    val event = (randomDomain, randomPageName, randomPageAction)
    stream.update("PageActionDomainTrend", Seq(event._2, event._3, event._1))
    stream.update("PageActionTrend", Seq(event._2, event._3))
    stream.update("PageDomainTrend", Seq(event._2, event._1))
    stream.update("ActionDomainTrend", Seq(event._3, event._1))
    stream.update("PageTrend", Seq(event._2))
    stream.update("ActionTrend", Seq(event._3))
    stream.update("DomainTrend", Seq(event._1))
  }
  println()

  // the result is a json string with the number of updates and a rate of updates/s
  val (updates, rate) = stream.done()
  println("%d updates at %.0f keys/s".format(updates, rate))

  // let's query the data now (this may also be done concurrently during updating)
  val trend = client.query("PageActionDomainTrend")
  println(trend.map(entry => "%d - %s".format(math.round(entry._2), entry._1.mkString(":"))).mkString("\r\n"))

}
