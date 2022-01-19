#!/bin/bash
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/Client.java ../src/ClientMain.java
java -cp "../lib:../lib/*:../out/*:../out:../src" ClientMain 