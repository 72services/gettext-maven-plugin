# maven-gettext-plugin
Continuation of the maven2 gettext plugin from googlecode

# Changelog

## 1.3.0

* Remove `POT-Creation-Date` by default from generated files. This simplifies VCS history
* `nowrap` support for `gettext`

## 1.2.11

* Avoid creation of empty `message.properties` and `message_en.properties`

## 1.2.10

* support for `outputFormat=java`
* support for `sort=by-file|output`
* make `backup` configurable

# Release

Release is automatically tagged and deployed to Maven Central via release plugin as follows:

    JAVA_HOME="$(/usr/libexec/java_home -v 1.8)" mvn release:clean release:prepare release:perform
