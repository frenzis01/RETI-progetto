#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
BKPDIR="$SCRIPT_DIR/../bkp"
cd "$(dirname "$0")"
echo "Adding some parameters to serverConfig.json"
echo "{
\"tcpPort\" : \"12345\",
\"multicastPort\" : \"6789\",
\"registryPort\" : \"1900\",
\"authorPercentage\" : \"0.6\",
\"backupDir\" : \"$BKPDIR\",
\"exclusiveLogin\" : true
}" > ../config/serverConfig.json

echo "Compiling server..."
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/*.java ../src/exceptions/*.java

echo "Building Server.jar ..."
cd ../out
jar vcmf ../config/server.mf ../jar/Server.jar Server*.class ROSint.class ROSimp.class ROCint.class User.class Transaction.class Post.class exceptions/*.class

cd $SCRIPT_DIR