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

Prerequisite:

* install the streamdrill-client in your local maven repository using `mvn install` in the parent directory

1. Download the streamdrill.jar from http://streamdrill.com/
2. run streamdrill: `java -jar streamdrill.jar`
3. run the demo web app: `mvn jetty:run`
4. run the data injector: `mvn scala:run -DmainClass=streamdrill.examples.PageActionsExample`
5. open dashboard and play: http://localhost:8080/


StockMentions demo
==================

[Stock Mentions](https://raw.githubusercontent.com/thinkberg/streamdrill-client/master/examples/src/main/webapp/stocks/twistocks.png)

This demo opens a connection to the Twitter streaming API and collects information on stock mentions.
The data is dissected and put into a number of trends in streamdrill. To see what can be done with the API,
there is a javascript visualization showing connections between words and stocks.

The demo is also online available at http://play.streamdrill.com/vis/

Prerequisites:

Go to https://dev.twitter.com/ and log in using your twitter account. Then click on your avatar (top right)
and select "My applications". Create a new application and you will then have access to the required keys
and tokens used below.

1. Download the streamdrill.jar from http://streamdrill.com/
2. run streamdrill: `java -jar streamdrill.jar`
3. run the demo web app: `mvn jetty:run`
4. start the data collector: `mvn scala:run -DmainClass=streamdrill.examples.StockMentions -DaddArgs="api-key|api-secret|token|token-secret"`
5. open http://localhost:8080/stocks/

Replace the arguments with the corresponding parts from the Twitter App configuration site:

> api-key <- API key
> api-secret <- API secret
> token <- Access token
> token-secret <- Access token secret
