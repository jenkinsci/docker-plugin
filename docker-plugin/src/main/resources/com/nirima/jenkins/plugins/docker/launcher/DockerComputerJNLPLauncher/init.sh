#!/usr/bin/env bash

set -uxe

export CONFIG="/tmp/config.sh"
while [ ! -f "$CONFIG" ]; do
    echo "No config, sleeping for 1 second"
    sleep 1
done

echo "Found config file"
source "$CONFIG"
# require:
# $JENKINS_URL
# $COMPUTER_URL
# $COMPUTER_SECRET

if [ -z "$JENKINS_URL" ]; then
 echo "JENKINS_URL is not defined! Exiting."
 exit 1
fi

if [ -z "$COMPUTER_URL" ]; then
 echo "COMPUTER_URL is not defined! Exiting."
 exit 1
fi

# Jenkins not at home - use tmp
if [ ! -d "$JENKINS_HOME" ]; then
  JENKINS_HOME=/tmp
fi

[ -z "$JENKINS_HOME" ] && cd "$JENKINS_HOME"


# download slave jar
# TODO some caching mechanism with checksums

if [ -x "$(command -v curl)" ]; then
  curl -o "${JENKINS_HOME}/slave.jar" "${JENKINS_URL}/jnlpJars/slave.jar"
else
  wget "${JENKINS_URL}/jnlpJars/slave.jar" -O "${JENKINS_HOME}/slave.jar"
fi

env

RUN_OPTS="-jnlpUrl ${JENKINS_URL}/${COMPUTER_URL}/slave-agent.jnlp "
if [ ! -z "$COMPUTER_SECRET" ]; then
 RUN_OPTS+=" -secret $COMPUTER_SECRET "
fi

RUN_CMD="java -jar ${JENKINS_HOME}/slave.jar $RUN_OPTS"
if [ ! -z "$JENKINS_USER" ] && [ x"$JENKINS_USER" != "xroot" ] && [ "$JENKINS_USER" != "null" ]; then
 RUN_CMD="su - $JENKINS_USER -c '$RUN_CMD'"
fi

$RUN_CMD
