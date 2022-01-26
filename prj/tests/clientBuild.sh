#!/bin/bash
cd "$(dirname "$0")"
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
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/Client.java ../src/ClientMain.java
echo "Building Client.jar ..."
cd ../out
jar vcmf ../config/client.mf ../jar/Client.jar Client*.class ROSint.class ROCint.class ROCimp.class Util.class exceptions/*.class
cd "$(dirname "$0")"