# Tests de charge de RabbitMQ

Ce projet contient une série de tests de production et de consommation en masse de messages
RabbitMQ.
La spécification initiale est donnée dans la fiche
[INGEXP-11951](***REMOVED***/browse/INGEXP-11951).

Les tests sont écrits en Java, sans l'aide de Spring ni de Spring Boot afin d'être au plus bas niveau possible
d'interaction avec RabbitMQ.

L'accès à RabbitMQ est sécurisé par un UAA.

## Scénario 1

