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

[ -z "$JENKINS_HOME" ] && cd "$JENKINS_HOME"

# download slave jar
# TODO some caching mechanism with checksums
wget "${JENKINS_URL}/jnlpJars/slave.jar" -O "${JENKINS_HOME}/slave.jar"

env

RUN_OPTS="-jnlpUrl ${JENKINS_URL}/${COMPUTER_URL}/slave-agent.jnlp "
if [ ! -z "$COMPUTER_SECRET" ]; then
 RUN_OPTS+=" -secret $COMPUTER_SECRET "
fi

RUN_CMD=""
if [ ! -z "$JENKINS_USER" ] && [ x"$JENKINS_USER" != "xroot" ]; then
 RUN_CMD="su - $JENKINS_USER -c"
fi

$RUN_CMD "java -jar slave.jar $RUN_OPTS"
