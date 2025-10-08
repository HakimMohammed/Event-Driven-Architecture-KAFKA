# Kafka Cloud Stream Demo

This project is a Spring Boot 3 + Spring Cloud Stream application that demonstrates three types of functional components with Apache Kafka:

- Supplier that periodically emits PageEvent messages
- Consumer that logs received PageEvent messages
- Kafka Streams Function that filters, transforms, window-aggregates events, and exposes the aggregation via Interactive Queries

A simple web page consumes a server‑sent events (SSE) endpoint to render a live chart of page view counts.


## High‑level Architecture

Data flows through two Kafka topics (names are configurable):

1) Supplier → topic T2
- Bean: pageEventSupplier
- Produces PageEvent(name, user, date, duration) at a fixed interval
- Destination: spring.cloud.stream.bindings.pageEventSupplier-out-0.destination=T2

2) Function (Kafka Streams) T2 → T1
- Bean: pageEventFunction: KStream<String, PageEvent> -> KStream<String, Long>
- Steps:
  - filter events with duration > 100
  - map to key=name, value=duration
  - groupByKey
  - windowed count over 5-second tumbling windows
  - materialize as state store named "page-event-count"
  - map windowed key back to plain key
- Input: spring.cloud.stream.bindings.pageEventFunction-in-0.destination=T2
- Output: spring.cloud.stream.bindings.pageEventFunction-out-0.destination=T1

3) Consumer ← topic T1
- Bean: pageEventConsumer
- Simply logs received PageEvent objects to stdout (in this setup, the Function outputs Long counts to T1; the Consumer definition shows how to consume PageEvent, and can be repointed if desired)
- Destination: spring.cloud.stream.bindings.pageEventConsumer-in-0.destination=T1

4) Interactive Queries + SSE
- The Kafka Streams state store "page-event-count" is queried every second from /analytics
- The endpoint streams a JSON map of page -> count as text/event-stream
- The static index.html subscribes to /analytics and draws a live chart for "Page 1" and "Page 2"


## Important Beans and Files

- KafkaCloudStreamApplication.java: Spring Boot entry point
- PageEvent.java: record representing the event payload {name, user, date, duration}
- PageEventHandler.java:
  - pageEventSupplier: produces demo events with random names ("Page 1" or "Page 2"), users ("User 1" or "User 2"), and durations [10..1009]
  - pageEventConsumer: logs events
  - pageEventFunction: Kafka Streams topology performing filter, map, windowed count and materialization as "page-event-count"
- PageEventController.java:
  - GET /publish?name=...&topic=...: sends a single PageEvent to the provided Kafka topic using StreamBridge
  - GET /analytics (text/event-stream): every second, queries the window store and streams a map of current counts
- src/main/resources/static/index.html: simple page that connects to /analytics with EventSource and draws a real-time chart


## Configuration (application.properties)

- spring.cloud.stream.bindings.pageEventConsumer-in-0.destination=T1
- spring.cloud.stream.bindings.pageEventSupplier-out-0.destination=T2
- spring.cloud.stream.bindings.pageEventFunction-in-0.destination=T2
- spring.cloud.stream.bindings.pageEventFunction-out-0.destination=T1
- spring.cloud.function.definition=pageEventConsumer;pageEventSupplier;pageEventFunction
- spring.cloud.stream.kafka.streams.binder.configuration.commit.interval.ms=1000

Notes:
- The supplier polling interval is intended to be configurable; the property is named as a producer poller fixed delay. If you need to adjust it, verify the property key for your Spring Cloud Stream version (e.g., spring.cloud.stream.poller.fixed-delay=2000 when using a default poller) or configure a dedicated Poller bean.
- The Kafka Streams state store used by Interactive Queries is named "page-event-count" (from Materialized.as("page-event-count")).


## Prerequisites

- Java 21+
- Maven 3.9+
- Apache Kafka running locally (KRaft or ZooKeeper mode)

Example using Docker (single Kafka with KRaft):

- docker run -p 9092:9092 -p 29092:29092 \
  -e KAFKA_CFG_NODE_ID=1 -e KAFKA_CFG_PROCESS_ROLES=broker,controller \
  -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:29092 \
  -e KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=1@localhost:29092 \
  -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CFG_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE=true \
  --name kraft-kafka -d bitnami/kafka:latest


## How to Run

1) Start Kafka (see prerequisites). Ensure localhost:9092 is reachable.
2) Build and run the app:
- mvn spring-boot:run
3) Open the live chart:
- http://localhost:8080/

You should see two lines (Page 1 and Page 2) updating. Counts reflect the number of events with duration > 100 observed in the last 5 seconds, grouped by page name.


## Testing the REST publish endpoint

- Send a one-off event to a topic (for example, T2 to feed the function):
- curl "http://localhost:8080/publish?name=Page%201&topic=T2"

The StreamBridge will send a PageEvent to topic T2; if its duration > 100, it will be counted by the function and reflected in the /analytics stream shortly after.


## Under the Hood: Kafka Streams Topology

- Input stream: KStream<String, PageEvent> from T2
- filter: keep only events where duration > 100
- map: (key, event) → (event.name, event.duration)
- groupByKey
- windowedBy: 5-second time windows (TimeWindows.of(Duration.ofSeconds(5)))
- count: materialize as "page-event-count"
- toStream: convert KTable<Windowed<String>, Long> to KStream
- map: (windowedKey, count) → (windowedKey.key, count)

The state store supports Interactive Queries; the controller fetches all entries for the last 5 seconds on each tick and streams them to clients.


## Troubleshooting

- If no data appears on the chart:
  - Verify Kafka is running and accessible at localhost:9092
  - Check application logs for binder errors
  - Ensure topics T1 and T2 exist (auto-creation may create them automatically depending on broker config)
  - Confirm that the supplier is emitting: look for log messages from pageEventConsumer if it is pointed at the correct topic/type
- If Interactive Query fails: make sure the store name matches ("page-event-count")


## License

This project is for educational purposes. No explicit license is provided in the POM.