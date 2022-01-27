#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd "$SCRIPT_DIR"
echo "Adding some parameters to clientConfig.json"

echo "{
    \"registryAddress\" : \"127.0.0.1\",
    \"registryPort\" : \"1900\",
    \"serverPort\" : \"12345\",
    \"serverNameLookup\" : \"winsomeServer\",
    \"serverAddress\" : \"localhost\",
    \"cli\" : \"false\"
}" > ../config/clientConfig.json

echo "Compiling client..."
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/Client*.java ../src/ROC*.java  ../src/VolatileWrapper.java ../src/exceptions/*.java
echo "Building Client.jar ..."
cd ../out
jar vcmf ../config/client.mf ../jar/Client.jar Client*.class ROSint.class ROCint.class ROCimp.class VolatileWrapper.class exceptions/*.class
cd "$SCRIPT_DIR"