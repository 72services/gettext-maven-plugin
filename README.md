# maven-gettext-plugin
Continuation of the maven2 gettext plugin from googlecode

# Changelog

## 1.2.11

* Avoid creation of empty `message.properties` and `message_en.properties`

## 1.2.10

* support for `outputFormat=java`
* support for `sort=by-file|output`
* make `backup` configurable

# Release

    git tag ...
    git push origin master v...
    mvn -Prelease deploy
    upgate pom to next version
    git push
