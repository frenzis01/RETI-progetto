#!/bin/bash

echo -e "{
    \"registryAddress\" : \"127.0.0.1\",
    \"registryPort\" : \"1900\",
    \"serverPort\" : \"12345\",
    \"serverNameLookup\" : \"winsomeServer\",
    \"serverAddress\" : \"localhost\",
    \"cli\" : \"false\"
}" > ../clientConfig.json

javac -cp "../lib/*:../src:../out" -d "../out/" ../src/Client*.java ../src/ROC*.java ../src/VolatileWrapper.java ../src/exceptions/*.java
java -cp "../lib:../lib/*:../out/*:../out:../src" ClientMain 