#!/bin/bash
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/*.java ../src/exceptions/*.java
java -cp "../lib/*:../out" ServerMain