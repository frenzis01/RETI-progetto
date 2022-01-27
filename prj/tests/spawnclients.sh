#!/bin/bash

clients=(
    'java -jar ../jar/Client.jar login u2 2 + follow u3 + show feed + rate post 2 1+ logout + login u3 3 + follow u2 + exit'
    'java -jar ../jar/Client.jar login u2 2 + unfollow u3 + list following + logout + login u3 3 + list followers + exit'
    'java -jar ../jar/Client.jar login u3 3 + post "post by 3" "test content" + wallet + wallet btc + logout + login u1 1 + show post 0 + exit'
    'java -jar ../jar/Client.jar login u1 1 + post "post by 1" "test content" + wallet + wallet btc + logout + login u4 4 + show post 5 + list following + exit'
    'java -jar ../jar/Client.jar login u3 3 + delete post -1 + wallet + wallet btc + logout + login u1 1 + post "by 1" "second content" + exit'
    'java -jar ../jar/Client.jar login u1 1 + delete post -1 + wallet + wallet btc + exit'
    'java -jar ../jar/Client.jar login u4 4 + follow u3 + rate post 5 1 + exit'
    'java -jar ../jar/Client.jar login u4 4 + unfollow u3 + rate post 8 -1 + unfollow u1 + exit'
    'java -jar ../jar/Client.jar login u4 4 + follow u1 + comment 10 "Comment by u4" + list followers + exit'
    'java -jar ../jar/Client.jar login u1 1 + show feed + blog + follow u4 + show post 15 + list following + unfollow u4 +exit'
    'java -jar ../jar/Client.jar login u1 1 + follow u3 + show feed + unfollow u3 + blog + wallet + exit'
    )

while true 
do
    i=$(( RANDOM % ${#clients[@]}))
    echo ${clients[i]} $BASHPID
    ${clients[i]}
done
