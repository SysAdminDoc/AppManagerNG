#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later

if [ $# -lt 2 ]; then
    echo "USAGE: ./run_server.sh <path|port> <token>"
    exit 1
fi

SERVER_NAME=
JAR_PATH=
EXEC_JAR_PATH=
%ENV_VARS%
PORT="path:$1"
TOKEN=",token:$2"
ARGS="${PORT}${ARGS}${TOKEN}"
JAR_PACKAGE_NAME="io.github.muntashirakon.AppManager"
JAR_MAIN_CLASS="${JAR_PACKAGE_NAME}.server.ServerRunner"
# Ideally, id -u could be used, but it's not supported on older platforms
# neither are commands like awk or sed, we're only left with grep.
SELF_UID=$(id | grep -oE "uid=[0-9]+" | grep -oE "[0-9]+")
SELF_GID=$(id | grep -oE "gid=[0-9]+" | grep -oE "[0-9]+")

echo "Starting $SERVER_NAME as $SELF_UID:$SELF_GID..."
if [ "${JAR_PATH}" != "${EXEC_JAR_PATH}" ]; then
    # Copy am.jar to executable directory
    cp -f ${JAR_PATH} ${EXEC_JAR_PATH}
    if [ $? -ne 0 ]; then
        # Copy failed
        echo "Error! Could not copy jar file to the executable directory."
        exit 1
    fi
    # Fix permission
    chmod 755 ${EXEC_JAR_PATH}
    chown $SELF_UID:$SELF_GID ${EXEC_JAR_PATH}
fi
# Debug log
echo "Jar path: $JAR_PATH"
# Save jar path to environment variable
export CLASSPATH=${EXEC_JAR_PATH}
# Execute local server
# Only the config: a second argument would be read as an old server's pid to kill
exec app_process /system/bin --nice-name=${SERVER_NAME} ${JAR_MAIN_CLASS} "$ARGS" &
if [ $? -ne 0 ]; then
    # Start failed
    echo "Error! Could not start local server."
    exit 1
else
    # Start success
    echo "Local server has started."
    exit 0
fi
