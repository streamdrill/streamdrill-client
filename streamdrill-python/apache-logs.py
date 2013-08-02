#!/usr/bin/env python
# encoding=utf-8

# very simple example which constructs a table with three elements and randomly generates
# three numbers in the range 0..10, 0..5, 0..20 with means at

import sys
import re
from streamdrill import StreamDrillClient

if len(sys.argv) != 2:
    print("""Usage: %s <log-file>

for example:
   %s http-blog.mikiobraun.de.log""".replace("%s", sys.argv[0]))
    sys.exit(0)

site = "http://blog.mikiobraun.de/"
pageViews = "page-views"
referers = "referers"
visitors = "visitors"

# 65.55.215.69 - - [01/Aug/2013:00:02:07 +0200] "GET /robots.txt HTTP/1.1" 410 1129 "-" "msnbot-media/1.1 (+http://search.msn.com/msnbot.htm)"
# group 1      2 3 4                                 5                     6   7     8   9
logline = re.compile(r"([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+) (\S+) (\S+) \[([^\]]+)\] \"GET ([^\"]+) HTTP/1\.1\" ([0-9]+) ([0-9]+) \"([^\"]+)\" \"([^\"]+)\"")

client = StreamDrillClient("http://localhost:9669")
client.delete(pageViews)
client.delete(referers)
client.delete(visitors)
client.create(pageViews, "path", 1000, ("hour", "minute", "second"))
client.create(referers, "path:referer", 1000, ("hour", "minute", "second"))
client.create(visitors, "path:addr", 1000, ("hour", "minute", "second"))

stream = client.stream()

for line in open(sys.argv[1]):
    result = logline.match(line)
    if result:
        #print(result.groups())
        addr = result.group(1)
        path = result.group(5)
        referer = result.group(8)

        if path.endswith(".html"):
            print(addr, path, referer)
            stream.update(pageViews, [path])
            if referer != "-" and not referer.startswith(site):
                stream.update(referers, [path, referer])
            stream.update(visitors, [path, addr])

stream.close()