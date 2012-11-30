/*
 * Copyright (c) 2011 TWIMPACT UG (haftungsbeschraenkt). All rights reserved.
 */

/*
 * Small Scala wrapper around JSONParsers.
 */

package streamdrill.client

import net.minidev.json.parser.{JSONParser => SmartParser}

class JSONParserException(ex: Exception) extends RuntimeException(ex)

/**
 * Convenience Object to parse JSON
 * (currently the SmartJSONParser is the fastest)
 */
object JSONParser extends SmartJSONParser

/**
 * Trait that is used for JSON parsing
 */
trait JSONParser {
  def parse(s: String): JSONObject
}


/**
 * JSON Parser using the smart-json library
 */
class SmartJSONParser extends JSONParser {
  private val parser = new ThreadLocal[SmartParser] {
    override def initialValue = new SmartParser(SmartParser.MODE_JSON_SIMPLE | SmartParser.IGNORE_CONTROL_CHAR)
  }

  def parse(s: String) = try {
    new JSONObject(parser.get.parse(s))
  } catch {
    case ex: Exception => throw new JSONParserException(ex)
  }
}