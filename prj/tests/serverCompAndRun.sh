#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
cd "$SCRIPT_DIR"
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/*.java ../src/exceptions/*.java
java -cp "../lib/*:../out" ServerMain