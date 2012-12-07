#!/usr/bin/env python
# encoding=utf-8

"""
Uses the twitter module by http://mike.verdone.ca/twitter/

Easiest way to install that module is with `easy_install twitter`.

Looks at the entities in a tweet and extracts the host and path for the
links contained. Instant Twitter link trender!
"""

from twitter import *
from streamdrill import StreamDrillClient
import datetime, email.utils, sys

# alright, spammers, here's where you'll want to go... ;)
__author__ = 'Mikio Braun <mikio@twimpact.com>'

#
# main
#
if len(sys.argv) != 3:
  print """Usage:

  %s username password
  """ % sys.argv[0]
  exit(0)

# set up trends. These are the default API access codes for the demo instance.
key = "f9aaf865-b89a-444d-9070-38ec6666e539"
secret = "9e13e4ac-ad93-4c8f-a896-d5a937b84c8a"

c = StreamDrillClient("localhost:9669", key, secret)

# Create a trend.
#
# The reason we're storing host and path seperately is because this allows
# us to filter for the host.
c.create("twitter-links", "host:path", 10000, ("day", "hour", "minute"))
c.clear("twitter-links")

# This will create a link-out button linking to the real-time search page
# of Twitter for that link. Works like a charm (well, mostly)
c.setMeta("twitter-links", "linkTemplate", "http://twitter.com/search/realtime?q=http://$1$2")

def parsedate(ds):
  return datetime.datetime(*email.utils.parsedate(ds)[:6])

# Ok, extract the path and host part of the URL (by hand, I know, I know),
# and update the trend.
def analyzeurl(trend, url, ts):
  if url == None:
    return
  if url.startswith("http://"):
    start = 7
  elif url.startswith("https://"):
    start = 8
  else:
    return
  i = url[start:].find("/")
  if i == -1:
    site = url[start:]
    path = "/"
  else:
    site = url[start:start+i]
    path = url[start+i:]
  #print("Updating %s %s at %s" % (site, path, ts))
  c.update(trend, (site, path), timestamp=ts)

#
# Main
#

# connect to Twitter
user = sys.argv[1]
password = sys.argv[2]

count = 0
updates = 0

print "Connecting to Twitter stream..."
ts = TwitterStream(auth=UserPassAuth(user, password))
for tweet in ts.statuses.sample():
  if count == 0:
    print "Ok, streaming..."
    print "Hit CTRL-C to stop!"
  count += 1
  if count % 1000 == 0:
    print "Processed %d tweets, %d links updated" % (count, updates)
  if 'entities' in tweet:
    entities = tweet['entities']
    try:
      # let's just dig into this as far as we can (and nevertheless
      # have a catch-all around it in case we've got some unexpected
      # surprises in the stream (Looking at you, Twitter!)
      now = parsedate(tweet['created_at'])
      if 'media' in entities:
        media = entities['media']
        for medium in media:
          analyzeurl("twitter-links", medium['expanded_url'], now)
          updates += 1
      if 'urls' in entities:
        urls = entities['urls']
        for url in urls:
          analyzeurl("twitter-links", url['expanded_url'], now)
          updates += 1
    except Exception as e:
      print "Got error %s for tweet %s" % (str(e), tweet)
