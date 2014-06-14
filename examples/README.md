streamdrill examples
====================

These examples show how to use the streamdrill client. The main examples are in scala, but any other language should
work too.

http://demo.streamdrill.com/docs/?p=examples

HOWTO run the dashboard demo
============================

This demo is an example on how to add your own dashboard using streamdrill as a backend.
It requires a running streamdrill and will proxy requests through the demo webapp to streamdrill.
In case your streamdrill server is running on a different host or port, edit src/main/webapp/WEB-INF/web.xml

1) Download the streamdrill.jar from http://streamdrill.com/
2) run streamdrill: java -jar streamdrill.jar
3) run the demo web app: `mvn jetty:run`
4) run the data injector: `mvn scala:run -DmainClass=streamdrill.examples.PageActionsample`
5) open dashboard and play: http://localhost:8080/
