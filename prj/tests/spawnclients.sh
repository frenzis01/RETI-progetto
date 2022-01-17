#!/bin/bash
BWHT="\033[1;37m"
REG="(tput sgr0)"
CLI="java -cp \"../lib/*:../out\" ClientMain"

clients=(
    'java -cp "../lib:../lib/*:../out/*:../out:../src" ClientMain login u2 2 + follow u3 + show feed + rate post 2 1+ logout + login u3 3 + follow u2 + exit'
    'java -cp "../lib:../lib/*:../out/*:../out:../src" ClientMain login u2 2 + unfollow u3 + list following + logout + login u3 3 + list followers + exit'
    'java -cp "../lib:../lib/*:../out/*:../out:../src" ClientMain login u3 3 + post "post by 2" "test content" + wallet + wallet btc + logout + login u1 1 + show post 0 + exit'
    'java -cp "../lib:../lib/*:../out/*:../out:../src" ClientMain login u3 3 + delete post -1 + wallet + wallet btc + logout + login u1 1 + post "by 1" "second content" + exit'
    'java -cp "../lib:../lib/*:../out/*:../out:../src" ClientMain login u1 1 + delete post -1 + wallet + wallet btc + exit'
    )

while true 
do
    i=$(( RANDOM % ${#clients[@]}))
    echo ${clients[i]} $BASHPID
    ${clients[i]}
done

# i=$(( RANDOM % ${#clients[@]}))
# echo ${clients[1]}
# ${clients[1]}
