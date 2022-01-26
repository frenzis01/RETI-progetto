#!/bin/bash
javac -cp "../lib/*:../src:../out" -d "../out/" ../src/*.java ../src/exceptions/*.java
jar vcmf ../config/server.mf ../jar/Server.jar Server*.class ROSint.class ROSimp.class ROCint.class User.class Util.class Transaction.class Post.class exceptions/*.class