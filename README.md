## Project source

This project is build based on [OpenCalc](https://github.com/clementwzk/OpenCalc)

## License

This project is licensed under [GPLv3](/LICENSE)

## Local configuration:

To build both the debug and release versions of the app, add the following entries to `local.properties`:

```properties
mudita_repo_username={your.nexus.username}
mudita_repo_password={your.nexus.password}
mudita_nexus_repo_url={your.nexus.url}
```

For the release build, additional Sentry configuration is required:
```properties
sentry_url=https://sentry.mce.one/
sentry_dsn=...
sentry_project=kompakt-calculator
sentry_org=mudita
sentry_auth_token=... // Contact your team lead to obtain the token
```
