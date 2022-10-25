#!/usr/bin/env sh

CLASSPATH="../target/feedback-1.0-SNAPSHOT-shaded.jar"
LOGGING="-Djava.util.logging.config.file=./logging.properties"
USER_HOME="-Duser.home=$(pwd)"
DEBUG=""
# CONFIGPATH="config.json"
#DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"

java ${DEBUG} -cp ${CLASSPATH} ${LOGGING} ${USER_HOME} medley.simulator.Simulator $@
