/*
 * Copyright (c) 2016 streamdrill UG (haftungsbeschraenkt). All rights reserved.
 */

package streamdrill.examples

import scala.io.Source
import streamdrill.client.StreamDrillClient
import scala.util.Random
import org.apache.lucene.analysis.en.EnglishAnalyzer
import scala.Array
import twitter4j._
import twitter4j.auth.AccessToken

/**
 * A stock mentions demo. It requests a twitter stream using the top NASDAQ symbols
 * and creates some sensible trends for these. Querying the different trends can reveals
 * connections between words and stock symbols that can be visualized.
 *
 * @author Matthias L. Jugel
 */
object StockMentions extends App {
  if(args.length < 4) {
    println("usage: StockMentions api-key api-secret token token-secret")
    println("       Open https://dev.twitter.com/, log in and create a new app.")
    println("       Then use the provides api keys and token here.")
    System.exit(1)
  }

  val OAUTH_API_KEY = args(0)
  val OAUTH_API_SECRET = args(1)

  // the command line arguments are the token and the token secret from Twitter
  val OAUTH_ACCESS_TOKEN = args(2)
  val OAUTH_ACCESS_SECRET = args(3)

/* If you have permission denied issues, uncomment this and double check!
  println("API key             = %s".format(OAUTH_API_KEY))
  println("API secret          = %s".format(OAUTH_API_SECRET))
  println("Access token        = %s".format(OAUTH_ACCESS_TOKEN))
  println("Access token secret = %s".format(OAUTH_ACCESS_SECRET))
*/

  // these are our demo access key and secret
  val ACCESS_KEY = "f9aaf865-b89a-444d-9070-38ec6666e539"
  val ACCESS_SECRET = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

  val BASE_URL = "http://localhost:9669"

  // create a new client to access the streamdrill instance
  val client = new StreamDrillClient(BASE_URL, ACCESS_KEY, ACCESS_SECRET)

  val NASDAQ100 = Source.fromInputStream(getClass.getResourceAsStream("/NASDAQ-100.txt")).getLines().map {
    case symbol if symbol.trim() != "" => "$%s".format(symbol)
  }.toSet

  val SP500 = Source.fromInputStream(getClass.getResourceAsStream("/sp500-symbol-list.txt")).getLines().map {
    case symbol if symbol.trim() != "" => "$%s".format(symbol)
  }.toSet

  var SYMBOLS = SP500 ++ NASDAQ100
  var removable = (SP500 &~ NASDAQ100).toList
  while (SYMBOLS.size > 400) {
    SYMBOLS = SYMBOLS - removable(Random.nextInt(removable.size))
  }

  val twitterStream = new TwitterStreamFactory().getInstance
  twitterStream.setOAuthConsumer(OAUTH_API_KEY, OAUTH_API_SECRET)
  twitterStream.setOAuthAccessToken(new AccessToken(OAUTH_ACCESS_TOKEN, OAUTH_ACCESS_SECRET))

  twitterStream.addListener(new StatusListener() {
    // create the trend
    val SYMBOL_TREND = "symbol-trend"
    val SYMBOL_MENTIONS = "symbol-mentions"
    val SYMBOL_COMBINATIONS = "symbol-combinations"
    val SYMBOL_URLS = "symbol-url"
    val SYMBOL_HASHTAGS = "symbol-hashtag"
    val SYMBOL_KEYWORDS = "symbol-keywords"

    client.create(SYMBOL_TREND, "symbol", 1000, Seq("day", "hour", "minute"))
    client.create(SYMBOL_MENTIONS, "symbol:user", 10000, Seq("day", "hour", "minute"))
    client.setMeta(SYMBOL_MENTIONS, "linkTemplate", "http://twitter.com/$2")
    client.create(SYMBOL_COMBINATIONS, "symbol:symbol2", 10000, Seq("day", "hour", "minute"))
    client.create(SYMBOL_URLS, "symbol:url", 10000, Seq("day", "hour", "minute"))
    client.setMeta(SYMBOL_URLS, "linkTemplate", "$2")
    client.create(SYMBOL_HASHTAGS, "symbol:hashtag", 10000, Seq("day", "hour", "minute"))
    client.create(SYMBOL_KEYWORDS, "symbol:keyword", 10000, Seq("day", "hour", "minute"))

    val RT = "\b([rRqQ][tT]|via)\b".r
    val USERMENTION = """\@[a-zA-Z_0-9]+""".r
    val NOTLETTERORSEP = "[^\\p{L}\\p{Z}\\p{P}]+".r
    val STCKS = """\$[a-zA-Z_0-9]+""".r
    val TEXT_CLEANER = Seq(
      RT.pattern,
      USERMENTION.pattern,
      NOTLETTERORSEP.pattern,
      STCKS.pattern
    ).mkString("|").r
    val STOPWORDS = EnglishAnalyzer.getDefaultStopSet

    val MULTSEP = "[\\p{Z}\\p{P}]+".r
    private def tokenize(text: String): Array[String] = {
      MULTSEP.replaceAllIn(TEXT_CLEANER.replaceAllIn(text, ""), " ")
          .toLowerCase.split("\\p{Space}+")
          .filter(w => w.length > 2 && !STOPWORDS.contains(w))
          .map(new String(_))
    }

    def onStatus(status: Status) {
      var text = status.getText
      val urlEntities = status.getURLEntities
      urlEntities.foreach(url => text = text.replace(url.getURL, ""))
      val words = tokenize(text)

      val symbols = text.split(" ").filter(s => s.startsWith("$") && SYMBOLS.contains(s))

      symbols.foreach {
        symbol =>
          client.update(SYMBOL_TREND, Array(symbol))
          client.update(SYMBOL_MENTIONS, Array(symbol, status.getUser.getScreenName))
          symbols.foreach {
            case ms if ms != symbol => client.update(SYMBOL_COMBINATIONS, Array(symbol, ms))
            case _ =>
          }
          urlEntities.foreach(url => client.update(SYMBOL_URLS, Array(symbol, url.getExpandedURL)))
          status.getHashtagEntities
              .foreach(hashtag => client.update(SYMBOL_HASHTAGS, Array(symbol, "#" + hashtag.getText)))
          val symbolKey = symbol.substring(1).toLowerCase
          words.foreach(w => if(symbolKey != w) client.update(SYMBOL_KEYWORDS, Array(symbol, w)))
      }
    }

    def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}

    def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {}

    def onScrubGeo(userId: Long, upToStatusId: Long) {}

    def onStallWarning(warning: StallWarning) {}

    def onException(ex: Exception) {
      ex.printStackTrace()
    }
  })

  twitterStream.filter(new FilterQuery().track(SYMBOLS.toArray:_*))
}
