# hello

#
# TODO: Uncommit this when CI/CD pipeline is set
#[![pipeline status](https://github.com/productiveAnalytics/hello/badges/master/pipeline.svg)](https://github.com/productiveAnalytics/hello/commits/master)
#

This is a [Serverless Framework](https://serverless.com/framework/) service to demonstrate how to 
make a clean [12factor](https://12factor.net/) microservice with an easy to manage CI/CD pipeline to handle DevOps tasks.

## 12 Factor & Lambdas

### Er, make that 8 Factor, or maybe 7.5?

1.	Codebase: One codebase tracked in revision control, many deploys
2.	Dependencies: Explicitly declare and isolate dependencies
3.	Config: Store config in the environment
4.	Backing services: Treat backing services as attached resources
5.	Build, release, run: Strictly separate build and run stages
6.	~~Processes~~
7.	~~Port binding~~
8.	~~Concurrency~~
9.	Disposability: Maximize robustness with fast startup and graceful shutdown
10.	Dev/prod parity: Keep development, staging, and production as similar as possible
11.	Logs: Treat logs as event streams
12.	~~Admin processes~~

### 1. Codebase

This service uses Git for version control. This service is isolated in its own repository for 
simplicity's sake, but it could be a monorepo with other microservices. What is important here is
that our service is stored in revision control and that we can use our CI/CD pipeline to deploy it as
often as necessary.

### 2. Dependencies

Since this is a Java Gradle project all dependency management is handled by Gradle. For us to
maintain loose coupling of our microservice and to keep this microservice isolated we need to
explicitly declare our dependencies in our build.gradle file. It is important that we limit external
dependencies to minimize technical debt, however IF there is a need to include a library you have to
explicitly declare and it is clear to you and your team what external dependencies your microservice
has.

From our build.gradle file:

```groovy
dependencies {
    compile (
        'com.amazonaws:aws-lambda-java-core:1.1.0',
        'com.amazonaws:aws-lambda-java-log4j:1.0.0',
        'com.fasterxml.jackson.core:jackson-core:2.8.5',
        'com.fasterxml.jackson.core:jackson-databind:2.8.5',
        'com.fasterxml.jackson.core:jackson-annotations:2.8.5'
    )
    testCompile "org.junit.jupiter:junit-jupiter-params:5.3.2"
    testImplementation 'org.junit.jupiter:junit-jupiter-api:5.3.1'
    testRuntime 'org.junit.jupiter:junit-jupiter-engine:5.3.1'
}
```

It is clear from looking here what our external dependencies are. 

In my experience, to achieve faster cold starts and lambda execution times, it is best do limit
external libraries, resist heavy-weight frameworks, and to do as much as possible with the AWS SDK
for the language of your choice. Resist the urge to make common modules or utils to use between your
microservices, if it makes sense to do so however do make sure that they are stateless.

I know we were all taught to keep it DRY, but it's OK to cut & paste code between microservices and 
it is preferred over tight coupling.

### 3. Config

Lambda supports environment variables you can store configuration values in your Lambda runtime 
environment. You can set this either in the console, but to minimize configuration drift it is best
to configure your environment in your `serverless.yml` file. You can set these values for the whole
service or for each individual function in your service.

Here is an example of an environment variable for a lambda function:

```yaml
functions:
  hello:
    handler: com.serverless.Handler
    environment:
      MY_VAR: myVar
```

We can get this value from our lambda like so:

```java
System.getenv("MY_VAR");
```

This way it is tracked with your code in
version control. You can also import config values into your `serverless.yml` from an external
configuration file. Let's say another team wants to use your service but may have different values
for their config we can define those values in a `config.yml` file.

Here is how we load the `config.yml` into our `serverless.ym`:

```yaml
custom:
  ${file(./config.yml)}
```
Here are the values in our `config.yml`:

```yaml
APP_ID: 1552
CI_TYPE: Core
BILLING: MedicalTransactions/IMNCore
COST_CENTER: 30138
TEAM: IMNCore
```

And we can use those config values in our `serverless.yml`:

```yaml
  tags:
    APP ID: ${self:custom.APP_ID}
    Billing: ${self:custom.BILLING}
    Cost Center: ${self:custom.COST_CENTER}
    Team: ${self:custom.TEAM}
```

If another team wanted to use our serverless service all they would have to change is the
`config.yml` file. If these are values you explicitly don't want to share you can add this file to
your `.gitignore` file to make sure you don't commit them. For secrets however this is probably
insufficient. To use secrets in your Lambda environment you can use [AWS SSM Parameter Store]() to 
store and encrypt these secret values and use the AWS SDK to retrieve these values and you can also
import them into your `serverless.yml`:

```yaml
custom:
  supersecret: ${ssm:/path/to/secureparam~true}
```

### 4. Backing Services

This is the half factor, Lambda does not allow you to run another service as part of your function
execution. Typically you would access data stores or message queues via connection strings, HTTP
endpoint, or DNS name from your config, environment variables, or SSM as discussed in the Config 
section.

It is worth noting here that if your Lambda requires resources for its sole use, you can create this
backing service infrastructure in your `serverless.yml` in the Resources section.

### 5. Build, release, run

We separate these stages and automate in our CI/CD pipeline. Serverless framework makes this easy.
For example I can do all three of these tasks simply from the command line.

1. Build: `./gradlew build`
2. release: `serverless deploy`
3. run: `serverless invoke -- function hello`

And automate these steps in the CI/CD pipeline just as easily.

### 9. Disposability

Just as it is easy to deploy a Serverless Framework service quickly with `serverless deploy`, we can 
dispose of it just as easily with a `serverless remove`.

We must always be ready to kill our darlings and dispose our microservices when they are no longer
needed. Because this services is neatly contained, with its dependencies explicitly managed, and
easily deployed or destroyed, we can dispose of this service at will.

Lambda is event and trigger driven so we do not have to worry about start up times like we would
with a monolith or a container based service. We do however need to follow Lambda 
[best practices](https://docs.aws.amazon.com/lambda/latest/dg/best-practices.html) to minimize the
impact of cold-starts. 

### 10. Dev/prod Parity

Serverless Framework allows you to create different environments with the `stage` option. Because we
use the same `serverless.yml` to create the environment in every stage we can be assured that the
Lambda environment we created with `serverless deploy --stage dev` has perfect parity to the one we
create with `serverless deploy --stage prod`. This allows us to create distinct separate environments
so that we can test our service in dev or staging environments without interfering with production.

### 11. Logs

With Lambda it is sufficient to use the logging utility of the runtime language to stream logs to
CloudWatch. For example in our Java project we write logs in hour Handler like so:

```java
import org.apache.log4j.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class Handler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

  private static final Logger LOG = Logger.getLogger(Handler.class);

  @Override
  public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {
    LOG.info("received: " + input);
```

You can also tail the logs to verify that they are being generated correctly in a local or remote
invocation of our serverless function. To see the log output of a deployed hello lambda run:

`serverless invoke --function hello --log`

To tail the log can use `serverless log`:

`serverless logs -t`

### 12 Factor TL:DR

We try to maintain the 12 Factor application standards in our microservices to reduce technical debt,
clearly define and isolate our microservices and make our CI/CD pipeline as easy as possible
especially at scale. Serverless is a great tool for that :)

## Serverless Framework Services

### Service Creation

1. `serverless create --template aws-java-gradle --path hello` Creates a gradle java serverless service in `hello/`

### Gradle configuration for Java based services

Serverless framework defaults to gradle version 3.5. It's a good idea to update it to a more current
version. Run `./gradlew wrapper --gradle-version 5.2.1` or update your 
`gradle/wrapper/gradle-wrapper.properties` file `distributionUrl`:

```bash
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-5.2.1-bin.zip
```

You should also remove the wrapper task from the build.gradle file.

## Service Configuration:

Here is a the configuration for this service in two parts. `serverless.yml` is where you configure 
your serverless service. I exported some of the values for your specific configurations into a
separate `config.yml` file so that if another project wants to use this service they only need to
supply their own `config.yml` file.

### serverless.yml

```yaml
service: hello

plugins:
  - serverless-plugin-aws-alerts

custom:
  alerts:
    dashboards: true
    alarms:
      - functionThrottles
      - functionErrors
      - functionInvocations
      - functionDuration
  config: ${file(./config.yml)}

provider:
  name: aws
  runtime: java8
  stage: dev
  region: us-east-1
  role: arn:aws:iam::560395879688:role/IMNCore-Lambda
  versionFunctions: true
  deploymentBucket:
    name: imncore-chc-dev-medicaltransactions
  stackName: ${self:custom.config.TEAM}-${self:provider.stage}-${self:service}
  tags:
    APP_ID: ${self:custom.config.APP_ID}
    Description: env=${self:provider.stage}/appid=${self:custom.config.APP_ID}/team=${self:custom.config.TEAM}
    CI_TYPE: ${self:custom.config.CI_TYPE}
    Billing: ${self:custom.config.BILLING}
    COST_CENTER: ${self:custom.config.COST_CENTER}

package:
  artifact: build/distributions/hello.zip

functions:
  hello:
    name: ${self:custom.config.TEAM}-${self:provider.stage}-hello
    handler: com.serverless.Handler
    environment:
      MY_VAR: myVar
```

### config.yml

```yaml
APP_ID: 1552
CI_TYPE: Core
BILLING: MedicalTransactions/IMNCore
COST_CENTER: 30138
TEAM: IMNCore
```

## Local Testing and Debugging

### Testing 

To invoke Serverless functions locally you run the command,
`serverless invoke local --funciton [function name] `. Let's say we also wanted to pass some data to
 this local invocation we can do so with the `--data` flag like so:
 `serverless invoke local -f hello --data '{"body": "Ryan"}'`

You can see the input field of the response now contains the data input.
```
ApiGatewayResponse{statusCode=200, body='{"message":"Hello, Ryan.","input":{"body":"Ryan"}}', headers={X-Powered-By=AWS Lambda & serverless}, isBase64Encoded=false}
```

Let's say that you want to test with an actual AWS style event like an api gateway request, we can 
generate the event json with [SAM](https://aws.amazon.com/serverless/sam/) and then pass that event to our function.

`sam local generate-event apigateway aws-proxy --method POST --path hello --body '{"message": "Ryan"}' > src/test/resources/event.json`
 
 This will create this json file:
 ```json
{
  "body": "Ryan",
  "resource": "/{proxy+}",
  "path": "/hello",
  "httpMethod": "POST",
  "isBase64Encoded": true,
  "queryStringParameters": {
    "foo": "bar"
  },
  "pathParameters": {
    "proxy": "/hello"
  },
  "stageVariables": {
    "baz": "qux"
  },
  "headers": {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Encoding": "gzip, deflate, sdch",
    "Accept-Language": "en-US,en;q=0.8",
    "Cache-Control": "max-age=0",
    "CloudFront-Forwarded-Proto": "https",
    "CloudFront-Is-Desktop-Viewer": "true",
    "CloudFront-Is-Mobile-Viewer": "false",
    "CloudFront-Is-SmartTV-Viewer": "false",
    "CloudFront-Is-Tablet-Viewer": "false",
    "CloudFront-Viewer-Country": "US",
    "Host": "1234567890.execute-api.us-east-1.amazonaws.com",
    "Upgrade-Insecure-Requests": "1",
    "User-Agent": "Custom User Agent String",
    "Via": "1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)",
    "X-Amz-Cf-Id": "cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==",
    "X-Forwarded-For": "127.0.0.1, 127.0.0.2",
    "X-Forwarded-Port": "443",
    "X-Forwarded-Proto": "https"
  },
  "requestContext": {
    "accountId": "123456789012",
    "resourceId": "123456",
    "stage": "prod",
    "requestId": "c6af9ac6-7b61-11e6-9a41-93e8deadbeef",
    "requestTime": "09/Apr/2015:12:34:56 +0000",
    "requestTimeEpoch": 1428582896000,
    "identity": {
      "cognitoIdentityPoolId": null,
      "accountId": null,
      "cognitoIdentityId": null,
      "caller": null,
      "accessKey": null,
      "sourceIp": "127.0.0.1",
      "cognitoAuthenticationType": null,
      "cognitoAuthenticationProvider": null,
      "userArn": null,
      "userAgent": "Custom User Agent String",
      "user": null
    },
    "path": "/prod/hello",
    "resourcePath": "/{proxy+}",
    "httpMethod": "POST",
    "apiId": "1234567890",
    "protocol": "HTTP/1.1"
  }
}
```

Now we can use this file to test our function:

`serverless invoke local -f hello --path src/test/resources/event.json`

```
ApiGatewayResponse{statusCode=200, body='{"message":"Hello, Ryan.","input":{"body":"Ryan","resource":"/{proxy+}","path":"/path/to/resource","httpMethod":"POST","isBase64Encoded":true,"queryStringParameters":{"foo":"bar"},"pathParameters":{"proxy":"/path/to/resource"},"stageVariables":{"baz":"qux"},"headers":{"Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8","Accept-Encoding":"gzip, deflate, sdch","Accept-Language":"en-US,en;q=0.8","Cache-Control":"max-age=0","CloudFront-Forwarded-Proto":"https","CloudFront-Is-Desktop-Viewer":"true","CloudFront-Is-Mobile-Viewer":"false","CloudFront-Is-SmartTV-Viewer":"false","CloudFront-Is-Tablet-Viewer":"false","CloudFront-Viewer-Country":"US","Host":"1234567890.execute-api.us-east-1.amazonaws.com","Upgrade-Insecure-Requests":"1","User-Agent":"Custom User Agent String","Via":"1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)","X-Amz-Cf-Id":"cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==","X-Forwarded-For":"127.0.0.1, 127.0.0.2","X-Forwarded-Port":"443","X-Forwarded-Proto":"https"},"requestContext":{"accountId":"123456789012","resourceId":"123456","stage":"prod","requestId":"c6af9ac6-7b61-11e6-9a41-93e8deadbeef","requestTime":"09/Apr/2015:12:34:56 +0000","requestTimeEpoch":1428582896000,"identity":{"cognitoIdentityPoolId":null,"accountId":null,"cognitoIdentityId":null,"caller":null,"accessKey":null,"sourceIp":"127.0.0.1","cognitoAuthenticationType":null,"cognitoAuthenticationProvider":null,"userArn":null,"userAgent":"Custom User Agent String","user":null},"path":"/prod/path/to/resource","resourcePath":"/{proxy+}","httpMethod":"POST","apiId":"1234567890","protocol":"HTTP/1.1"}}}', headers={X-Powered-By=AWS Lambda & serverless}, isBase64Encoded=false}
```

### Debugging

OK so here's the rub when it comes to Java debugging with the Serverless Framework. For Python, Ruby
and Node.js there is a very nice `serverless-offline` plugin that you can use for local debugging. 
There is not currently great plugin for Java. HOWEVER if you're using IntelliJ IDEA and have docker 
and SAM installed you can use the [AWS Toolkit plugin](https://plugins.jetbrains.com/plugin/11349-aws-toolkit).

If your lambda function Handler extends `com.amazonaws.services.lambda.runtime.RequestHandler` you 
the AWS toolkit will recognize it as a Lambda handler. You can then click on the Lambda icon in the
gutter and debug the function locally.

 ![debug lambda: I know I need to make a better gif.](img/lambda-local-debug.gif)
 
## Deployment

To deploy a Serverless function you must execute the `buildZip` gradle task and then run `serverless
deploy`. This will bundle your Serverless service and copy it to your deployment bucket and build 
 your CloudFormation stack to deploy your function.

```
Serverless: Packaging service...
Serverless: Uploading CloudFormation file to S3...
Serverless: Uploading artifacts...
Serverless: Uploading service hello.zip file to S3 (1.81 MB)...
Serverless: Validating template...
Serverless: Creating Stack...
Serverless: Checking Stack create progress...
..............
Serverless: Stack create finished...
Service Information
service: hello
stage: dev
region: us-east-1
stack: my-team-dev-hello
resources: 4
api keys:
  None
endpoints:
  None
functions:
  hello: hello-dev-hello
layers:
  None
```

We can now test our lambda locally without logging in to AWS:

`serverless invoke -f hello -p src/test/resources/event.json`.

```json
{
    "statusCode": 200,
    "body": "{\"message\":\"Hello, Ryan.\",\"input\":{\"body\":\"Ryan\",\"resource\":\"/{proxy+}\",\"path\":\"/path/to/resource\",\"httpMethod\":\"POST\",\"isBase64Encoded\":true,\"queryStringParameters\":{\"foo\":\"bar\"},\"pathParameters\":{\"proxy\":\"/path/to/resource\"},\"stageVariables\":{\"baz\":\"qux\"},\"headers\":{\"Accept\":\"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8\",\"Accept-Encoding\":\"gzip, deflate, sdch\",\"Accept-Language\":\"en-US,en;q=0.8\",\"Cache-Control\":\"max-age=0\",\"CloudFront-Forwarded-Proto\":\"https\",\"CloudFront-Is-Desktop-Viewer\":\"true\",\"CloudFront-Is-Mobile-Viewer\":\"false\",\"CloudFront-Is-SmartTV-Viewer\":\"false\",\"CloudFront-Is-Tablet-Viewer\":\"false\",\"CloudFront-Viewer-Country\":\"US\",\"Host\":\"1234567890.execute-api.us-east-1.amazonaws.com\",\"Upgrade-Insecure-Requests\":\"1\",\"User-Agent\":\"Custom User Agent String\",\"Via\":\"1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront)\",\"X-Amz-Cf-Id\":\"cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==\",\"X-Forwarded-For\":\"127.0.0.1, 127.0.0.2\",\"X-Forwarded-Port\":\"443\",\"X-Forwarded-Proto\":\"https\"},\"requestContext\":{\"accountId\":\"123456789012\",\"resourceId\":\"123456\",\"stage\":\"prod\",\"requestId\":\"c6af9ac6-7b61-11e6-9a41-93e8deadbeef\",\"requestTime\":\"09/Apr/2015:12:34:56 +0000\",\"requestTimeEpoch\":1428582896000,\"identity\":{\"cognitoIdentityPoolId\":null,\"accountId\":null,\"cognitoIdentityId\":null,\"caller\":null,\"accessKey\":null,\"sourceIp\":\"127.0.0.1\",\"cognitoAuthenticationType\":null,\"cognitoAuthenticationProvider\":null,\"userArn\":null,\"userAgent\":\"Custom User Agent String\",\"user\":null},\"path\":\"/prod/path/to/resource\",\"resourcePath\":\"/{proxy+}\",\"httpMethod\":\"POST\",\"apiId\":\"1234567890\",\"protocol\":\"HTTP/1.1\"}}}",
    "headers": {
        "X-Powered-By": "AWS Lambda & serverless"
    },
    "isBase64Encoded": false
}
```

You can also set the -l flag to true to see the logging data as well:

`serverless invoke -f hello -p src/test/resources/event.json --log`

```
START RequestId: f91a9dd6-b5f2-4425-8ac3-2757db234cc9 Version: $LATEST
2019-03-14 02:59:12 <f91a9dd6-b5f2-4425-8ac3-2757db234cc9> INFO  com.serverless.Handler:18 - received: {body=Ryan, resource=/{proxy+}, path=/path/to/resource, httpMethod=POST, isBase64Encoded=true, queryStringParameters={foo=bar}, pathParameters={proxy=/path/to/resource}, stageVariables={baz=qux}, headers={Accept=text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8, Accept-Encoding=gzip, deflate, sdch, Accept-Language=en-US,en;q=0.8, Cache-Control=max-age=0, CloudFront-Forwarded-Proto=https, CloudFront-Is-Desktop-Viewer=true, CloudFront-Is-Mobile-Viewer=false, CloudFront-Is-SmartTV-Viewer=false, CloudFront-Is-Tablet-Viewer=false, CloudFront-Viewer-Country=US, Host=1234567890.execute-api.us-east-1.amazonaws.com, Upgrade-Insecure-Requests=1, User-Agent=Custom User Agent String, Via=1.1 08f323deadbeefa7af34d5feb414ce27.cloudfront.net (CloudFront), X-Amz-Cf-Id=cDehVQoZnx43VYQb9j2-nvCh-9z396Uhbp027Y2JvkCPNLmGJHqlaA==, X-Forwarded-For=127.0.0.1, 127.0.0.2, X-Forwarded-Port=443, X-Forwarded-Proto=https}, requestContext={accountId=123456789012, resourceId=123456, stage=prod, requestId=c6af9ac6-7b61-11e6-9a41-93e8deadbeef, requestTime=09/Apr/2015:12:34:56 +0000, requestTimeEpoch=1428582896000, identity={cognitoIdentityPoolId=null, accountId=null, cognitoIdentityId=null, caller=null, accessKey=null, sourceIp=127.0.0.1, cognitoAuthenticationType=null, cognitoAuthenticationProvider=null, userArn=null, userAgent=Custom User Agent String, user=null}, path=/prod/path/to/resource, resourcePath=/{proxy+}, httpMethod=POST, apiId=1234567890, protocol=HTTP/1.1}}
END RequestId: f91a9dd6-b5f2-4425-8ac3-2757db234cc9
REPORT RequestId: f91a9dd6-b5f2-4425-8ac3-2757db234cc9  Duration: 12.28 ms      Billed Duration: 100 ms         Memory Size: 1024 MB    Max Memory Used: 106 MB       Memory Size: 1024 MB    Max Memory Used: 60 MB  
```

## DevOps with GitLab CI

### CI/CD

Here is my `.gitlab-ci.yml`:

```yaml
stages:
  - build
  - test
  - deploy
  - smoke-test
  - remove
  - publish

cache:
  key: "$CI_COMMIT_REF_NAME"
  paths:
    - .gradle/wrapper
    - .gradle/caches
    - node_modules/

build:
  image: java:8-jdk
  stage: build
  only: ['branches', 'tags', 'merge_requests']
  before_script:
    - export GRADLE_USER_HOME=`pwd`/.gradle
  script: ./gradlew build
  artifacts:
    expire_in: 1 hour
    paths:
      - build/distributions/*.zip

check:
  image: java:8-jdk
  stage: test
  only: ['branches', 'tags', 'merge_requests']
  before_script:
    - export GRADLE_USER_HOME=`pwd`/.gradle
  script: ./gradlew check

deploy:
  image: node:8.10
  dependencies:
    - build
  stage: deploy
  only:
    - merge_requests
  before_script:
    - export SLS_STAGE="$(echo $CI_COMMIT_REF_NAME | tr -d '/' | tr -d '-' | tr '[:upper:]' '[:lower:]' | sed 's/feature//g')"
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless deploy --stage $SLS_STAGE

deploy:dev:
  image: node:8.10
  stage: deploy
  only:
    - develop
  before_script:
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless deploy # defaults to dev.

deploy:prod:
  image: node:8.10
  stage: deploy
  only:
    - master
  before_script:
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless deploy --stage prod

smoke-test:
  image: node:8.10
  stage: smoke-test
  only:
    - merge_requests
  before_script:
    - export SLS_STAGE="$(echo $CI_COMMIT_REF_NAME | tr -d '/' | tr -d '-' | tr '[:upper:]' '[:lower:]' | sed 's/feature//g')"
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless invoke --function hello --stage $SLS_STAGE --path src/test/resources/event.json --log

smoke-test:dev:
  image: node:8.10
  stage: smoke-test
  only:
    - develop
  before_script:
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless invoke --function hello --path src/test/resources/event.json --log

smoke-test:prod:
  image: node:8.10
  stage: smoke-test
  only:
    - master
  before_script:
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless invoke --function hello --stage prod --path src/test/resources/event.json --log

# A test to force a smoke test failure.
#smoke-test-fail:
#  image: node:8.10
#  stage: smoke-test
#  before_script:
#    - export SLS_STAGE="$(echo $CI_COMMIT_REF_NAME | tr -d '/' | tr -d '-' | tr '[:upper:]' '[:lower:]' | sed 's/feature//g')"
#    - npm i -g npm@latest serverless
#    - npm i
#  script:
#    - serverless invoke --function hell --path src/test/resources/event.json --log

undeploy:
  image: node:8.10
  dependencies:
    - build
  stage: remove
  only:
    - merge_requests
  before_script:
    - export SLS_STAGE="$(echo $CI_COMMIT_REF_NAME | tr -d '/' | tr -d '-' | tr '[:upper:]' '[:lower:]' | sed 's/feature//g')"
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless remove --stage $SLS_STAGE

# A useful sanity test to check your AWS Permissions :)
#awsTest:
#  image: python:3.7
#  stage: deploy
#  script:
#    - pip install awscli
#    - aws s3 ls


```

The CI pipeline graph is a simple build -> test -> deploy -> remove for all branches.

To keep the branch stages from colliding, i.e. if every deployment state was dev, I create stages 
for each git branch.

 ### Smoke Test

To do a simple smoke test I am going to invoke our hello function with our test `event.json` file
the same way we did to test our function locally. 

From `gitlab-ci.yml`:

```yaml
moke-test:
  image: node:8.10
  stage: smoke-test
  before_script:
    - export SLS_STAGE="$(echo $CI_COMMIT_REF_NAME | tr -d '/' | tr -d '-' | tr '[:upper:]' '[:lower:]' | sed 's/feature//g')"
    - npm i -g npm@latest serverless
    - npm i
  script:
    - serverless invoke --function hello --path src/test/resources/event.json --log
```

If this invocation returns an error it will fail the smoke-test stage. We could if we wanted to
invoke another type of test here, a shell script, rest-assured, etc. to do a more thorough test, but
for a simple quick test this will suffice.

Ideally we would also have other test suites and checks. For example we could include
[SonarQube](https://www.sonarqube.org/), [Black Duck](https://www.blackducksoftware.com/), 
or [Fortify](https://www.microfocus.com/en-us/products/static-code-analysis-sast/overview) scans of 
our code or do some load testing on our deployed application and publish those metrics to a DevOps
dashboard or as README badges, like the pipeline badge on this project.
 
### Monitoring

To add monitoring to our service I could create a dashboard with CloudFormation or Terraform, but
that's work and I'm lazy.  Instead I'm just going to install a Serverless Framework plugin, 
[serverless-plugin-aws-alerts](https://github.com/ACloudGuru/serverless-plugin-aws-alerts) from our
good friends at [acloud.guru](https://acloud.guru).

To install it:
`serverless plugin install --name serverless-plugin-aws-alerts`

And to configure it I have to modify my `serverless.yml` a bit.

```yaml
service: hello

plugins:
  - serverless-plugin-aws-alerts

custom:
  alerts:
    dashboards: true
    alarms:
      - functionThrottles
      - functionErrors
      - functionInvocations
      - functionDuration
  config: ${file(./config.yml)}
```

This will create a CloudWatch Dashboard for you:

![Dashboard](img/dashboard.png)


### Teams are in Control

Teams should be in control of their entire DevOps pipeline. This instills the team with a feeling of
ownership and makes the team more robust. One person can have "pager duty" at a time but ideally any
team member should understand the process enough to own it. This reduces dependency on one person or
and external DevOps team. We need to be able to deploy individual services on demand not when the
DevOps team has time to do it or when the team DevOps lead gets out of jury duty.

## TODO

- [ ] Do the Json base64 decoding on the event body payload.

- [ ] Optimize GitLab ci for pipeline speed/figure out caching.
- [ ] Do blue/green deployments or canary deployments with Serverless Framework Plugins.

## Done

- [x] Create hello Serverless Framework service.
- [x] Create CI/CD Pipeline on Gitlab CI, make build job in CI.
- [x] Create unit test, make test job in CI.
- [x] Make a stub event for local testing.
- [x] Create deployment job.
- [x] Create hello Serverless Framework service.
- [x] Create CI/CD Pipeline on Gitlab CI, make build job in CI.
- [x] Create unit test, make test job in CI.
- [x] Make a stub event for local testing.
- [x] Create deployment job.
- [x] Create example environment configurations.
- [x] Add a Serverless Framework plugin to build a metrics dashboard and alerts for service.
- [x] Create smoke test and smoke-test job.
- [x] Create a job to promote service to dev environment after merge .
- [x] Create job to promote service to production on merge to master.

## References

1. [Serverless Framework](https://www.serverless.com)
2. [The Twelve Factor App](https://12factor.net/)
3. [Applying the Twelve-Factor App Methodology to Serverless Applications](https://aws.amazon.com/blogs/compute/applying-the-twelve-factor-app-methodology-to-serverless-applications/)
4. [Serverless AWS Alerts Plugin](https://github.com/ACloudGuru/serverless-plugin-aws-alerts#serverless-aws-alerts-plugin)
5. [The best ways to test your serverless applications](https://medium.freecodecamp.org/the-best-ways-to-test-your-serverless-applications-40b88d6ee31e)
6. [How to Test Serverless Applications](https://serverless.com/blog/how-test-serverless-applications/)