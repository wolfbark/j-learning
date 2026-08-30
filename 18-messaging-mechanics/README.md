# 18 — Messaging Mechanics: Partitions, Lag, Retries, and Queues That Aren't Streams

> After this lesson you can reason about a broker instead of hoping about it: predict which
> partition a key lands on and what that guarantees, explain why a consumer group can't outgrow
> its partition count (and why a Kafka 4.2 **share group** can), diagnose a rebalance storm from
> its symptoms, measure and bound consumer lag, build a retry chain that terminates in a
> dead-letter topic carrying evidence, gate schema changes in CI, and say out loud where the
> RabbitMQ model is the better tool.

## Why this matters (2026)

Projects 07–11 taught the *patterns* of asynchronous systems: events versus commands, the
transactional outbox, idempotent consumers, sagas, service boundaries. They deliberately treated
the broker as a pipe that works. This lesson is the other half — **the machinery and its failure
modes** — because that is where the incidents come from. Nobody's postmortem says "our outbox
pattern was theoretically unsound." They say: one partition was carrying 90% of the traffic; a
slow consumer breached `max.poll.interval.ms` and the group rebalanced itself into a coma for
forty minutes; a poison message blocked a partition for three hours because retries were
unbounded; someone added a required field and every consumer in the estate started throwing.

Three things changed recently enough to be worth naming:

- **Kafka 4.2 made share groups (KIP-932) generally available.** For fifteen years the answer to
  "can I have more consumers than partitions?" was *no, use more partitions*. Now a topic can be
  consumed either as a log (consumer group, offsets, whole-partition ownership) or as a queue
  (share group, per-record acknowledgement, competing consumers on the same partition). The
  log-versus-queue argument that shaped a decade of broker selection has partially collapsed —
  and knowing *which* part collapsed is now a real design skill.
- **KRaft-only Kafka (4.x) plus the KIP-848 consumer rebalance protocol** made rebalancing
  incremental rather than stop-the-world for groups that opt in — but the classic protocol is
  still the default in most deployments, and the `max.poll.interval.ms` footgun survives both.
- **Schema registries became normal infrastructure**, which means the interesting question is no
  longer "should we version our messages" but "which compatibility direction do we enforce, and
  what happens in CI when someone violates it".

What this lesson **does not** re-teach: the dual-write problem, the transactional outbox, event
externalization, and idempotent consumers. Those belong to
[project 07](../07-events-and-outbox/README.md), and the last section here points back to it for
the reason that matters — none of the broker mechanics below buy you exactly-once *effects*.

## Core concepts

### A log and a queue are different data structures, not different products

| | Durable log (consumer group) | Work queue (share group / AMQP queue) |
|---|---|---|
| Unit of progress | an **offset** per partition | an **acknowledgement** per record |
| Ownership | a partition belongs to one member | any member may take any record |
| Max useful consumers | number of partitions | unbounded |
| Ordering | total per partition | none, by construction |
| Redelivery | rewind an offset (replays a *range*) | re-acquire one record |
| Reading is | non-destructive — the log stays | destructive within the group |
| Natural payload | facts (`ParcelScanned`) | tasks (`NotifyCustomer`) |

The grammar check from project 07 still decides which one you want: if the message is a fact in
the past tense that many unknown consumers may care about, it belongs on a log. If it is an
imperative addressed to whoever is free — *send this SMS* — it is a work item, and a log gives you
the wrong failure modes (one slow item blocks a partition; two workers can't share it; "retry" means
rewinding an offset over records that already succeeded).

### The key chooses the partition, and that is the whole ordering story

```
partition = murmur2(key) % partitionCount        // for any record with a key
```

Three consequences, all load-bearing:

1. **Same key ⇒ same partition ⇒ total order.** Everything about parcel `P-1` is ordered against
   everything else about `P-1`.
2. **Different keys ⇒ no relationship at all.** There is no global order in a partitioned topic.
   Consumers see a *merge* of independent sequences, and the merge differs per run.
3. **`partitionCount` is in the formula.** Add partitions and most keys re-home — which silently
   breaks per-key ordering for anything in flight, and re-splits your consumers' state.

A null key is a different mode entirely (sticky batching round-robin, no ordering domain), which is
fine for metrics and wrong for anything stateful.

### Consumer groups, assignment, and the rebalance you caused yourself

A consumer group's members divide the partitions between them. With 3 partitions and 4 members,
one member is paid to watch — partitions are the unit of parallelism, so the partition count is a
capacity decision you make at topic-creation time and regret later.

The failure mode everybody hits at least once:

```
poll()  ──►  returns up to max.poll.records records
             │
             └─► your handler takes longer than max.poll.interval.ms  (default 5 min)
                     │
                     └─► coordinator: "member is dead" ──► rebalance
                             │
                             └─► survivor inherits the same slow batch ──► repeats forever
```

Nothing is wrong with the code that processes one record. What is wrong is the *batch size times
the per-record cost* against the interval. The fixes, in order of preference: bound the batch
(`max.poll.records`), make the work faster, pause/resume instead of holding up the poll loop, or —
last, and only with your eyes open — raise `max.poll.interval.ms` and accept a slower failure
detector.

### Lag is a queue you cannot see

Lag is `log-end-offset − committed-offset`, per partition. It is the single most useful number in a
streaming system, and the only honest way to ask "is my consumer keeping up?" Two rules:

- **Measure lag on the polling thread.** A `KafkaConsumer` is single-threaded by contract; calling
  it from your test or metrics thread earns a `ConcurrentModificationException`.
- **Buffering is not backpressure.** Handing records to an unbounded in-memory queue makes lag
  *look* fine while relocating the backlog into your heap, where it fails later, further away, and
  without the broker's metrics to explain it. Real backpressure is: bounded hand-off, `pause()` the
  assignment when the buffer is full, keep calling `poll()` so the member stays alive, `resume()`
  when the worker catches up.

### Retries that terminate, and dead letters that carry evidence

Two questions, in this order:

1. **Is this failure retryable?** A timeout, a 503, a deadlock: yes. A malformed payload, an
   unknown enum value, a validation failure: never — retrying is just failing more expensively,
   and on a log it blocks the partition behind it.
2. **How many times, and where does the last attempt go?** "Until it works" is not a policy.
   Bounded attempts with backoff, then a dead-letter topic whose records carry the original
   payload, the original topic/partition/offset and the exception — otherwise the DLQ is a
   graveyard nobody can act on.

In Spring Kafka the two shapes are **blocking retries** (`DefaultErrorHandler` with a `BackOff`,
recovering into a `DeadLetterPublishingRecoverer`) and **non-blocking retries** (`@RetryableTopic`:
failed records are forwarded to timed retry topics so the main partition keeps moving, at the cost
of losing per-key ordering for the retried records). Step 6 builds the blocking variant, because
its ordering semantics are the ones you must be able to explain.

### Schema evolution: pick a direction before you need one

- **Backward** compatible: new readers can read old data (consumers upgrade first).
- **Forward** compatible: old readers can read new data (producers upgrade first).
- **Full**: both — the only sane default for a topic with consumers you don't control.

Mechanically, that's a handful of rules: adding an optional field with a default is safe; adding a
required field without a default breaks new readers of old data; removing a required field breaks
old readers; changing a type breaks both; a rename is a removal *and* an addition. A schema
registry (Apicurio, Confluent) enforces these centrally and can reject a producer at registration
time. This project implements the same rules as a unit-testable gate, because a registry you don't
have yet is not an excuse for finding out in production.

### Share groups: what actually changed in 4.2, and what didn't

A share group has **no offsets**. The broker keeps per-record state (`Available → Acquired →
Acknowledged`), hands each record to one member under an *acquisition lock*, and redelivers it if
the member releases it, rejects it, or lets the lock expire. Every record carries a
`deliveryCount`, so "how many times has this been tried?" is finally a broker-level fact.

What that buys you: more workers than partitions, per-record acknowledgement, and redelivery of
*one* record instead of a rewind over a range.

What it does not buy you — measured in this project, not assumed:

- **No ordering.** Two workers on one partition means the second scan can be handled before the
  first. Share groups are for tasks, not for facts.
- **No magical draining of an existing backlog.** When a large backlog is already sitting in the
  partition, one member's fetch can acquire a big batch and the others find nothing to do. Share
  groups smooth *arriving* work; they are not a parallel backfill tool.
- **Still at-least-once.** A released or lock-expired record comes back. Consumers still need the
  idempotency of project 07.

Three configuration facts that cost real debugging time (all verified on `apache/kafka:4.2.0`):

- `share.acknowledgement.mode=explicit` on the client, or `acknowledge(...)` throws
  `IllegalStateException: Implicit acknowledgement of delivery is being used`.
- A share group's start position is **group** configuration on the broker
  (`share.auto.offset.reset`, default `latest`), set with
  `Admin.incrementalAlterConfigs` on a `ConfigResource(Type.GROUP, groupId)`. The client-side
  `auto.offset.reset` does nothing.
- The share coordinator's internal topic defaults to replication factor 3. On a single-broker test
  cluster, set `KAFKA_SHARE_COORDINATOR_STATE_TOPIC_REPLICATION_FACTOR=1` or every share-group call
  times out with no useful error.

## The project

A parcel-tracking feed. Two message flows, deliberately different in kind:

- `parcels.scans` — **facts**, keyed by parcel id. A durable log with 3 partitions, consumed by a
  classic consumer group that maintains a projection (`ScanFeed`).
- `parcels.notify-customer` — **work items** (`NotifyCustomer`): somebody must actually send the
  message, exactly one worker should do it, and it can fail and be retried.

```
src/main/java/dev/vlearning/parcels/
├── MessagingMechanicsApplication.java
├── ParcelsProperties.java              ← topic names are configuration, so each test owns its topics
├── KafkaTopicsConfig.java
├── wire/JsonCodec.java                 ← Jackson 3, tolerant reader, epoch-millis timestamps
├── scan/
│   ├── ParcelScan.java, ScanStatus.java
│   ├── PartitionKeys.java              ← byParcel / byCustomer / salted(…) ← step 3 is here
│   └── ScanPublisher.java
├── feed/
│   ├── ScanFeed.java                   ← projection that remembers partition + offset
│   ├── ScanFeedListener.java           ← @KafkaListener, classic consumer group
│   └── BackpressuredScanReader.java    ← unbounded buffer, no pause ← step 5 is here
├── notify/
│   ├── NotifyCustomer.java             ← a command, not an event
│   ├── NotifyDispatcher.java, NotifyWorker.java   ← naive worker ← step 6 is here
│   ├── FlakyChannel.java               ← arms transient / permanent failures
│   ├── ChannelUnavailableException.java (retryable) / UnknownChannelException.java (not)
│   └── NotifyLedger.java               ← what happened, for the tests to assert on
└── schema/
    ├── FieldSpec.java, FieldType.java, RecordSchema.java
    ├── SchemaCompatibility.java        ← one rule implemented ← step 7 is here
    └── ScanSchemas.java                ← V1 plus four attempted changes
```

Three pieces of given code are **deliberately wrong**, and each is a step's subject:
`PartitionKeys.salted` throws, `BackpressuredScanReader` buffers without limit and commits
records it has only buffered, and `NotifyWorker` has no error classification, no bounded retry and
no dead-letter path.

The test-side harness is where most of the observation happens:

```
src/test/java/dev/vlearning/parcels/support/
├── KafkaSupport.java          ← singleton apache/kafka:4.2.0, admin, topics, produce/drain, lag
├── ConsumerGroupProbe.java    ← N classic members you can poll by hand and watch assignment
├── ShareGroupPool.java        ← N competing share-group workers, per-member counters
└── RabbitSupport.java         ← singleton rabbitmq:3-management-alpine
```

**Requirements:** Docker running. Containers are singletons (static start + `@DynamicPropertySource`),
and every test class creates its own topics with a random suffix, so replayed records cannot leak
between tests.

```bash
mvn test          # pristine checkout: green in well under a minute; checkpoints are skipped
```

What stays enabled by default is a thin proof that the infrastructure and the two riskiest
mechanics work: keyed placement and consumer-group assignment (`StreamMechanicsSmokeTest`), share
group queue semantics and per-record redelivery (`ShareGroupSmokeTest`), AMQP routing and
dead-lettering (`RabbitContrastTest`), plus the application happy path
(`HappyPathIntegrationTest`). All the measurement work — skew, rebalance storms, lag, DLQ
forensics — is in the checkpoints, where it is yours to run.

## Guided steps

Checkpoint tests live in `src/test/java` annotated
`@Disabled("Checkpoint N — enable when you start step N")`. Remove the annotation when you reach
the step. Checkpoints 1, 2 and 4 are **observation** checkpoints: they pass against the given code
and exist to make a mechanic visible. Checkpoints 3, 5, 6 and 7 are partly red on purpose — the
first test of each shows you the problem, the rest stay red until you fix it.

### Step 1 — Two ways to consume the same topic

**Goal:** feel the log/queue distinction on one broker, and place each of this project's two flows
on the right side of it.

Enable `Checkpoint1LogVsQueueTest` and read all three tests before running them.

```bash
mvn test -Dtest=Checkpoint1LogVsQueueTest
```

- `aConsumerGroupCannotOutgrowItsPartitions` — one partition, three members: two sit idle and one
  does all thirty records.
- `aShareGroupCan` — the same single partition, three share-group workers, all three busy.
- `theLogReplaysAndTheQueueDoesNot` — a brand-new consumer group re-reads the whole log; the same
  share group, restarted, has nothing left. Acknowledgement is *group state*, not a log position.

Then answer, in one sentence each: which of `ParcelScan` and `NotifyCustomer` is a command, which
is an event, and what breaks if you swap their transports?

<details><summary>Hint — why the share group needs work to <em>arrive</em></summary>

`ShareGroupPool.start()` returns only after every member has joined, and the tests produce work
afterwards, some of it trickled. That is not politeness: with a backlog already in the log, a
single member's fetch can acquire a large batch, and you will watch one worker do everything while
the other two poll empty. Change `Duration.ofMillis(15)` to `Duration.ZERO` in `aShareGroupCan`
and see it happen — then put it back, and remember it the next time somebody proposes share groups
as a backfill accelerator.

</details>

<details><summary>Hint — driving a share group from Spring instead of raw clients</summary>

Spring Kafka 4.1 supports share consumers on `@KafkaListener`. The wiring is a second container
factory (class names verified against spring-kafka 4.1.1; this project's own code uses the raw
client on purpose, so you can see the acknowledgement):

```java
@Bean
ShareKafkaListenerContainerFactory<String, String> shareFactory(ParcelsProperties properties) {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, …);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");
    var factory = new ShareKafkaListenerContainerFactory<String, String>(
            new DefaultShareConsumerFactory<>(props));
    factory.setConcurrency(3);                                     // three workers, one partition
    factory.getContainerProperties().setShareAckMode(ContainerProperties.ShareAckMode.EXPLICIT);
    return factory;
}

@KafkaListener(topics = "${parcels.topics.tasks}", groupId = "notify-workers",
               containerFactory = "shareFactory")
void onTask(ConsumerRecord<String, String> record, ShareAcknowledgment ack) {
    …
    ack.acknowledge();          // or acknowledge(AcknowledgeType.RELEASE) to hand it back
}
```

Remember the group-level `share.auto.offset.reset`; no Spring property covers it.

</details>

**Done when:** all three checkpoint-1 tests pass, and you can state the one property a share group
gives up in exchange for competing consumers.

### Step 2 — Partitions, keys, and what "ordered" means

**Goal:** stop saying "Kafka is ordered" and start saying what is ordered.

Enable `Checkpoint2OrderingTest`. Six parcels, five scans each, interleaved as a real feed would
interleave them. The tests assert that per-parcel order survives, that the producer's global order
does not, and that a single-partition topic gives you total order at a throughput ceiling of one
consumer.

Then predict, without running anything: on a 3-partition topic, which partition holds `P-4711`?
Check with `PartitionKeys.partitionFor("P-4711", 3)`, and check that against
`StreamMechanicsSmokeTest`, which asserts the broker agrees.

<details><summary>Hint — the arithmetic</summary>

`BuiltInPartitioner.partitionForKey(key.getBytes(UTF_8), partitionCount)`, i.e.
`toPositive(murmur2(bytes)) % partitionCount`. Note what is *not* in that expression: the record's
size, the partition's current load, the consumer's health, or anything about your business.

</details>

**Done when:** checkpoint 2 is green and you can explain why adding partitions to a keyed topic is
a breaking change for consumers that keep per-key state.

### Step 3 — Hot partitions, and the key that fixes them

**Goal:** produce a skewed workload, measure the skew, then re-key and re-measure.

Enable `Checkpoint3HotPartitionTest`. The traffic is what every real parcel feed looks like: one
enormous B2B customer and a long tail. `keyingByCustomerCooksOnePartition` proves that keying by
customer puts 90% of the traffic on one of six partitions — one consumer at 100% CPU, five idle,
and lag that only ever grows on that partition.

Now implement `PartitionKeys.salted(scan, buckets)` so that the other two tests pass. Note the
constraints the tests encode: the key must still *name* the customer (or the downstream aggregate
cannot be rebuilt) and it must be **stable per parcel** (or you traded skew for lost ordering).

<details><summary>Hint — the shape of a salted key</summary>

```java
public static String salted(ParcelScan scan, int buckets) {
    int bucket = Math.floorMod(scan.parcelId().hashCode(), buckets);
    return scan.customerId() + "#" + bucket;
}
```

Use more buckets than partitions (the test uses `4 × partitions`) — the salt spreads keys, and
`murmur2 % partitions` then spreads the buckets. The price is downstream: anything that aggregated
per customer now has to merge `buckets` partial aggregates, which is exactly the re-aggregation
step you should be able to describe before you ship this.

</details>

<details><summary>Hint — when salting is the wrong answer</summary>

If the whale's ordering domain genuinely is the customer (a running balance, say), a salted key
destroys the guarantee you needed and no amount of downstream cleverness restores it. Then the
answers are: a dedicated topic (or dedicated partitions) for the whale, a different aggregate
boundary, or accepting the hot partition and scaling that single consumer vertically. Choosing
"even distribution" over "correct ordering" without noticing is a genuine production bug.

</details>

**Done when:** all three checkpoint-3 tests are green and you can describe the re-aggregation cost
you just moved downstream.

### Step 4 — Rebalancing, and the poll interval you breached

**Goal:** cause a rebalance storm on purpose, then fix it with one line of configuration.

Enable `Checkpoint4RebalanceTest`. Both tests use the *same* slow handler (300 ms per record) and
the same short `max.poll.interval.ms` (5 s). They differ in `max.poll.records`: 100 versus 1.

- `aPollBatchBiggerThanThePollIntervalNeverFinishes` — the member is evicted mid-batch, the group
  re-assigns, the survivor inherits the same batch, and the work never completes. The test asserts
  more than two assignment rounds and *incomplete* processing. That is a passing test describing a
  broken system.
- `boundingThePollBatchFixesIt` — all sixty records processed, and not one rebalance after the
  group settled.

While it runs, watch the group from outside — the observation is the point of the step:

```bash
docker exec -it $(docker ps --filter ancestor=apache/kafka:4.2.0 -q) \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group cp4-storm-group
```

<details><summary>Hint — the three timers, and which one you actually breached</summary>

`heartbeat.interval.ms` (background thread, "I'm alive"), `session.timeout.ms` (how long the
coordinator waits for heartbeats), `max.poll.interval.ms` (how long the coordinator waits for the
*next poll call*). Slow processing never misses a heartbeat — the background thread keeps beating
happily while your handler grinds — so this failure always shows up as the poll interval, and
raising `session.timeout.ms` does nothing for it.

</details>

**Done when:** checkpoint 4 is green and you can name the four ways out (smaller batches, faster
work, pause/resume, longer interval) and the cost of each.

### Step 5 — Lag, and backpressure that isn't a bigger buffer

**Goal:** measure lag from inside the consumer, then bound the work in flight instead of the heap.

Enable `Checkpoint5LagBackpressureTest`. The first test passes against the given code: a slow
consumer builds measurable lag and then catches up without losing anything. The second one fails,
because `BackpressuredScanReader` "solves" backpressure with an unbounded queue — it asserts that
the hand-off buffer never exceeded its bound and that you got there by pausing.

Rewrite the poll loop so that it pauses when the buffer is full and resumes when the worker has
caught up.

<details><summary>Hint — the pause/resume loop</summary>

```java
var records = consumer.poll(Duration.ofMillis(200));
records.forEach(handOff::add);                       // now a bounded ArrayBlockingQueue

if (handOff.remainingCapacity() == 0 && consumer.paused().isEmpty()) {
    consumer.pause(consumer.assignment());
    recordPaused();
} else if (handOff.size() < inFlightLimit / 2 && !consumer.paused().isEmpty()) {
    consumer.resume(consumer.paused());
}
```

Two details decide whether this works: you must **keep calling `poll()` while paused** (a paused
consumer still heartbeats and still holds its assignment — stopping the loop is how you turn
backpressure into a rebalance), and the queue must actually be bounded, with the poll loop
respecting `remainingCapacity()` rather than blocking inside `add`.

</details>

<details><summary>Hint — the second bug in that class</summary>

`commitSync()` runs right after records are handed off, before the worker has processed them. A
crash there loses everything buffered: at-least-once quietly became at-most-once. Moving the commit
behind the worker's completion is the stretch goal, and it is where this lesson touches project
07's territory — the guarantee lives in *where you commit*, not in the broker.

</details>

**Done when:** both checkpoint-5 tests are green, and you can say what lag looks like in Grafana for
a consumer that is paused and healthy versus one that is dead.

### Step 6 — Bounded retries and a dead letter worth reading

**Goal:** classify failures, bound the retries, and land the hopeless ones in a DLQ with their
cause attached — while healthy work keeps flowing.

Enable `Checkpoint6RetryDlqTest`. It dispatches a poison task (`channel = "carrier-pigeon"` →
`UnknownChannelException`), two healthy tasks, and separately a task whose channel fails twice
before succeeding (`ChannelUnavailableException`). It asserts: the poison task is attempted
**once**, lands in the DLQ with `kafka_dlt-exception-message` and `kafka_dlt-original-topic`
headers, the healthy tasks are delivered anyway, and the transient failure succeeds on attempt
three and is *not* dead-lettered.

Add a `DefaultErrorHandler` to the application.

<details><summary>Hint — the error handler bean</summary>

```java
@Bean
DefaultErrorHandler notifyErrorHandler(KafkaTemplate<String, String> template,
                                       ParcelsProperties properties) {
    var recoverer = new DeadLetterPublishingRecoverer(template,
            (record, exception) -> new TopicPartition(properties.topics().dlq(), 0));
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(200L, 2));   // 1 + 2 attempts
    handler.addNotRetryableExceptions(UnknownChannelException.class);
    return handler;
}
```

Boot's Kafka auto-configuration wires a single `CommonErrorHandler` bean into the listener
container factory, so declaring this bean is the whole change. `FixedBackOff(interval, maxRetries)`
counts *retries*, not attempts — `(200, 2)` means three tries in total, which is what the test
asserts. `DeadLetterPublishingRecoverer` copies the original record's headers and adds the
`kafka_dlt-*` ones; the destination resolver above pins partition 0 because the DLQ topic has one
partition and the default resolver reuses the *original* partition number.

</details>

<details><summary>Hint — why the poison message blocking the partition matters more than the DLQ</summary>

With unbounded retries on a log, a poison record does not just fail — it stops every record behind
it on that partition, because the offset cannot advance. That is why "retry forever, someone will
look at it" is not a conservative choice: it converts one bad message into a partition-wide outage.
The non-blocking alternative (`@RetryableTopic`) keeps the main partition moving by forwarding
failures to retry topics, at the cost of per-key ordering for the retried records — a trade you
should be able to argue either way.

</details>

**Done when:** checkpoint 6 is green, and you can explain what a consumer of the DLQ topic needs in
order to replay a fixed record safely (hint: project 07's idempotency).

### Step 7 — Schema evolution, with a gate in CI

**Goal:** make one compatible change and one breaking change, and have the test suite tell them
apart.

Enable `Checkpoint7SchemaEvolutionTest`. Three of its tests already pass: the compatible change
(an optional field with a default) clears the gate, an old consumer reads the new payload on the
wire, and a *strict* reader of the same payload blows up — tolerant reading is a decision somebody
makes on purpose. Three fail, because `SchemaCompatibility` only implements one rule.

Implement the remaining rules so that `V3_REQUIRED_CARRIER`, `V4_RETYPED_TIMESTAMP` and
`V5_RENAMED_HUB` are all rejected.

<details><summary>Hint — the two missing rules</summary>

```java
for (FieldSpec after : next.fields()) {
    if (previous.field(after.name()).isEmpty() && after.required() && after.defaultIfAny().isEmpty()) {
        violations.add(new Violation(after.name(), "required-field-added", …));
    }
}
for (FieldSpec before : previous.fields()) {
    next.field(before.name())
        .filter(after -> after.type() != before.type())
        .ifPresent(after -> violations.add(new Violation(before.name(), "type-changed", …)));
}
```

The rename case needs no third rule: `V5` removes `hubId` and adds a required `facilityId`, so the
existing removal rule and your new addition rule each fire once. If your implementation reports
only one violation for it, you have a rule that returns early.

</details>

In production this gate belongs to a **schema registry** — Apicurio or Confluent Schema Registry —
configured with `FULL` (or `BACKWARD`) compatibility on the subject, so a producer carrying an
illegal schema is refused at registration instead of at 3 a.m. Neither image is cached on this
machine, which is why the rules live in a unit test here; the rules themselves are identical, and a
test that runs in every build has one genuine advantage over a registry: it fails in the pull
request, next to the diff that caused it.

**Done when:** all six checkpoint-7 tests are green and you can name the compatibility direction
your team's topics actually need, and who has to upgrade first under it.

### Interlude — the other broker model: RabbitMQ

`RabbitContrastTest` is enabled by default and takes about six seconds. It is small on purpose:
having *felt* both models once is worth more than a chapter about them.

Kafka is a **dumb broker with smart consumers**: it appends bytes to partitions and remembers
offsets; routing, filtering, retry policy and progress tracking are the consumer's problem.
RabbitMQ is a **smart broker with dumb consumers**: the broker owns routing, per-message state,
acknowledgements, redelivery and dead-lettering, and the consumer just says yes or no.

```
publisher ──► exchange ──(routing key ⨯ binding)──► queue ──► consumer
                                                      │  basicAck   → gone
                                                      │  basicNack(requeue=true)  → back in line
                                                      └─ basicNack(requeue=false) → dead-letter exchange
```

The test declares a topic exchange, binds `notify.delivered` with the pattern `scan.delivered.#`,
publishes two messages, and finds only the matching one queued — **the non-matching message was
never stored anywhere**. In Kafka every consumer reads every record in its partitions and filters
in application code. Consumer-side filtering is more flexible; broker-side routing is less traffic,
less code, and one fewer thing to get wrong.

Two more things the test shows:

- **Per-message acknowledgement.** `basicGet(…, false)` then `basicAck(deliveryTag, false)` — one
  message, one decision, no offsets. Exactly the model Kafka's share groups adopted.
- **Dead-lettering as configuration.** `x-dead-letter-exchange` on the queue plus
  `basicNack(requeue=false)` *is* the entire dead-letter implementation. Compare with step 6's
  error-handler bean, backoff, recoverer and destination resolver.
- **Quorum queues.** The `x-queue-type: quorum` argument makes the queue a Raft replicated log
  across nodes — the default choice in RabbitMQ 4.x for anything durable (classic mirrored queues
  are gone). They cost more memory and disk per message and do not support some legacy features;
  that is the trade for a queue that survives a node loss without ambiguity.

Choose RabbitMQ when routing is complex, messages are tasks, you want per-message TTLs, priorities
and delayed delivery, and throughput is measured in thousands per second. Choose Kafka when the
stream is a shared source of truth many consumers replay, when ordering per key matters, and when
throughput is measured in hundreds of thousands per second. With share groups, "we need queue
semantics" alone is no longer a reason to run a second broker — but "we need routing" and "we need
replay" still point in opposite directions.

### The honest ending

Everything in this lesson is about the *transport*. None of it gives you exactly-once **effects**.
A share group's acknowledgement, an AMQP `basicAck`, a committed offset, even Kafka's transactional
producer with `read_committed` — all of them can be lost after your side effect happened and before
the broker recorded it. The delivery count comes back as 2, and the customer gets a second SMS, the
card gets charged twice, the external API creates a duplicate shipment.

The broker's job is to not lose your message. Making the *effect* happen once is your job, and you
already built it in [project 07](../07-events-and-outbox/README.md): capture intent in one local
transaction, accept at-least-once in the middle, and make the receiver idempotent. This lesson
tells you what the middle actually does under load. It does not let you skip the ends.

## Self-check

1. A topic has 6 partitions and your consumer group has 10 members. What are the four idle members
   doing, and what changes if you switch that group to a share group?
2. What exactly is ordered in a keyed Kafka topic, and what happens to that ordering when you add
   partitions on a Friday afternoon?
3. Your consumer group rebalances every 30 seconds and lag grows. Which timer did you breach, why
   didn't heartbeats save you, and which single configuration change do you try first?
4. Lag is flat at zero but the service OOMs every four hours. What is the most likely design bug,
   and which metric would have shown it?
5. A poison message arrives on a 3-partition topic and your retry policy is "retry until success".
   Describe the blast radius after ten minutes.
6. Which failures should never be retried, and what should carry the record's cause into the DLQ so
   somebody can act on it?
7. You add a required field with no default to an event that 30 services consume. Who breaks —
   producers, consumers, or both — and which compatibility direction did you just violate?
8. A share group redelivers a work item whose `deliveryCount` is 3. Your worker already sent the
   SMS on attempt 1. Whose bug is the duplicate SMS, and where is it fixed?

## Stretch goals

- **Commit after processing.** Fix the second bug in `BackpressuredScanReader`: move the offset
  commit behind the worker's completion, and write the test that fails against the current
  ordering (kill the reader mid-flight, restart it, assert nothing was skipped).
- **Non-blocking retries.** Re-implement step 6 with `@RetryableTopic` (retry topics with
  exponential backoff plus a DLT) and write down, concretely, which ordering guarantee you gave up
  and for which records.
- **Watch a rebalance with the new protocol.** Set `group.protocol=consumer` (KIP-848) on the
  checkpoint-4 consumers and compare assignment behaviour and storm severity with the classic
  protocol. Explain what "incremental" bought you and what it did not fix.
- **A real registry.** Stand up Apicurio Registry, register `ParcelScan` V1 with `FULL`
  compatibility, and try to register V3. Then delete your unit-test gate — or keep both, and
  justify the duplication.
- **Share group at scale.** Run `aShareGroupCan` with 8 workers on a 1-partition topic and a
  backlog produced *before* the workers start. Measure the distribution, then explain the result
  using acquisition locks and fetch batch sizes rather than "share groups are unfair".

## Resources

- **Gwen Shapira, Todd Palino, Rajini Sivaram & Krit Petty — *Kafka: The Definitive Guide*, 2nd
  ed.** (O'Reilly) — chapters 3–4 (producers, consumers, keys and partitions), 6 (internals and
  reliability) and 7 (reliable data delivery). The reference text for every mechanic in steps 2–5.
- **Gunnar Morling — "Kafka Queues: Now and in the Future" / KIP-932 explainer** (morling.dev) —
  the clearest walk-through of share groups, acquisition locks and delivery counts, including what
  they are *not*.
- **KIP-932: Queues for Kafka** (Apache Kafka wiki) — the primary source; read the "Share group
  state" and "Client API" sections before you design around share groups.
- **Soby Chacko — "Spring for Apache Kafka: Share Consumers" / KIP-932 support in Spring Kafka**
  (spring.io blog) — `DefaultShareConsumerFactory`, `ShareKafkaListenerContainerFactory` and the
  `ShareAckMode` options used in step 1's hint.
- **Confluent Developer courses** — *Apache Kafka Internal Architecture*, *Kafka Consumers*, and
  *Schema Registry & Data Governance* (developer.confluent.io) — short, free, and the assignment /
  rebalance animations are worth more than the equivalent prose.
- **Gregor Hohpe & Bobby Woolf — *Enterprise Integration Patterns*** (Addison-Wesley) — Message
  Channel, Point-to-Point vs Publish-Subscribe, Dead Letter Channel, Invalid Message Channel,
  Competing Consumers, Message Router. Every mechanic in this lesson has a 2003 name; using it
  makes design conversations shorter.
- **RabbitMQ documentation, 4.x** (rabbitmq.com/docs) — "Exchanges and routing", "Consumer
  acknowledgements and publisher confirms", "Quorum queues" and "Dead letter exchanges". Read the
  acknowledgement page against Kafka's offset model.
- **Apache Kafka documentation — consumer configuration** (kafka.apache.org/documentation) —
  `max.poll.records`, `max.poll.interval.ms`, `session.timeout.ms`, `heartbeat.interval.ms`,
  `group.protocol`, and the share-group group configs. The defaults are a design opinion; know it.
- **Spring for Apache Kafka reference — "Handling Exceptions"** (docs.spring.io) —
  `DefaultErrorHandler`, `DeadLetterPublishingRecoverer`, `@RetryableTopic`, and the blocking vs
  non-blocking retry comparison behind step 6.

---

**Footnotes on this scaffold (verified on this machine, August 2026).**

1. Kafka image is `apache/kafka:4.2.0` — share groups are GA there; on 4.1 they are a preview
   behind feature flags. `kafka-clients` 4.2.1 comes from Boot 4.1.1's dependency management, so no
   pin is needed for `KafkaShareConsumer`.
2. Testcontainers 2.0.5: `org.testcontainers:testcontainers-kafka` →
   `org.testcontainers.kafka.KafkaContainer`; `org.testcontainers:testcontainers-rabbitmq` →
   `org.testcontainers.rabbitmq.RabbitMQContainer` (the 1.x `org.testcontainers.containers`
   classes are still in the jars, deprecated).
3. Kafka auto-configuration requires `spring-boot-starter-kafka` in Boot 4; Jackson 3 is not on the
   classpath transitively here, so `tools.jackson.core:jackson-databind` is declared explicitly.
4. The RabbitMQ image is `rabbitmq:3-management-alpine` (cached locally) while the documentation
   linked above is 4.x. Quorum queues, dead-letter exchanges and topic routing behave the same; the
   4.x differences that matter are defaults (quorum by default) and the removal of classic mirrored
   queues.
5. `share.auto.offset.reset` is set per group via `Admin.incrementalAlterConfigs` on a
   `ConfigResource(Type.GROUP, …)`; `share.acknowledgement.mode=explicit` is a client property.
   Both are required for the share-group tests, and neither has a Spring Boot property.
