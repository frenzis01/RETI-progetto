#!/bin/sh
#!/bin/bash
#TEST3 (aka stress test)
BWHT="\033[1;37m"
REG=$(tput sgr0)
TIMER=300
export BWHT

#run server in background
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/*.java ../src/exceptions/*.java
java -cp "../lib/*:../out" ServerMain &
export S_PID=$!

echo $BWHT "

    STARTING STRESS TEST
    10 Clients will run simultaneously without '-p' option for ${TIMER}s
    Server PID = $S_PID

" $REG

#"cli" : "true" is a key value
# it allows us to pass winsome cli commands through cli arguments
echo "{
    \"registryAddress\" : \"127.0.0.1\",
    \"registryPort\" : \"1900\",
    \"serverPort\" : \"12345\",
    \"serverNameLookup\" : \"winsomeServer\",
    \"serverAddress\" : \"localhost\",
    \"cli\" : \"true\"
}" > ../clientConfig.json


sleep 2

start=$SECONDS

for i in {1..10}; do
    ./spawnclients.sh &
done

sleep ${TIMER}

echo $BWHT "
    KILLING SERVER and SPAWNCLIENTS
" $REG
killall -9 spawnclients.sh > /dev/null 2>/dev/null
kill -15 $S_PID
# -9 == SIGKILL
# -2 == SIGINT
# -15 == SIGTERM
duration=$(( SECONDS - start ))

sleep 2

echo $BWHT "

    Well done! (${duration}s)

" $REG