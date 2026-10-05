#!/usr/bin/env sh

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

SCRIPT="$0"
while [ -h "$SCRIPT" ] ; do
  ls=`ls -ld "$SCRIPT"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    SCRIPT="$link"
  else
    SCRIPT=`dirname "$SCRIPT"`"/$link"
  fi
done
SAVED="`pwd`"
cd "`dirname \"$SCRIPT\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -n "$JAVA_HOME" ] ; then
  if [ -x "$JAVA_HOME/jre/bin/java" ] ; then
    JAVACMD="$JAVA_HOME/jre/bin/java"
  else
    JAVACMD="$JAVA_HOME/bin/java"
  fi
else
  JAVACMD="java"
fi

exec "$JAVACMD" \
  -Xmx64m \
  -Xms64m \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
