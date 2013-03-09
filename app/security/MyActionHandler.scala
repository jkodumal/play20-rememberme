package security

import com.tersesystems.authentication._
import play.api.Logger


import play.api.mvc.{BodyParser, Result, Request, Action}

import controllers.AuthController

import models.User

/**
 * An action handler with all the dependencies resolved.
 */
object MyActionHandler extends ActionHandler[String, User]
{
  val SESSION_ID = "sessionId"

  val logger = Logger(this.getClass)

  val authenticationService = MyAuthenticationService

  val userService = models.User

  val sessionStore = MySessionStore

  val contextConverter = new ContextConverter[User]
  {
    def apply[A](request: Request[A], user: Option[User]) = security.MyContext[A](request, user)
  }

  val userIdConverter = new UserIdConverter[String]
  {
    def apply(userId: String) = userId
  }

  def apply[A](bp: BodyParser[A])(f: Request[A] => Result): Action[A] = {
    actionHandler(bp)(f)
  }

  def gotoSuspiciousAuthDetected[A](request: Request[A]): Result = {
    AuthController.suspiciousActivity(request)
  }

}
