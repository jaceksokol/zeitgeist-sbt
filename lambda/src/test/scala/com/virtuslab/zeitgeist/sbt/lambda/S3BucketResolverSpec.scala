package com.virtuslab.zeitgeist.sbt.lambda

import java.net.InetAddress
import java.util.Date

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.{GetUserResult, User}
import com.virtuslab.zeitgeist.sbt.{SbtTest, _}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{MustMatchers, WordSpec}
import scala.util.{Failure, Success}

import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult

class S3BucketResolverSpec extends WordSpec with MustMatchers with MockFactory with SbtTest {

  "Resolving bucket name" should {
    "do nothing without placeholder" in {
      val s3Resolver = buildStubWith()
      s3Resolver.resolveBucketName(S3BucketId("test")) must be(Success(S3BucketId("test")))
      s3Resolver.resolveBucketName(S3BucketId("")) must be(Success(S3BucketId("")))
    }

    "handle correctly userName or userId" in {
      val s3Resolver = buildStubWith(userName = Option("testUser"), userId = "testId")
      s3Resolver.resolveBucketName(S3BucketId("com.test.{username}")) must be(
        Success(S3BucketId("com.test.testuser"))
      )

      s3Resolver.resolveBucketName(S3BucketId("net.{userId}.testing.project")) must be(
        Success(S3BucketId("net.testid.testing.project"))
      )

      s3Resolver.resolveBucketName(S3BucketId("net.{uSerId}.testing.{userNaME}")) must be(
        Success(S3BucketId("net.testid.testing.testuser"))
      )
    }

    "handle correctly accountId" in {
      val s3Resolver = buildStubWith(accountId = "jacek-123")

      s3Resolver.resolveBucketName(S3BucketId("com.test.project.{accountid}")) must be(
        Success(S3BucketId(s"com.test.project.jacek-123"))
      )
    }

    "handle correctly hostname" in {
      val localhost = InetAddress.getLocalHost.getHostName

      val s3Resolver = buildStubWith()
      s3Resolver.resolveBucketName(S3BucketId("com.test.project.{hostname}")) must be(
        Success(S3BucketId(s"com.test.project.${localhost.toLowerCase}"))
      )

      s3Resolver.resolveBucketName(S3BucketId("net.{HOSTNaME}.testing.project")) must be(
        Success(S3BucketId(s"net.${localhost.toLowerCase}.testing.project"))
      )
    }

    "fail is userName requested but not available" in {
      val s3Resolver = buildStubWith()
      s3Resolver.resolveBucketName(S3BucketId("test")) must be(Success(S3BucketId("test")))
      s3Resolver.resolveBucketName(S3BucketId("test.{username}")) must be(Failure(new UserNameNotAvailable))
    }

  }

  private def buildStubWith(userId: String = "", userName: Option[String] = None, accountId: String = "123456789012") = {
    val awsIamClient: AwsIAM = new AwsIAM(Region("region")) {
      override protected def buildIAMClient: AmazonIdentityManagement = {
        val client = stub[AmazonIdentityManagement]

        val user = new User("path", userName.orNull, userId, "arn:1234567", new Date)
        val result = new GetUserResult()
        result.setUser(user)
        (client.getUser _).when().returning(result)

        client
      }
    }
    val awsStsClient: AwsSTS = new AwsSTS(Region("region")) {
      override protected def buildSTSClient: AWSSecurityTokenService = {
        val client = stub[AWSSecurityTokenService]
        val result = new GetCallerIdentityResult()
          .withAccount(accountId)
          .withArn(s"arn:aws:iam::$accountId:user/Bob")
          .withUserId(userId)

        (client.getCallerIdentity _).when(*).returning(result)

        client
      }
    }
    new S3BucketResolver(awsIamClient, awsStsClient)
  }
}
