package streamdrill.examples

/*
 * Copyright (c) 2012 TWIMPACT UG (haftungsbeschraenkt). All rights reserved.
 */

import io.Source
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import util.Random
import streamdrill.client.StreamDrillClient

/**
 * This example shows how to create a trend of combined entities.
 * We will send random combinations of female and male names to
 * the trend and see which combinations come up in the top 10.
 *
 * side note: the results are completely random, so don't take it
 * too seriously.
 *
 * @author Matthias L. Jugel <matthias.jugel@twimpact.com>
 */

object NameCombinationsExample extends App {

  // read the names from our file (data from US census)
  val names = Source.fromInputStream(new GZIPInputStream(new FileInputStream("data/names.csv.gz"))).getLines()
  val male = names.next().split(',')
  val female = names.next().split(',')

  // these are our demo access key and secret
  val ACCESS_KEY = "f9aaf865-b89a-444d-9070-38ec6666e539"
  val ACCESS_SECRET = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

  println("found %d female and %d male names".format(female.length, male.length))

  // create a new client to access the streamdrill instance
  val client = new StreamDrillClient("http://localhost:9669", ACCESS_KEY, ACCESS_SECRET)

  // create the trend
  client.create("combinations", "female:male", 100000, Seq("hour"))

  println("sending updates (10k per dot)")
  val stream = client.stream()
  for (i <- 0 until 1000000) {
    if (i % 10000 == 0) print(".")
    stream.update("combinations", Seq(female(Random.nextInt(female.length)), male(Random.nextInt(male.length))))
  }
  println()

  // the result is a json string with the number of updates and a rate of updates/s
  val (updates, rate) = stream.done()
  println("%d updates at %.0f keys/s".format(updates, rate))

  // let's query the data now (this may also be done concurrently during updating)
  val trend = client.query("combinations")
  println(trend.map(entry => "%d - %s".format(math.round(entry._2), entry._1.mkString("/"))).mkString("\r\n"))

}
