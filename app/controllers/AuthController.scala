package controllers

import play.api.mvc._
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._

import models._

import views._
import play.api.mvc.Cookie
import com.tersesystems.authentication._
import security.{MySessionStore, MyAuthenticationService}

object AuthController extends Controller with SessionSaver[String] with BaseActions {

  val logger = Logger(this.getClass)

  def sessionStore = MySessionStore

  def authenticationService = MyAuthenticationService

  val loginForm = Form(mapping(
    "email" -> email,
    "password" -> nonEmptyText,
    "rememberMe" -> boolean
  )(authenticateUser)(_ => None)
    .verifying("Invalid username or password", _.isDefined)
  )

  def login = Open {
    implicit ctx =>
      Ok(html.auth.login(loginForm))
  }

  def suspicious = Open {
    implicit ctx =>
      Ok(html.auth.suspicious())
  }

  def authenticate = Open {
    implicit ctx =>
      loginForm.bindFromRequest.fold(
        err => {
          logger.debug("Bad request: err = " + err)
          authorizationFailed(ctx)
        },
        eventOption => {
          eventOption.map { event =>
            gotoLoginSucceeded(event.userId, event.series, event.token)
          }.getOrElse {
            // No event found, something bad happened.
            logger.error("authenticate: could not log in")
            authorizationFailed(ctx)
          }
        }
      )
  }

  def logout = Open {
    implicit ctx =>
      gotoLogoutSucceeded(ctx)
  }

  def logoutSucceeded(req: RequestHeader): PlainResult = {
    logger.debug("logoutSucceeded")
    Redirect(routes.Application.index())
  }

  def suspiciousActivity(implicit req: RequestHeader): PlainResult = {
    Redirect(routes.AuthController.suspicious())
  }

  def authenticationFailed(implicit req: RequestHeader): PlainResult = {
    logger.debug("authenticationFailed: " + req)
    Redirect(routes.Application.index()) withCookies SessionCookie("access_uri", req.uri)
  }

  def gotoLoginSucceeded[A](userId: String, series: Option[Long], token: Option[Long])(implicit req: RequestHeader) = {
    logger.debug("gotoLoginSucceeded: login succeeded, userId = " + userId)
    val sessionId = saveAuthentication(userId)
    val sessionCookie = SessionCookie("sessionId", sessionId)

    val rememberMeCookie = for {
      s <- series
      t <- token
    } yield {
      val rememberMe = RememberMe(userId, s, t)
      RememberMe.encodeAsCookie(rememberMe)
    }

    // Append a session cookie here.
    val cookies: Seq[Cookie] = rememberMeCookie.toList :+ sessionCookie
    loginSucceeded(req) withCookies (cookies: _*)
  }

  def gotoLogoutSucceeded(implicit req: RequestHeader) = {
    req.session.get("sessionId") foreach {
      sessionStore.deleteSession(_)
    }
    val cookies = DiscardingCookie(RememberMe.COOKIE_NAME)
    logoutSucceeded(req).withNewSession discardingCookies (cookies)
  }

  def loginSucceeded(req: RequestHeader): PlainResult = {
    val uri = req.session.get("access_uri").getOrElse(routes.Application.index().url)
    req.session - "access_uri"
    Redirect(uri)
  }

  def authorizationFailed(req: RequestHeader): PlainResult = {
    logger.debug("authorizationFailed")
    val cookies = DiscardingCookie(RememberMe.COOKIE_NAME)
    Redirect(routes.AuthController.login()) discardingCookies (cookies) flashing (FLASH_ERROR -> "Cannot login with username/password")
  }

  def authenticateUser(email: String, password: String, rememberMe: Boolean): Option[UserAuthenticatedEvent[String]] = {
    authenticationService.authenticate(email, password, rememberMe).fold(
      fault => {
        logger.debug("authenticateUser: failed")
        None
      },
      event => Some(event)
    )
  }
}
