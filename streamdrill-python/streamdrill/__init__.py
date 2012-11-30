import hmac
import hashlib
import base64
from datetime import datetime
import time
import httplib
import urllib
import json
import types

__author__ = 'streamdrill'

def datetime2millis(dt):
  return long(time.mktime(dt.timetuple())) * 1000 + long(dt.microsecond / 1000.0)

class StreamDrillException(Exception):
  def __init__(self, msg):
    self.message = msg

  def __str__(self):
    return "StreamDrillException: " + self.message

class StreamDrillClient:
  def __init__(self, host, authKey, authSecret):
    self.host = host
    self.authKey = authKey
    self.authSecret = authSecret

    self.AUTHORIZATION = "AUTHORIZATION"

  def currentTimeMillis(self):
    return datetime2millis(datetime.now())

  def sign(self, key, secret, ts, url):
    message = key + "\n" + str(ts) + "\n" + url
    d = hmac.new(secret, message, hashlib.sha1).digest()
    return base64.b64encode(d)

  def connectWithAuth(self, path, queryparams="", method="GET"):
    c = httplib.HTTPConnection(self.host)
    timestamp = self.currentTimeMillis()
    fullpath = path
    if queryparams:
      fullpath = path + "?" + queryparams
    print "Connecting to " + fullpath
    c.putrequest(method, fullpath)
    c.putheader("X-TPK-Timestamp", timestamp)
    c.putheader(self.AUTHORIZATION, "TPK %s:%s" % (self.authKey, self.sign(self.authKey, self.authSecret, timestamp, path)))
    c.endheaders()
    return c

  def readResponse(self, c):
    return c.getresponse().read()

  def create(self, trend, entity, size, timescales):
    if type(timescales) != types.StringType:
      timescales = ",".join(timescales)
    path = "/1/create/%s/%s" % (trend, entity)
    query = urllib.urlencode({"size": size, "timescales": timescales})
    c = self.connectWithAuth(path, query)
    r = c.getresponse()
    self.checkStatus(r)
    j = json.loads(r.read())
    return j[trend]

  def update(self, trend, key, timestamp=None):
    path = "/1/update/%s/%s" % (trend, key)
    if timestamp is not None:
      queryparams = urllib.urlencode({"ts": timestamp})
    else:
      queryparams = ""
    c = self.connectWithAuth(path, queryparams)
    self.checkStatus(c.getresponse())

  def query(self, trend, count, offset=0, timescale=None, filter=dict()):
    path = "/1/query/" + trend
    qp = "count=" + str(count)
    if offset:
      qp += "&offset=" + offset
    if timescale is not None:
      qp += "&timescale=" + timescale
    if filter:
      qp += "&" + urllib.urlencode(filter)

    print qp

    response = self.readResponse(self.connectWithAuth(path, qp))
    j = json.loads(response)
    print j
    return map(lambda kv: (kv["key"], kv["score"]), j)

  def checkStatus(self, response):
    status = response.status
    if status == 400:
      j = json.loads(response.read())
      raise StreamDrillException("%s: %s" % (j["error"], j["message"]))
    elif status != 200 and status != 201:
      raise StreamDrillException("Got status response %d" % status)


  def setMeta(self, trend, key, value):
    c = self.connectWithAuth("/1/meta/%s/%s" % (trend, key), urllib.urlencode({'value': value}))
    response = c.getresponse()
    self.checkStatus(response)

  def getMeta(self, trend, key):
    r = self.doSimple("/1/meta/%s/%s" % (trend, key))
    return json.loads(r)["value"]

  def doSimple(self, path, qp=""):
    c = self.connectWithAuth(path, qp)
    r = c.getresponse()
    if r.status != 200:
      raise StreamDrillException("Got status " + r.status)
    return r.read()

  def delete(self, trend):
    self.doSimple("/1/delete/%s" % trend)

  def clear(self, trend):
    self.doSimple("/1/clear/%s" % trend)

  def stream(self):
    c = httplib.HTTPConnection(self.host)
    timestamp = self.currentTimeMillis()
    path = "/1/update"
    c.putrequest("POST", path)
    c.putheader("X-TPK-Timestamp", timestamp)
    c.putheader(self.AUTHORIZATION, "TPK %s:%s" % (self.authKey, self.sign(self.authKey, self.authSecret, timestamp, path)))
    c.endheaders()
    return StreamDrillClientStream(c)

class StreamDrillClientStream:
  def __init__(self, conn):
    self.conn = conn
    self.conn.set_debuglevel(10)

  def update(self, trend, key, ts=None):
    if ts is None:
      self.conn.send("/%s/%s\n" % (trend, key))
    else:
      self.conn.send("/%s/%s?ts=%d\n" % (trend, key, datetime2millis(ts)))

  def close(self):
    r = self.conn.getresponse()
    return r.read()


key = "f9aaf865-b89a-444d-9070-38ec6666e539"
secret = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

c = StreamDrillClient("localhost:8080", key, secret)
trend = "test-trend"
apitoken = c.create(trend, "user:song", 100, "hour")
print apitoken
c.update(trend, "juhu:123")
c.update(trend, "man:456")
print c.query(trend, 5, filter={'song': 123})

c.setMeta(trend, "foo", "bar")
print c.getMeta(trend, "foo")

print c.query(trend, 5)