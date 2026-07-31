#!/bin/sh
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java ${JAVA_OPTS:-} -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
