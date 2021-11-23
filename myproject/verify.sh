#!/bin/sh

javac -encoding UTF-8 -cp . ticketingsystem/GenerateHistory.java
java -cp . ticketingsystem/GenerateHistory 4 1000 1 0 0 > trace
java -jar VeriLinS.jar 4 trace 1 history
