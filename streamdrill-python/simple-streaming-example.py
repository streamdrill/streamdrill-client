#!/usr/bin/env python
# encoding=utf-8

# very simple example which constructs a table with three elements and randomly generates
# three numbers in the range 0..10, 0..5, 0..20 with means at

import sys
from streamdrill import StreamDrillClient

if len(sys.argv) != 3:
    print("""Usage: %s <trend-name> <column-names-sperated-by-colons>

for example:
   %s my-trend name:number:room""".replace("%s", sys.argv[0]))
    sys.exit(0)

trend = sys.argv[1]
columns = sys.argv[2]

client = StreamDrillClient("http://localhost:9669")
client.delete(trend)
client.create(trend, columns, 1000, ("hour", "minute", "second"))

stream = client.stream()

for line in sys.stdin:
    vals = line.split(",")[0:len(columns)]
    stream.update(trend, vals)

stream.close()
