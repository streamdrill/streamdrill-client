package streamdrill.examples

import io.Source
import java.text.SimpleDateFormat
import streamdrill.client.StreamDrillClient
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

/**
 * A more practical example that parses an apache http log-file and streams the
 * request IP, the url/path on the server and the referer as combination to the
 * trend. The result can be used to identify trending referers, IPs or paths
 * in real-time.
 *
 * This example reads the data from a log file, but it could also be sent directly.
 *
 * @author Matthias L. Jugel <matthias.jugel@twimpact.com>
 */

object ApacheLogAnalysis extends App {
  if (args.length != 1) {
    println("usage: ApacheLogAnalysis <apache-access.log>")
    System.exit(0)
  }

  val logStream = if(args(0).endsWith(".gz")) {
    new GZIPInputStream(new FileInputStream(args(0)))
  } else {
    new FileInputStream(args(0))
  }

  // these are our demo access key and secret
  val ACCESS_KEY = "f9aaf865-b89a-444d-9070-38ec6666e539"
  val ACCESS_SECRET = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

  // our parsers for the date and the log line
  val dateParser = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z")
  val lineParser = """^((?:[^ ,]+(?:, +[^ ,]+)+)|(?:[^ ]+)) (\S+) (\S+) \[([\w:/]+\s[+\-]\d{4})\] "([A-Z]+) (.+?) HTTP/(1.\d)" (\d{3}) (-|\d+) "([^"]*)" "([^"]*)""""
      .r

  // create a new client to access the streamdrill instance
  val client = new StreamDrillClient("localhost:9669", ACCESS_KEY, ACCESS_SECRET)

  // create the trend
  client.create("apache-access-log", "host:local:referer", 100000, Seq("day", "hour", "minute"))
  val stream = client.stream()

  println("streaming log file: %s".format(args(0)))
  Source.fromInputStream(logStream).getLines().foreach {
    case lineParser(host, _, _, date, method, url, version, response, size, referer, _*) =>
      stream.update("apache-access-log", Seq(host, url, referer), ts = Some(dateParser.parse(date)))
    case l => println("not matched: '%s'".format(l))
  }

  // the result is a json string with the number of updates and a rate of updates/s
  val (updates, rate) = stream.done()
  println("%d updates at %.0f keys/s".format(updates, rate))

  // let's query the data now (this may also be done concurrently during updating)
  val trend = client.query("apache-access-log")
  println(trend.map(entry => "%d - %s".format(math.round(entry._2), entry._1.mkString(" - "))).mkString("\r\n"))
}

