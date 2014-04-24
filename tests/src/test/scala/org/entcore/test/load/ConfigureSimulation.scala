package org.entcore.test.load

import io.gatling.core.Predef._
import io.gatling.core.session.Expression
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._
import io.gatling.http.Headers.Names._
import io.gatling.http.Headers.Values._
import scala.concurrent.duration._
import bootstrap._
import assertions._
import net.minidev.json.{JSONArray, JSONObject, JSONValue}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class ConfigureSimulation extends Simulation {

	val httpProtocol = http
		.baseURL("http://one")
		.acceptHeader("*/*")
		.acceptEncodingHeader("gzip, deflate")
		.acceptLanguageHeader("fr-fr,pt-br;q=0.8,en-us;q=0.6,fr;q=0.4,en;q=0.2")
		.connection("keep-alive")
		.userAgentHeader("Mozilla/5.0 (X11; Linux x86_64; rv:24.0) Gecko/20140319 Firefox/24.0 Iceweasel/24.4.0")

	val scn = scenario("Configure plateform")
		.exec(Auth.login("tom.mate", "password"))
		.pause(1)
    .exec(http("List Schools")
    .get("""/directory/api/ecole""")
    .check(status.is(200), jsonPath("status").is("ok"),
      jsonPath("$.result..id").findAll.saveAs("schoolsIds"),
      jsonPath("$.result..name").findAll.saveAs("schoolsNames")))
    .exec(http("Find roles")
    .get("""/appregistry/roles""")
    .check(status.is(200), jsonPath("status").is("ok"),
      jsonPath("$.result").find.transform(_.map{res =>
        val json = JSONValue.parse(res).asInstanceOf[JSONObject]
        json.values.asScala.foldLeft[List[String]](Nil){(acc, c) =>
          val app = c.asInstanceOf[JSONObject]
          app.get("id").asInstanceOf[String] :: acc
        }
      }).saveAs("rolesIds")))
    .foreach("${schoolsIds}", "schoolId") {
    exec(http("Find profile groups with roles")
    .get("""/appregistry/groups/roles?schoolId=${schoolId}""")
    .check(status.is(200), jsonPath("status").is("ok"),
      jsonPath("$.result").find.transform(_.map{res =>
        val json = JSONValue.parse(res).asInstanceOf[JSONObject]
        json.values.asScala.foldLeft[List[(String, String)]](Nil){(acc, c) =>
          val app = c.asInstanceOf[JSONObject]
          (app.get("id").asInstanceOf[String], app.get("name").asInstanceOf[String]) :: acc
        }
      }).saveAs("profileGroups")))
      .exec{session =>
        val schools = session("schoolsNames").as[ArrayBuffer[String]]
        val pg = session("profileGroups").as[List[(String, String)]]
        val pgu = pg.foldLeft[List[String]](Nil){(acc, c) =>
          val s = c._2.substring(0, c._2.lastIndexOf('-'))
          if (schools.contains(s)) {
            c._1::acc
          } else {
            acc
          }
        }
        session.set("profilGroupIds", pgu)
      }
      .foreach("${profilGroupIds}", "profilGroupId") {
      exec(http("Link profil groups with roles")
        .post("""/appregistry/authorize/group?schoolId=${schoolId}""")
        .param("""groupId""", """${profilGroupId}""")
        .multiValuedParam("""roleIds""", """${rolesIds}""")
        .check(status.is(200), jsonPath("status").is("ok")))
      }
    }

	setUp(scn.inject(atOnce(1 user))).protocols(httpProtocol)
}