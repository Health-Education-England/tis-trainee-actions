# TIS Trainee Actions

## About
This services manages actions that we require a trainee to perform, such as

 - Reviewing held personal data
 - Reviewing held training data
 - Submitting required forms

## Developing

### Running

```shell
gradlew bootRun
```

#### Environmental Variables

| Name                              | Description                                             | Default   |
|-----------------------------------|---------------------------------------------------------|-----------|
| ACTION_EVENT_TOPIC                | The SNS topic to broadcast Action events.               |           |
| AWS_ENDPOINT                      | The AWS endpoint to use, used for local dev. (Optional) |           |
| AWS_XRAY_DAEMON_ADDRESS           | The AWS XRay daemon host. (Optional)                    |           |
| ENVIRONMENT                       | The environment to log events against.                  | local     |
| MONGO_DB                          | The name of the MongoDB database.                       | actions   |
| MONGO_HOST                        | The MongoDB database server host.                       | localhost |
| MONGO_PASSWORD                    | The login password for the MongoDB database.            | pwd       |
| MONGO_PORT                        | The MongoDB database server port.                       | 27017     |
| MONGO_USER                        | The login username for the MongoDB database.            | admin     |
| PROGRAMME_MEMBERSHIP_SYNCED_QUEUE | The queue URL for Programme Membership sync events.     |           |
| SENTRY_DSN                        | A Sentry error monitoring Data Source Name. (Optional)  |           |


### Testing

The Gradle `test` task can be used to run unit tests and produce coverage
reports.
```shell
gradlew test
```

The Gradle `integrationTest` task can be used to run integration tests, Docker
is a prerequisite for many integration tests.
```shell
gradlew integrationTest
```

The Gradle `check` lifecycle task can be used to run unit tests and also verify
formatting conforms to the code style guidelines.
```shell
gradlew check
```

### Building

```shell
gradlew bootBuildImage
```

## Versioning
This project uses [Semantic Versioning](semver.org).

## License
This project is license under [The MIT License (MIT)](LICENSE).
