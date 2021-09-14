# Tests de charge de RabbitMQ

Ce projet contient une série de tests de production et de consommation en masse de messages
RabbitMQ.
La spécification initiale est donnée dans la fiche
[INGEXP-11951](***REMOVED***/browse/INGEXP-11951).

Les tests sont écrits en Java, sans l'aide de Spring ni de Spring Boot afin d'être au plus bas niveau possible
d'interaction avec RabbitMQ.

# Scénarios

Plusieurs scénarios de tests sont présentés.

Dans tous ces scénarios, la taille des messages est d'environ 100 ko, soit à peu près la
taille maximale de 128 ko acceptée à l'État.

Dans tous ces scénarios, l'accès à RabbitMQ est sécurisé par un UAA.

Dans tous les scénarios, le producteur se borne à construire le message et à l'envoyer à
RabbitMQ, tandis que le consommateur se borne à recevoir les messages et à émettre une
trace succinte.
Ce sont donc des producteur et consommateur légers.

## Scénario 1 : test d'endurance

On produit des messages à un rythme faible et régulier durant une longue période.

Résultat attendu : le système doit supporter 2 messages par secondes durant 8 heures.

## Scénario 2 : test de charge

On produit des messages à un rythme croissant, jusqu'à atteindre la capacité d'absorption
de messages du système.

Résultat attendu : si l'on lance chaque seconde un message de plus qu'à la seconde précédente,
le système doit supporter la charge durant 5 minutes
(charge finale : 300 messages/sec, soit 30 Mo/sec). 

## Scénario 3 : test de contrôle de la capacité de stockage

On produit à un rythme faible et régulier, mais sans les consommer, jusqu'à épuiser la capacité de stockage
de RabbitMQ.

Résultat attendu : le système doit supporter 2 messages par secondes durant 1,5 heure
(soit environ 1 Go de messages stockés).

# Construction de l'application

Lancer la commande

```mvn clean package```

pour créer le fichier JAR.

# Exécution d'un scénario

Lancer la commande

```java -jar target/rabbitmq-load.jar mdp.properties scenario.properties```

où :
- le fichier `mdp.properties` contient une seule propriété `gina.password = XXX`, où `XXX`
  est la valeur du mot de passe de l'utilisateur AD (càd Gina) renseigné dans
  l'autre fichier `scenario.properties` (propriété `gina.user`).
- le fichier `scenario.properties` contient les propriétés pour un scénario.

Des fichiers `scenario.properties` sont fournis dans les sources, à titre d'exemples.
