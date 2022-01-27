Per eseguire una rapida demo utilizzare i comandi:

cd prj/jar
java -jar Server.jar

java -jar Client.jar

è importante spostarsi nella sottocartella 'jar' affinché Client e Server possano essere utilizzati correttamente senza creare/modificare file di configurazione .json



In caso l'esecuzione dei jar non vada a buon fine poiché il codice è stato compilato con una versione più recente di Java, eseguire

prj/tests/serverBuild.sh
prj/tests/clientBuild.sh

E poi ritentare l'esecuzione dei .jar
