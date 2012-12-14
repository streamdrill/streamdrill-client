# coding=utf-8

"""
Streamdrill client for Python.

Let's you access a streamdrill instance.

Main class is StreamDrillClient.

For more information, go to http://streamdrill.com
"""

import hmac
import hashlib
import base64
from datetime import datetime
import httplib
import urllib
import json
import types
from wsgiref.handlers import format_date_time
from time import mktime
import urlparse


__author__ = 'Mikio Braun <mikio.braun@twimpact.com>, Matthias Jugel <matthias.jugel@twimpact.com>'
__copyright__ = '(c) 2012 by TWIMPACT'
__version__ = '1.0.0'


def datetime2millis(dt):
  return long(mktime(dt.timetuple())) * 1000 + long(dt.microsecond / 1000.0)

class StreamDrillException(Exception):
  """Base class for streamdrill related exceptions"""
  def __init__(self, msg):
    self.message = msg

  def __str__(self):
    return "StreamDrillException: " + self.message


class StreamDrillClient:
  """Client to connect with streamdrill

  Main methods:

  * create - create a new trend
  * update - send some event data
  * query - query trends
  * stream - open streaming connection
  * setMeta/getMeta - access meta information on trends
  """
  def __init__(self, serverUrl, authKey, authSecret):
    self.serverUrl = serverUrl
    self.url = urlparse.urlparse(serverUrl)
    self.host = self.url.netloc
    self.basepath = self.url.path
    self.authKey = authKey
    self.authSecret = authSecret

    self.AUTHORIZATION = "AUTHORIZATION"

    self.debuglevel = 0

  def _currentDate(self):
    now = datetime.now()
    stamp = mktime(now.timetuple())
    return format_date_time(stamp)

  def _sign(self, method, date, url, secret):
    message = method + "\n" + date + "\n" + url
    d = hmac.new(secret, message, hashlib.sha1).digest()
    return base64.b64encode(d)

  def _connectWithAuth(self, path, queryparams="", method="GET"):
    c = httplib.HTTPConnection(self.host)
    date = self._currentDate()
    signpath = self.basepath + path
    fullpath = self.basepath + path
    if queryparams:
      fullpath += "?" + queryparams
    if self.debuglevel > 0:
      print "Connecting to " + fullpath
    c.putrequest(method, fullpath)
    c.putheader("Date", date)
    c.putheader(self.AUTHORIZATION, "TPK %s:%s" % (self.authKey, self._sign(method, date, signpath, self.authSecret)))
    c.endheaders()
    return c

  def _readResponse(self, c):
    return c.getresponse().read()

  def create(self, trend, entities, capacity, timescales):
    """Create a new trend.

    You may also call this on an existing trend to get the apiKey

    Parameters:
      trend: name of the trend
      entities: string with ":" separated list of entities
      capacity: capacity of the trend
      timescales: string or list of strings. One of "minute", "hour", "day", or "week"."""
    if type(timescales) != types.StringType:
      timescales = ",".join(timescales)
    path = "/1/create/%s/%s" % (trend, entities)
    query = urllib.urlencode({"size": capacity, "timescales": timescales})
    c = self._connectWithAuth(path, query)
    r = c.getresponse()
    self._checkStatus(r)
    j = json.loads(r.read())
    return j[trend]

  def update(self, trend, keys, timestamp=None):
    """Send an event, update the trend.

    Parameters:
      trend: name of the trend
      keys: an array or list of keys
      timestamp: datetime object (optional)
    """
    quotedkeys = ":".join(map(lambda k: urllib.quote(k.encode('utf-8'), ""), keys))
    path = "/1/update/%s/%s" % (trend, quotedkeys)
    if timestamp is not None:
      queryparams = urllib.urlencode({"ts": datetime2millis(timestamp)})
    else:
      queryparams = ""
    c = self._connectWithAuth(path, queryparams)
    self._checkStatus(c.getresponse())

  def query(self, trend, count, offset=0, timescale=None, filter=dict()):
    """Query trends.

    Parameters:
      trend: name of the trend
      count: number of events to return

    Keyword Args:
      offset: offset from top to start (e.g. offset=10 start with the 10th most active)
      timescale: select timescale (default: first one)
      filter: a dictionary of entities and keys to fitler results
    """
    path = "/1/query/" + trend
    qp = "count=" + str(count)
    if offset:
      qp += "&offset=" + offset
    if timescale is not None:
      qp += "&timescale=" + timescale
    if filter:
      qp += "&" + urllib.urlencode(filter)

    response = self._readResponse(self._connectWithAuth(path, qp))
    j = json.loads(response)
    return map(lambda kv: (kv["keys"], kv["score"]), j)

  def _checkStatus(self, response):
    status = response.status
    if status == 400:
      r = response.read()
      j = json.loads(r)
      raise StreamDrillException("%s: %s" % (j["error"], j["message"]))
    elif status != 200 and status != 201:
      raise StreamDrillException("Got status response %d (%s)" % (status, httplib.responses[status]))


  def setMeta(self, trend, key, value):
    """Set meta information for trends

    Currently supported meta information:
      'linkTemplate': To construct a link to show in the dashboard. $1, $2 etc. are replaced with keys
    """
    c = self._connectWithAuth("/1/meta/%s/%s" % (trend, key), urllib.urlencode({'value': value}))
    response = c.getresponse()
    self._checkStatus(response)

  def getMeta(self, trend, key):
    """Read meta information for trends.

    See setMeat()"""
    r = self._doSimple("/1/meta/%s/%s" % (trend, key))
    return json.loads(r)["value"]

  def _doSimple(self, path, qp="", method="GET"):
    c = self._connectWithAuth(path, qp, method)
    r = c.getresponse()
    if r.status != 200:
      raise StreamDrillException("Got status %d (%s)" % (r.status, httplib.responses[r.status]))
    return r.read()

  def delete(self, trend):
    """Delete a trend."""
    self._doSimple("/1/delete/%s" % trend, method="DELETE")

  def clear(self, trend):
    """Remove all elements from a trend."""
    self._doSimple("/1/clear/%s" % trend, method="DELETE")

  def stream(self):
    """Start a streaming connection"""
    c = httplib.HTTPConnection(self.host)
    date = self._currentDate()
    path = self.basepath + "/1/update"
    #c.set_debuglevel(10)
    c.putrequest("POST", path)
    c.putheader("Content-type", "text/tab-separated-values")
    c.putheader("Date", date)
    c.putheader(self.AUTHORIZATION, "TPK %s:%s" % (self.authKey, self._sign("POST", date, path, self.authSecret)))
    c.putheader("Transfer-Encoding", "chunked")
    c.endheaders()
    return StreamDrillClientStream(c)

class StreamDrillClientStream:
  """Stream connection to stream drill.

  Pipe in data as a stream, don't forget to call close() at the end.
  """
  def __init__(self, conn):
    self.conn = conn

  def update(self, trend, keys, ts=None):
    """Send an update.

    Parameters:
      trend: name of the trend
      keys: array of keys to update
      ts: timestamp (as a datetime object) (optional)
    """
    if ts is None:
      text = "%s\t%s\n" % (trend, "\t".join(keys))
    else:
      text = "%s\t%s\tts=%d\n" % (trend, "\t".join(keys), datetime2millis(ts))
    l = len(text)
    self.conn.send("%x\r\n" % l)
    self.conn.send(text + "\r\n")

  def close(self):
    """Close the connection

    Returns a dict containing keys 'rate' and 'updates' telling
    you how many updates the server saw and what the rate was."""
    self.conn.send("0\r\n\r\n")
    r = self.conn.getresponse()
    return json.loads(r.read())


if __name__ == "__main__":
  #
  # run some simple tests.
  #
  key = "f9aaf865-b89a-444d-9070-38ec6666e539"
  secret = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

  c = StreamDrillClient("http://localhost:9669/blah", key, secret)
  c.debuglevel = 10
  trend = "test-trend"
  apitoken = c.create(trend, "user:song", 100, "hour")
  print apitoken
  c.update(trend, ("juhu", "123"))
  c.update(trend, ("man", "456"))
  c.update(trend, ("man", "this: is great!"))
  print c.query(trend, 5, filter={'song': 123})

  c.setMeta(trend, "foo", "bar")
  print c.getMeta(trend, "foo")

  print c.query(trend, 5)

  cs = c.stream()
  for i in range(10):
    cs.update("test-trend", ["frank", str(i)])

  print cs.close()['rate']