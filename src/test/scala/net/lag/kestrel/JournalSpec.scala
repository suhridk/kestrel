/*
 * Copyright 2009 Twitter, Inc.
 * Copyright 2009 Robey Pointer <robeypointer@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lag.kestrel

import java.io._
import com.twitter.util.{TempFolder, Time}
import org.specs.Specification

class JournalSpec extends Specification with TempFolder with TestLogging {
  "Journal" should {
    "walk" in {
      withTempFolder {
        val journal = new Journal(folderName + "/a1", false)
        journal.open()
        journal.add(QItem(Time.now, None, new Array[Byte](32), 0))
        journal.add(QItem(Time.now, None, new Array[Byte](64), 0))
        journal.add(QItem(Time.now, None, new Array[Byte](10), 0))
        journal.close()

        val journal2 = new Journal(folderName + "/a1", false)
        journal2.walk().map {
          case (item, itemsize) => item match {
            case JournalItem.Add(qitem) => qitem.data.size.toString
            case x => ""
          }
        }.mkString(",") mustEqual "32,64,10"
      }
    }

    "recover from corruption" in {
      withTempFolder {
        val journal = new Journal(folderName + "/a1", false)
        journal.open()
        journal.add(QItem(Time.now, None, new Array[Byte](32), 0))
        journal.close()

        val f = new FileOutputStream(folderName + "/a1", true)
        f.write(127)
        f.close()

        val journal2 = new Journal(folderName + "/a1", false)
        journal2.walk().map { case (item, itemsize) => item.toString }.mkString(",") must throwA[BrokenItemException]
      }
    }

    "identify valid journal files" in {
      "simple" in {
        withTempFolder {
          new FileOutputStream(folderName + "/test.50")
          new FileOutputStream(folderName + "/test.100")
          new FileOutputStream(folderName + "/test.3000")
          new FileOutputStream(folderName + "/test")
          Journal.journalsForQueue(new File(folderName), "test").toList mustEqual
            List("test.50", "test.100", "test.3000", "test")
        }
      }

      "half-finished pack" in {
        withTempFolder {
          new FileOutputStream(folderName + "/test.50")
          new FileOutputStream(folderName + "/test.100")
          new FileOutputStream(folderName + "/test.100.pack")
          new FileOutputStream(folderName + "/test.3000")
          new FileOutputStream(folderName + "/test")
          Journal.journalsForQueue(new File(folderName), "test").toList mustEqual
            List("test.100", "test.3000", "test")

          // and it should clean up after itself:
          new File(folderName + "/test").exists() mustEqual true
          new File(folderName + "/test.100.pack").exists() mustEqual false
          new File(folderName + "/test.100").exists() mustEqual true
          new File(folderName + "/test.50").exists() mustEqual false
        }
      }

      "missing last file" in {
        withTempFolder {
          new FileOutputStream(folderName + "/test.50")
          new FileOutputStream(folderName + "/test.100")
          Journal.journalsForQueue(new File(folderName), "test").toList mustEqual
            List("test.50", "test.100")
        }
      }

      "missing any files" in {
        withTempFolder {
          Journal.journalsForQueue(new File(folderName), "test").toList mustEqual
            List("test")
        }
      }

      "journalsBefore and journalAfter" in {
        withTempFolder {
          new FileOutputStream(folderName + "/test.50")
          new FileOutputStream(folderName + "/test.100")
          new FileOutputStream(folderName + "/test.999")
          new FileOutputStream(folderName + "/test.3000")
          new FileOutputStream(folderName + "/test")

          Journal.journalsBefore(new File(folderName), "test", "test.3000").toList mustEqual
            List("test.50", "test.100", "test.999")
          Journal.journalsBefore(new File(folderName), "test", "test.50").toList mustEqual Nil
          Journal.journalAfter(new File(folderName), "test", "test.100") mustEqual Some("test.999")
          Journal.journalAfter(new File(folderName), "test", "test.3000") mustEqual Some("test")
          Journal.journalAfter(new File(folderName), "test", "test") mustEqual None
        }
      }
    }
  }
}
