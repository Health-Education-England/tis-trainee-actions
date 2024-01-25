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

| Name                    | Description                                                        | Default   |
|-------------------------|--------------------------------------------------------------------|-----------|
| ENVIRONMENT             | The environment to log events against.                             | local     |
| SENTRY_DSN              | A Sentry error monitoring Data Source Name. (Optional)             |           |


### Testing

The Gradle `test` task can be used to run automated tests and produce coverage
reports.
```shell
gradlew test
```

The Gradle `check` lifecycle task can be used to run automated tests and also
verify formatting conforms to the code style guidelines.
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
