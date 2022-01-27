#!/bin/bash
# Stress test

# output formatting variables
BWHT="\033[1;37m"
REG=$(tput sgr0)
TIMER=40
export BWHT

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
BKPDIR="$SCRIPT_DIR/../bkp"

cd "$SCRIPT_DIR"

# SERVER SETUP
# build server
./serverBuild.sh
# allow the same user to be logged by more than one process
echo "{
    \"backupDir\" : \"$BKPDIR\",
    \"exclusiveLogin\" : \"false\"
}" > ../config/serverConfig.json
# run server in background
java -jar ../jar/Server.jar &
export S_PID=$!


# CLIENT SETUP
# build Client.jar
./clientBuild.sh

# overwrite clientConfig.json
#"cli" : "true" is a key value
# it allows us to pass winsome cli commands through cli arguments
echo -e "{
    \"registryAddress\" : \"127.0.0.1\",
    \"registryPort\" : \"1900\",
    \"serverPort\" : \"12345\",
    \"serverNameLookup\" : \"winsomeServer\",
    \"serverAddress\" : \"localhost\",
    \"cli\" : \"true\"
}" > ../config/clientConfig.json

# wait for the server to boot
sleep 2

echo -e $BWHT "

    STARTING STRESS TEST
    20 Clients will run simultaneously without printing anything for ${TIMER}s
    Server PID = $S_PID

" $REG


start=$SECONDS

for i in {1..20}
do
    ./spawnclients.sh &
done

echo -e $BWHT "
    stressTest.sh is SLEEPING now...
" $REG

sleep ${TIMER}

echo -e $BWHT "
    KILLING SERVER and SPAWNCLIENTS
" $REG
# killall -9 spawnclients.sh > /dev/null 2>/dev/null
# redirecting kill output doesn't seem to be working sadly...
#   looks ok using sh instead of bash, but doing so would break the script
killall -9 spawnclients.sh | at now &> /dev/null
kill -15 $S_PID
# -9 == SIGKILL
# -2 == SIGINT
# -15 == SIGTERM
duration=$(( SECONDS - start ))

sleep 2

echo -e $BWHT "

    Well done! (${duration}s)

" $REG