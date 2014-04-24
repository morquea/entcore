package org.entcore.test.scenarios

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import bootstrap._

object ConversationScenario {

  val scn = exec(http("Login teacher")
    .post("""/auth/login""")
    .param("""email""", """${teacherLogin}""")
    .param("""password""", """blipblop""")
    .check(status.is(302)))
  .exec(http("Get conversation page")
    .get("/conversation/conversation")
    .check(status.is(200)))
  .exec(http("Find visible users or groups")
    .get("/conversation/visible")
    .check(status.is(200), jsonPath("$.groups.id").findAll.transform(_.orElse(Some(Nil)))
    .saveAs("conversationTeacherVisibleGroupId"),
    jsonPath("$.users.id").findAll.transform(_.orElse(Some(Nil)))
      .saveAs("conversationTeacherVisibleUserId")))
  .exec(http("Create draft message")
    .post("/conversation/draft")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"body":"<p><span style:\"font-family: Arial, Helvetica, sans; font-size: 10.909090995788574px; line-height: 13.991477012634277px; text-align: justify;\">Lorem ipsum dolor sit amet, consectetur adipiscing elit. In tempor sem nec lacinia tempus. Proin placerat erat dignissim sollicitudin pulvinar. Ut orci metus, malesuada nec purus at, aliquam gravida dolor. Duis faucibus tincidunt varius. Quisque congue, nulla id condimentum pellentesque, erat orci volutpat libero, vitae commodo magna sem ac enim. In sit amet arcu quis lorem congue facilisis. Maecenas eget tincidunt magna, fringilla mollis leo. Curabitur in massa at metus consectetur porta. Duis vel gravida justo. Donec id libero vel eros dapibus bibendum non vehicula magna. Praesent a nisl quis lacus interdum malesuada sed et urna. Vivamus at massa vel velit dignissim feugiat. Praesent sodales massa eu suscipit vulputate. Integer ut nulla sem. Donec id mi turpis.</span><br></p>","to":[]}"""))
    .check(status.is(201), jsonPath("$.id").find.saveAs("conversationDraftId")))
  .exec(http("Update draft")
    .put("/conversation/draft/${conversationDraftId}")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"subject":"Lorem ipsum"}"""))
    .check(status.is(200)))
  .exec(http("Send message")
    .post("/conversation/send?id=${conversationDraftId}")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"to":["${studentId}"],"cc":["${profilGroupIds(0)}"]}"""))
    .check(status.is(200), jsonPath("$.sent").find.not("0")))
  .exec(http("List outbox")
    .get("/conversation/list/OUTBOX")
    .check(status.is(200), jsonPath("$[0].id").find.is("${conversationDraftId}"),
      jsonPath("$[0].subject").find.is("Lorem ipsum")))
  .exec(http("Move sent message to trash")
    .put("/conversation/trash?id=${conversationDraftId}")
    .header("Content-Length", "0")
    .check(status.is(200)))
  .exec(http("List outbox")
    .get("/conversation/list/OUTBOX")
    .check(status.is(200), jsonPath("$[0].id").find.not("${conversationDraftId}")))
  .exec(http("List trash")
    .get("/conversation/list/TRASH")
    .check(status.is(200), jsonPath("$[0].id").find.is("${conversationDraftId}")))
  .exec(http("Delete message")
    .delete("/conversation/delete?id=${conversationDraftId}")
    .check(status.is(204)))
  .pause(1)
  .exec(http("Logout teacher user")
    .get("""/auth/logout""")
    .check(status.is(302)))
  .exec(http("Login student")
    .post("""/auth/login""")
    .param("""email""", """${studentLogin}""")
    .param("""password""", """blipblop""")
    .check(status.is(302)))
  .exec(http("List inbox message before read")
    .get("/conversation/list/INBOX")
    .check(status.is(200), jsonPath("$[0].id").find.saveAs("conversationMessageId"),
      jsonPath("$[0].unread").find.transform(_.map(u => String.valueOf(u))).is("true")))
  .exec(http("Count unread messages")
    .get("/conversation/count/INBOX?unread=true")
    .check(status.is(200), jsonPath("$.count").find.saveAs("unreadMessageNumber")))
  .exec(http("Read message")
    .get("/conversation/message/${conversationMessageId}")
    .check(status.is(200), jsonPath("$.body").find.exists, jsonPath("$.state").find.is("SENT")))
  .exec(http("Count unread messages")
    .get("/conversation/count/INBOX?unread=true")
    .check(status.is(200), jsonPath("$.count").find.transform(_.map(c => String.valueOf(Integer.valueOf(c) + 1))).is("${unreadMessageNumber}"),
      jsonPath("$.count").find.not("${unreadMessageNumber}")))
  .exec(http("List inbox message after read")
    .get("/conversation/list/INBOX")
    .check(status.is(200), jsonPath("$[0].unread").find.transform(_.map(u => String.valueOf(u))).is("false")))
  .exec(http("Student send message to teacher group")
    .post("/conversation/send")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"body":"Nop :'(","to":["${profilGroupIds(0)}"]}"""))
    .check(status.is(400), jsonPath("$.error").find.is("conversation.send.error")))
  .exec(http("Student reply to all users or groups of previous message including teacher group")
    .post("/conversation/send?In-Reply-To=${conversationMessageId}")
    .header("Content-Type", "application/json")
    .body(StringBody("""{"body":"Nop :'(","to":["${profilGroupIds(0)}"]}"""))
    .check(status.is(200)))
  .exec(http("Move initially received message to trash")
    .put("/conversation/trash?id=${conversationMessageId}")
    .header("Content-Length", "0")
    .check(status.is(200)))
  .exec(http("List trash")
    .get("/conversation/list/TRASH")
    .check(status.is(200), jsonPath("$[0].id").find.is("${conversationMessageId}")))
  .exec(http("Restore message")
    .put("/conversation/restore?id=${conversationMessageId}")
    .header("Content-Length", "0")
    .check(status.is(200)))
  .exec(http("List inbox")
    .get("/conversation/list/INBOX")
    .check(status.is(200), jsonPath("$[0].id").find.is("${conversationMessageId}")))
}