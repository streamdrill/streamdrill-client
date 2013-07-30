#!/usr/bin/env python
# encoding=utf-8

from streamdrill import StreamDrillClient

client = StreamDrillClient("http://localhost:9669")

client.create("example-trend", "key:value", 1000, ("hour", "minute"))
client.clear("example-trend")

stream = client.stream()
for i in range(1000):
    print(i)

client.close()


