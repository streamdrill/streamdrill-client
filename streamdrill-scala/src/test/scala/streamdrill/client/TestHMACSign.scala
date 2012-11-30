package streamdrill.client

import org.junit._
import Assert._

/**
 * <one line description>
 *
 * <longer description>
 *
 * User: mikio
 * Date: 11/19/12
 * Time: 12:54 PM
 */

class TestHMACSign {
  @Test
  def simpleTest() {
    val client = new StreamDrillClient("", "", "")

    val date = "Thu, 29 Nov 2012 11:55:27 +0000"
    assertEquals("HQFLtFp0dtpO6SF80Bx+f6loeTc=", client.sign("GET",date, "/1/update", "secret"))
  }
}
