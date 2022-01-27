#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
BKPDIR="$SCRIPT_DIR/../bkp"
cd "$SCRIPT_DIR"
echo "{
\"tcpPort\" : \"12345\",
\"multicastPort\" : \"6789\",
\"registryPort\" : \"1900\",
\"authorPercentage\" : \"0.6\",
\"backupDir\" : \"$BKPDIR\",
\"exclusiveLogin\" : true
}" > ../config/serverConfig.json

javac -cp "../lib/*:../src:../out" -d "../out/" ../src/*.java ../src/exceptions/*.java
java -cp "../lib/*:../out" ServerMain