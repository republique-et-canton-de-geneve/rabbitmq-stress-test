Maven build:

[![Build with GitHub](https://github.com/republique-et-canton-de-geneve/rabbitmq-stress-test/actions/workflows/maven.yml/badge.svg)](https://github.com/republique-et-canton-de-geneve/rabbitmq-stress-test/actions/workflows/maven.yml)

SonarCloud analysis:

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=republique-et-canton-de-geneve_rabbitmq-stress-test&metric=bugs)](https://sonarcloud.io/dashboard?id=republique-et-canton-de-geneve_rabbitmq-stress-test)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=republique-et-canton-de-geneve_rabbitmq-stress-test&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=republique-et-canton-de-geneve_rabbitmq-stress-test)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=republique-et-canton-de-geneve_rabbitmq-stress-test&metric=code_smells)](https://sonarcloud.io/dashboard?id=republique-et-canton-de-geneve_rabbitmq-stress-test)

Licence :

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/why-affero-gpl.html)

# RabbitMQ stress tests

This projet defines a set of tests of en-masse production and consumption of RabbitMQ messages.
These tests were used to validate RabbitMQ as the new tool as a message-oriented middleware, to replace
Java Message Service (JMS).

The State of Geneva's acceptance criteria are very undemanding, as compared with
to RabbitMQ's notorious out-of-the-box capacity of handling many messages in short period of time.
Therefore, these tests were used to validate the State of Geneva's own setup of RabbitMQ - more than to validate the
RabbitMQ tool itself.
They all passed with success.

The tests are written in Java.
The usual Spring and Spring Boot code harness is not used, in order for the code to remain
as close as possible to the low-level Java client for RabbitMQ.

# Scenarios

Three test scenarios are implemented.

In every scenario, the message size is 100 kB, close to the maximum message size of 128 kB
chosen at the State of Geneva.

In every scenario, access to RabbitMQ is guarded by a Cloud Foundry
[User Account and Authentication](https://docs.cloudfoundry.org/concepts/architecture/uaa.html)
(UAA) server.

In every scenario, the producer just creates the message and sends it to the RabbitMQ broker;
similarly the consumer just receives the message and logs a short trace.
Accordingly, these are very light producers and consumers.

## Scenario 1 : reliability (endurance) test

A message is produced with a regular, slow pace, during a long period.

Acceptance criterion: the system must withstand 2 messages per second durant 8 hours.

## Scenario 2 : load test

Messages are produced with an increasing pace, until exhausting the processing capacity of the system.

Acceptance criterion: at every second, 1 more message is produced than at the previous second, and
the system must withstand the load during 1 minute and 40 seconds.
The final load is thus 100 messages per second, that is, 10 MB/sec.


## Scenario 3 : storage capacity test

Messages are produced with a regular, slow pace, during a long period.
The messages are not consumed at all.
Over a long period of time, the message storage capacity should be exhausted.

Acceptance criterion: the system must withstand 2 messages per second during 3 hours.
This roughly amounts to 2 GB of stored messages.

# Build

Run the following Maven command:

```mvn clean package```

to create the JAR file (an uber jar).

# Prerequisites to run any scenario

In the RabbitMQ broker, the exchanges, routing keys and queues mentioned in the properties
file `scenario.properties` described hereafter must be set up.

In the UAA server, the client_id, the groups and the external groups described in the latter file
must be set up.
The configuration can be easily downgraded to use RabbitMQ's "guest/guest" credentials and no UAA.

# Running a scnario

Using Java 8+, run the following command:

```java -jar target/rabbitmq-load.jar mdp.properties scenario.properties```

where:
- file `mdp.properties` is a one-line file containing a single property `ad.password = XXX`, where `XXX`
  is the password of the AD user mentioned in file `scenario.properties` (property `ad.user`).
- file `scenario.properties` contains the properties for a scenario.

A sample `scenario.properties` file is supplied in the sources.
It provides values for each of the scenarios.

The reason for defining property `ad.password` in its own separate file is to allow the team to include the
main properties file `scenario.properties` within the State of Geneva's Git repository without exposing - even
locally - any password.
