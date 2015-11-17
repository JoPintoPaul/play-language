/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.play.language

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{WordSpec, ShouldMatchers}
import play.api.Play
import play.api.Play._
import play.api.i18n.Lang
import play.api.libs.ws.WS
import play.api.mvc.Cookie
import play.api.test._
import play.api.test.Helpers._
import uk.gov.hmrc.play.language.LanguageUtils._

class LanguageControllerSpec extends WordSpec with ShouldMatchers with PlayRunners with ScalaFutures with DefaultAwaitTimeout with IntegrationPatience {

  val fallbackKey     = "Test.language.fallbackUrl"
  val fallbackValue   = "www.gov.uk"
  val fallbackConfig  = Map(fallbackKey -> fallbackValue)

  val mockLanguageController = new LanguageController {
    override protected def fallbackURL: String = ???
    override def languageMap: Map[String, Lang] = Map("english" -> English,
                                                      "cymraeg" -> Welsh)
  }

  trait Resource { this: WithServer =>
    def resource(path: String) = WS.url(s"http://localhost:$port" + path).get().futureValue
  }

  abstract class ServerWithConfig(conf: Map[String, String] = Map.empty) extends
    WithServer(FakeApplication(additionalConfiguration = conf)) with Resource

  "ServerWithConfig" should { 

    import play.api.Play.current

    "return fallback URL when additional configuration is set" in
      new ServerWithConfig(fallbackConfig) {
        current.configuration.getString(fallbackKey).get should be (fallbackValue)
      }
  }


  "The switch to English endpoint" should {

    val englishRequest = FakeRequest("GET", "/language/english")

    "respond with a See Other (303) status when a referer is in the header." in
      new ServerWithConfig() {

        val request = englishRequest.withHeaders(REFERER -> fallbackValue)
        val res = mockLanguageController.switchToLanguage("english")(request)

        val Some(result) = res



        status(result) should be (SEE_OTHER)
      }

    "respond with a See Other (303) status when no referer is in the header." in
      new ServerWithConfig() {
        val Some(result) = route(englishRequest)
        status(result) should be (SEE_OTHER)
      }

    "set the redirect location to the value of the referer header." in
      new ServerWithConfig() {
        val request = englishRequest.withHeaders(REFERER -> fallbackValue)
        val Some(result) = route(request)
        redirectLocation(result) should be (Some(fallbackValue))
      }

    "set the redirect location to a default value when no referer is in the header and no config is defined." in
      new ServerWithConfig() {
        val Some(result) = route(englishRequest)
        redirectLocation(result) should be (Some("/"))
      }

    "set the redirect location to a default value when no referer is in the header and a config is defined." in
      new ServerWithConfig(fallbackConfig) {
        val Some(result) = route(englishRequest)
        redirectLocation(result) should be (Some(fallbackValue))
      }

    "should set the English language in a local cookie." in
      new ServerWithConfig() {
        val Some(result) = route(englishRequest)
        cookies(result).get(Play.langCookieName) match {
          case Some(c: Cookie) => c.value should be (EnglishLangCode)
          case _ => fail("PLAY_LANG cookie was not found.")
        }
      }

    "switch back to English after being switched to Welsh." in
      new ServerWithConfig() {
        val Some(result) = route(FakeRequest("GET", "/language/cymraeg"))
        cookies(result).get(Play.langCookieName) match {
          case Some(c: Cookie) => c.value should be (WelshLangCode)
          case _ => fail("PLAY_LANG cookie was not found.")
        }
        val Some(enResult) = route(englishRequest)
        cookies(enResult).get(Play.langCookieName) match {
          case Some(c: Cookie) => c.value should be (EnglishLangCode)
          case _ => fail("PLAY_LANG cookie was not found.")
        }
      }

  }

  "The switch to Welsh endpoint" should {

    val welshRequest = FakeRequest("GET", "/language/cymraeg")

    "respond with a See Other (303) status when a referer is in the header." in
      new ServerWithConfig() {
        val request = welshRequest.withHeaders(REFERER -> fallbackValue)
        val Some(result) = route(request)
        status(result) should be (SEE_OTHER)
      }

    "respond with a See Other (303) status when no referer is in the header." in
      new ServerWithConfig() {
        val Some(result) = route(welshRequest)
        status(result) should be (SEE_OTHER)
      }

    "set the redirect location to the value of the referer header." in
      new ServerWithConfig() {
        val request = welshRequest.withHeaders(REFERER -> fallbackValue)
        val Some(result) = route(request)
        redirectLocation(result) should be (Some(fallbackValue))
      }

    "set the redirect location to the default referer value when not set" in
      new ServerWithConfig() {
        val Some(result) = route(welshRequest)
        redirectLocation(result) should be (Some("/"))
      }

    "set the redirect location to a default value when no referer is in the header and a config is defined." in
      new ServerWithConfig(fallbackConfig) {
        val Some(result) = route(welshRequest)
        redirectLocation(result) should be (Some(fallbackValue))
      }

    "should set the Welsh language in a local cookie." in
      new ServerWithConfig() {
        val Some(result) = route(welshRequest)
        cookies(result).get(Play.langCookieName) match {
          case Some(c: Cookie) => c.value should be (WelshLangCode)
          case _ => fail("PLAY_LANG cookie was not found.")
        }
      }

    "switch back to Welsh after being switched to English."  in
      new ServerWithConfig() {
        val Some(result) = route(FakeRequest("GET", "/language/english"))
        cookies(result).get(Play.langCookieName) match {
          case Some(c: Cookie) => c.value should be (EnglishLangCode)
          case _ => fail("PLAY_LANG cookie was not found.")
        }
        val Some(cyResult) = route(welshRequest)
        cookies(cyResult).get(Play.langCookieName) match {
          case Some(c: Cookie) => c.value should be (WelshLangCode)
          case _ => fail("PLAY_LANG cookie was not found.")
        }
      }
  }

}