# Changelog

All notable changes to this project will be documented in this file.

## [[NEXT]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/vNEXT) 2024

### New Features

- Push scheduler Result Proxy URL as Web2 secret to SMS. (#718)

### Bug Fixes

- Start scheduler in out-of-service mode. (#712)
- Scheduler needs to enter out-of-service mode on first blockchain communication loss.
  This is due to **Nethermind v1.14.7+99775bf7** where filters are lost on restart. (#715)
- Use Result Proxy URL defined in deal parameters if any, fall back to scheduler default one otherwise. (#716)
- Update off-chain task model with on-chain task consensus data in all workflows. (#720)

### Quality

- Reorder static and final keywords. (#717)
- Update `contribution` and `contributionAndFinalize` detector tests.
  TEE tasks with callback are now eligible to `contributeAndFinalize` flow. (#719)
- Resolve deprecations caused by `TaskDescription` in `ReplicatesService` and `ResultService`. (#723)
- Add missing `@PreDestroy` annotation in services implementing `Purgeable`. (#724)

### Dependency Upgrades

- Upgrade to `eclipse-temurin:11.0.24_8-jre-focal`. (#713)
- Upgrade to Gradle 8.10.2. (#714)
- Upgrade to `testcontainers` 1.20.4. (#721)
- Upgrade to `mongo:7.0.15-jammy`. (#722)

## [[8.5.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.5.0) 2024-06-19

### Deprecation Notices

- Deprecate legacy task feedback API endpoints. (#701)

### New Features

- Create `iexec-task-api` to access task feedback API. (#695)
- Move `notification` package from `iexec-commons-poco` to `iexec-core-library`. (#697 #698)
- Move `PublicConfiguration` class from `iexec-common` to `iexec-core-library`. (#699)
- Create `ConfigServerClient` instance and use it. (#700)
- Allow up to 32 task updates at a given time. (#703)
- Index `currentStatus` field in `task` collection. (#707)
- Replace `CredentialsService` with `SignerService`. (#708)

### Bug Fixes

- Always use `WorkerpoolAuthorization` to retrieve JWT and check result upload on Result Proxy. (#690)
- Use correct `Signature` import in `SchedulerClient`. (#697)
- Do not supply replicates past their contribution deadline to workers. (#702)
- Query blockchain adapter every 2s instead of every second. (#706)

### Quality

- Configure Gradle JVM Test Suite Plugin. (#691)
- Rename `IexecTaskApiClient` to `TaskApiClient`. (#696)
- Move `ReplicateTaskSummary` from `iexec-common` to `iexec-core`. (#704 #705)

### Dependency Upgrades

- Upgrade to Gradle 8.7. (#692)
- Upgrade to `eclipse-temurin:11.0.22_7-jre-focal`. (#693)
- Upgrade to Spring Boot 2.7.18. (#694)
- Upgrade to `iexec-commons-poco` 4.1.0. (#709)
- Upgrade to `iexec-common` 8.5.0. (#709)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.5.0. (#709)
- Upgrade to `iexec-result-proxy-library` 8.5.0. (#709)
- Upgrade to `iexec-sms-library` 8.6.0. (#709)

## [[8.4.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.4.1) 2024-04-03

### New Features

- Add `ConsensusReachedTaskDetector` to detect missed `TaskConsensus` on-chain events. (#683 #684)
- Generate enclave challenge with `Authorization` header after on-chain task has been initialized. (#686)

### Bug Fixes

- Keep a single `updateReplicateStatus` method in `ReplicatesService`. (#670)
- Check result has been uploaded for TEE tasks. (#672)
- Check for consensus early if a worker has already `CONTRIBUTED` when the task is updated to `RUNNING`. (#673)
- Always provide a `WorkerpoolAuthorization` to a worker during its recovery. (#674)
- Move task metrics from `TaskUpdateManager` to `TaskService`. (#676)
- Fail fast when tasks are detected past their contribution or final deadline. (#677)
- Mitigate potential race conditions by enforcing `currentStatus` value when updating a task. (#681)
- Use semaphores in `TaskUpdateRequestManager` to avoid blocking task update threads. (#685)

### Quality

- Prepare migration to `java.time` package by building `Date` objects from `Instant` objects. (#671)
- Add logs for better traceability. (#675)
- Remove code only used in tests from `TaskService` and `Task`. (#678 #679)
- Implement each task status transition in a single method. (#680)
- Execute `TaskUpdateManager` tests on a running MongoDB container. (#682)

### Dependency Upgrades

- Upgrade to `iexec-sms-library` 8.5.1. (#687)

## [[8.4.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.4.0) 2024-02-29

### New Features

- Use `MongoTemplate` to enable document update without full rewrite. (#661)

### Bug Fixes

- Filter out `CONTRIBUTE_AND_FINALIZE` tasks when detecting missed `REVEALED` status update. (#658)
- Fetch `results` on-chain when updating a replicate status in `CONTRIBUTE_AND_FINALIZE` workflow. (#659 #660)
- Properly catch all runtime exceptions when an enclave challenge generation fails. (#663)

### Quality

- Use `@DataMongoTest` and `@Testcontainers` annotations in replicates, compute logs and tasks tests. (#662 #664 #665)

### Dependency Upgrades

- Upgrade to `iexec-common` 8.4.0. (#666)
- Upgrade to `iexec-blockchain-adapter-library` 8.4.0. (#667)
- Upgrade to `iexec-result-proxy-library` 8.4.0. (#667)
- Upgrade to `iexec-sms-library` 8.5.0. (#667)

## [[8.3.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.3.0) 2024-01-11

### New Features

- Create `iexec-core-library` sub-project to split shared code/apis from specific scheduler application code. (#623)
- Move first DTO classes to `iexec-core-library` subproject. (#626)
- Move `PlatformMetric` to `iexec-core-library` subproject, modify it to become immutable. (#628 #629)
- Add prometheus endpoint with custom metrics. (#632)
- Expose version through prometheus endpoint. (#637, #639)
- Stop fetching completed tasks count from DB. (#638)
- Expose current task statuses count to Prometheus and `/metrics` endpoint. (#640, #654)
- Add `tasks` endpoints to `iexec-core-library`. (#645)

### Quality

- Add and use a non-root user in the dockerfile. (#627)
- Replace single thread executor with synchronized keyword. (#633)
- Move contribution status checks from `iexec-commons-poco`. (#636)
- Use `BlockchainAdapterService` from `iexec-blockchain-adapter-api-library`. (#641)
- `ResultRepositoryConfiguration` and `WorkerConfiguration` classes are now immutable with `@Value` lombok annotation. (#650)

### Bug Fixes

- Fix web security depreciation warning. (#624)
- Move `TaskModel` and `ReplicateModel` instances creation methods to `Task` and `Replicate` classes. (#625)
- Expose `TaskLogsModel` on `TaskController` instead of `TaskLogs`. (#631)
- Remove duplicated MongoDB read on `ReplicatesList` during replicate status update. (#647)
- Use less MongoDB calls when updating a task to a final status. (#649)
- Save contribution and result updload replicate data when `CONTRIBUTE_AND_FINALIZE_DONE`. (#651)
- Fix potential `NullPointerException` during first worker replicate request. (#652)
- Fix missed replicate status update detectors to avoid false positives by mixing `CONTRIBUTE-REVEAL-FINALIZE` and `CONTRIBUTE_AND_FINALIZE` workflows. (#653)

### Dependency Upgrades

- Upgrade to `eclipse-temurin:11.0.21_9-jre-focal`. (#635)
- Upgrade to Spring Boot 2.7.17. (#634)
- Upgrade to Spring Dependency Management Plugin 1.1.4. (#634)
- Upgrade to Spring Doc Openapi 1.7.0. (#637)
- Upgrade to `jenkins-library` 2.7.4. (#630)
- Upgrade to `iexec-commons-poco` 3.2.0. (#648)
- Upgrade to `iexec-common` 8.3.1. (#648)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.3.0. (#655)
- Upgrade to `iexec-result-proxy-library` 8.3.0. (#655)
- Upgrade to `iexec-sms-library` 8.4.0. (#655)

## [[8.2.3]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.2.3) 2023-12-14

### Bug Fixes

- Check if Worker can still accept more work right before giving it a new replicate. (#644)

## [[8.2.2]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.2.2) 2023-12-13

### Bug Fixes

- Replace `findAllByWalletAddress` with `findByWalletAddressIn` in `WorkerRepository`. (#643)

## [[8.2.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.2.1) 2023-12-13

### Bug Fixes

- Do not write `lastAliveDate` or `lastReplicateDemandDate` to MongoDB to decrease load on the database. (#642)

## [[8.2.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.2.0) 2023-09-29

### New Features

- Add blockchain connection health indicator. (#601)
- Block some connections and messages when blockchain connection is down. (#604)
- Block deal watching mechanisms when communication with the blockchain node is lost. (#606)
- Use `isEligibleToContributeAndFinalize` method from `TaskDescription`. (#619)

### Bug Fixes

- Clean call to `iexecHubService#getTaskDescriptionFromChain` in test. (#597)
- Reject deal if TEE tag but trust not in {0,1}. (#598)
- Fix and harmonize `Dockerfile entrypoint` in all Spring Boot applications. (#614)
- Use `mongo:4.4` in tests with `MongoDBContainer`. Replace `getContainerIpAddress` with `getHost`. (#616)

### Quality

- Remove `nexus.intra.iex.ec` repository. (#605)
- Remove `Graylog` support. Fetch logs with a sidecar to push them to your log infrastructure. (#607)
- Events are now immutable with `@Value` lombok annotation. (#608)
- Fix several code smells. (#609)
- Upgrade to Gradle 8.2.1 with up-to-date plugins. (#612)
- Remove `VersionService#isSnapshot`. (#618)

### Dependency Upgrades

- Remove `logstash-gelf` dependency. (#607)
- Upgrade to `eclipse-temurin` 11.0.20. (#610)
- Upgrade to Spring Boot 2.7.14. (#611)
- Upgrade to Spring Dependency Management Plugin 1.1.3. (#611)
- Upgrade to `testcontainers` 1.19.0. (#613)
- Upgrade to `jenkins-library` 2.7.3. (#615)
- Upgrade to `iexec-commons-poco` 3.1.0. (#617)
- Upgrade to `iexec-common` 8.3.0. (#617)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.2.0. (#620)
- Upgrade to `iexec-result-proxy-library` 8.2.0. (#620)
- Upgrade to `iexec-sms-library` 8.3.0. (#620)

## [[8.1.2]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.1.2) 2023-06-29

## Bug fixes

- Prevent race conditions in `WorkerService`. (#602)

### Dependency Upgrades

- Upgrade to `iexec-commons-poco` 3.0.5. (#602)

## [[8.1.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.1.1) 2023-06-23

### Dependency Upgrades

- Upgrade to `iexec-common` 8.2.1. (#599)
- Upgrade to `iexec-commons-poco` 3.0.4. (#599)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.1.1. (#599)
- Upgrade to `iexec-result-proxy-library` 8.1.1. (#599)
- Upgrade to `iexec-sms-library` 8.1.1. (#599)

## [[8.1.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.1.0) 2023-06-09

### New Features

- Add ContributeAndFinalize to `ReplicateWorkflow`. (#574)
- Add check for ContributeAndFinalize in `ReplicatesService`. (#576 #582)
- Add `running2Finalized2Completed` in `TaskUpdateManager`. (#577 #578)
- Disable `contributeAndFinalize` with CallBack. (#579 #581)
- Add purge cached task descriptions ability. (#587)
- Add detectors for `ContributeAndFinalize` flow. (#590 #593)

### Bug Fixes

- Prevent race condition on replicate update. (#568)
- Use builders in test classes. (#589)

### Quality

- Remove unused methods in `IexecHubService`. (#572)
- Clean unused Replicate methods and update tests. (#573)
- Clean unused `ReplicateStatus#RESULT_UPLOAD_REQUEST_FAILED`. (#575)
- Refactor unnotified detectors to avoid code duplication. (#580)
- Use `==` or `!=` operators to test the equality of enums. (#584)
- Rearrange checks order to avoid call to database. (#585)
- Move methods to get event blocks from `iexec-commons-poco`. (#588)
- Rename detectors' methods and fields to match Ongoing/Done standard. (#591)

### Dependency Upgrades

- Upgrade to `iexec-common` 8.2.0. (#571 #575 #586 #594)
- Add new `iexec-commons-poco` 3.0.2 dependency. (#571 #574 #586 #587 #588 #592 #594)
- Upgrade to `iexec-blockchain-adapter-api-library` 8.1.0. (#594)
- Upgrade to `iexec-result-proxy-library` 8.1.0. (#594)
- Upgrade to `iexec-sms-library` 8.1.0. (#594)

## [[8.0.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.0.1) 2023-03-20

### Bug Fixes

- Remove explicit version on `micrometer-registry-prometheus` dependency. (#563)
- Send a `TaskNotificationType` to worker with a 2XX HTTP status code. (#564)
- Remove `com.iexec.core.dataset` package. (#565)
- Improve log on `canUpdateReplicateStatus` method. (#566)

## [[8.0.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v8.0.0) 2023-03-08

### New Features

* Support Gramine framework for TEE tasks.
* Retrieve location of SMS services through an _iExec Platform Registry_.
* Improve authentication on scheduler.
    * burn challenge after login.
    * handle JWT expiration through the expiration claim.
    * cache JWT until expiration.
    * better claims usage.
* Show application version on banner.

### Bug Fixes

* Always return a `TaskNotificationType` on replicate status update when it has been authorized.
* Handle task added twice.

### Quality

* Improve code quality and tests.
* Removed unused variables in configuration.
* Use existing `toString()` method to serialize and hash scheduler public configuration.
* Use recommended annotation in `MetricController`.
* Remove `spring-cloud-starter-openfeign` dependency.

### Dependency Upgrades

* Replace the deprecated `openjdk` Docker base image with `eclipse-temurin` and upgrade to Java 11.0.18 patch.
* Upgrade to Spring Boot 2.6.14.
* Upgrade to Gradle 7.6.
* Upgrade OkHttp to 4.9.0.
* Upgrade `jjwt` to `jjwt-api` 0.11.5.
* Upgrade to `iexec-common` 7.0.0.
* Upgrade to `jenkins-library` 2.4.0.

## [[7.3.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.3.1) 2023-02-17

* Subscribe only to deal events targeting a specific workerpool.

## [[7.3.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.3.0) 2022-12-18

* Add endpoint to allow health checks.

## [[7.2.2]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.2.2) 2022-12-20

* Use `iexec-common` version [6.2.0](https://github.com/iExecBlockchainComputing/iexec-common/releases/tag/v6.2.0).
* Use `okhttp` version 4.9.0 to keep it consistent with the one in the web3j dependency imported by `iexec-common`.

## [[7.2.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.2.1) 2022-12-13

* Replace `sessionId` implementation with a hash of the public configuration. From a consumer point of view, a constant
  hash received from the `POST /ping` response indicates that the scheduler configuration has not changed. With such
  constant hash, either the scheduler has restarted or not, the consumer does not need to reboot.

## [[7.2.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.2.0) 2022-12-09

* Increments of jenkins-library up to version 2.2.3. Enable SonarCloud analyses on branches and Pull Requests.
* Update `iexec-common` version
  to [6.1.0](https://github.com/iExecBlockchainComputing/iexec-common/releases/tag/v6.1.0).

## [[7.1.3]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.1.3) 2022-12-07

* Bump version to 7.1.3.

## [[7.1.2]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.1.2) 2022-12-07

* Update README and add CHANGELOG.

## [[7.1.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.1.1) 2022-11-28

* Fix build process.

## [[7.1.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.1.0) 2022-08-11

* Retrieve a task execution status. Logs can only be retrieved by the person who requested the execution.
* Use OpenFeign client libraries.
* Fix concurrency issues.
* Use Spring Boot 2.6.2.
* Use Java 11.0.15.

## [[7.0.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v7.0.1) 2022-01-05

* Reduce probability of giving tasks whose consensus is already reached to additional workers.
* Remove useless logs.
* Handle task supply and task update management based on their level of priority.
* Upgrade automated build system.

## [[7.0.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/7.0.0) 2021-12-17

Highly improved throughput of the iExec protocol.

What has changed since v6.0.0?

* Fix task status deadlock. Chances to reach RUNNING task status for given states of replicates are now increased.
* Fix race condition on replicate attribution.
* Upgrade Jacoco/Sonarqube reporting and plugins.
* Consume blockchain configuration on iexec-blockchain-adapter-api & expose its URL for iexec-worker
* Upgrade artifacts publishing
* Enable local import of iexec-common
* Upgrade to Gradle 6.8.3
* Upgrade to JUnit5
* Fix concurrent upload requests.
* Merge abort notifications into a single notification with custom abort cause.
* Send transactions over a dedicated blockchain adapter micro-service.
* Reply gracefully to the worker when a status is already reported.
* Abort a TEE task when all alive workers did fail to run it.
* Move task state machine to a dedicated service.
* Remove useless internal task statuses belonging to the result upload request stage.
* Fix Recovering for the Retryable updateReplicateStatus(..) method.
* Add checks before locally upgrading to the INITIALIZED status
* Remove 2-blocks waiting time before supplying a new replicate
* Fix OptimisticLockingFailureException happening when 2 detectors detect the same change at the same time, leading to
  race updates on a same task
* Reuse socket when sending multiple requests to a blockchain node.
* Replay fromBlockNumber now lives in a dedicated configuration:
  A configuration document did store two different states. Updates on different states at the same time might lead to
  race conditions when saving to database. Now each state has its own document to avoid race conditions when saving.
* Fix TaskRepositoy.findByChainDealIdAndTaskIndex() to return unique result.

## [[6.4.2]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.4.2) 2021-12-14

* Fix task status deadlock. Chances to reach RUNNING task status for given states of replicates are now increased.

## [[6.4.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.4.1) 2021-11-30

* Fix race condition on replicate attribution.
* Upgrade Jacoco/Sonarqube reporting and plugins.

## [[6.4.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.4.0) 2021-11-25

* Consume blockchain configuration on iexec-blockchain-adapter-api & expose its URL for iexec-worker.
* Upgrade artifacts publishing.
* Enable local import of iexec-common.
* Upgrade to Gradle 6.8.3.
* Upgrade to JUnit5.

## [[6.3.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.3.0) 2021-11-16

* Fix concurrent upload requests.
* Merge abort notifications into a single notification with custom abort cause.

## [[6.2.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.2.0) 2021-11-05

* Send transactions over a dedicated blockchain adapter micro-service.
* Reply gracefully to the worker when a status is already reported.
* Abort a TEE task when all alive workers did fail to run it.
* Moved task state machine to a dedicated service.
* Removed useless internal task statuses belonging to the result upload request stage.

## [[6.1.6]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.1.6) 2021-10-21

* Fixed Recovering for the Retryable updateReplicateStatus(..) method.

## [[6.1.5]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.1.5) 2021-10-21

* Added checks before locally upgrading to the INITIALIZED status.
* Removed 2-blocks waiting time before supplying a new replicate.

## [[6.1.4]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.1.4) 2021-10-14

* Fixed OptimisticLockingFailureException happening when 2 detectors detect the same change at the same time, leading to
  race updates on a same task.

## [[6.1.3]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.1.3) 2021-10-05

* Bump iexec-common dependency (iexec-common@5.5.1) featuring socket reuse when sending multiple requests to a
  blockchain node.

## [[6.1.2]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.1.2) 2021-10-01

Bugfix - Replay fromBlockNumber now lives in a dedicated configuration:

* A configuration document did store two different states. Updates on different states at the same time might lead to
  race conditions when saving to database. Now each state has its own document to avoid race conditions when saving.

## [[6.0.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/6.0.1) 2021-09-28

* Fix : TaskRepositoy.findByChainDealIdAndTaskIndex() returns non-unique result

## [[6.0.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/v6.0.0) 2021-07-29

What's new?

* Added Prometheus actuators
* Moved TEE workflow configuration to dedicated service

## [[5.1.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/5.1.1) 2021-04-12

What is patched?

* Updated management port and actuator endpoints.

## [[5.1.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/5.1.0) 2021-03-26

What's new?

Fix WebSockets problem:

* Enhance different task stages detection. When detecting unnotified contribute/reveal,
  we use the initialization block instead of the current block to lookup for the contribute/reveal metadata.
* Use a dedicated TaskScheduler for STOMP WebSocket heartbeats.
* Use a dedicated TaskScheduler for @Scheduled tasks.
* Use a dedicated TaskExecutor for @Async tasks.
* TaskService is now the entry point to update tasks.
* feature/task-replay.

Also:

* Use the deal block as a landmark for a task.
* Keep the computing task list consistent.
* Worker should be instructed to contribute whenever it is possible (e.g. app/data download failure).
* Enhance worker lost detection.
* Enhance final deadline detection for tasks.

## [[5.0.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/5.0.0) 2020-07-22

What's new?

* Dapp developers can browse worker computing logs over iexec-core API.
* Task result link is standardized and generic. It supports Ethereum, IPFS & Dropbox "storage" providers.
* Result storage feature is moved to
  new [iExec Result Proxy](https://github.com/iExecBlockchainComputing/iexec-result-proxy).
* Full compatibility with new [iExec Secret Management Service](https://github.com/iExecBlockchainComputing/iexec-sms).
* Compatibility with latest PoCo 5.1.0 smart contracts.

## [[4.0.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/4.0.1) 2020-02-25

What's fixed?

* More resistance to unsync Ethereum nodes.

## [[4.0.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/4.0.0) 2019-12-13

What's new?

* Native-token sidechain compatibility.
* GPU workers support.
* Log aggregation.

What's fixed?

* Database indexes.
* JWT/challenge validity duration.
* Worker freed after contribution timeout.

## [[3.2.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/3.2.0) 2019-09-17

What is new?

* Bag Of Tasks (BoT): Bag Of Tasks Deals can now be processed by the middleware.
* Use of iexec input files: Sometimes external resources are needed to run a computation without using a dataset, that
  is what input files are for.
* Whitelisting: Now the core can define a whitelist of workers that are allowed to connect to the pool.
* Https: Workers can connect to the core using https.

What is patched?

* The project has been updated to java 11 and web3j 4.3.0.
* Internal refactoring to handle replicates update better.
* Limit workers that ask for replicates too often.
* Update workers configuration when they disconnect/reconnect.

## [[3.1.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/3.1.0) 2019-07-11

What's new?

* Full end-to-end encryption inside a Trusted Execution Environment (TEE) powered by Intel(R) SGX.
* Implemented the Proof-of-Contribution (PoCo) Sarmenta's formula for a greater task dispatching.

What's patched?

* Reopen task worflow is back.
* A single FAILED replicate status when a completion is impossible.
* WORKER_LOST is not set for replicates which are already FAILED.
* Restart when ethereum node is not available at start-up.

## [[3.0.1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/3.0.1) 2019-05-22

This release is a patch to fix an issue following the release of version 3.0.0.

When asking for a replicate, a worker sends the latest available block number from the node it is is connected to.
If that node is a little bit behind the node the core is connected to, the worker will have a disadvantage compare to
workers connected to more up-to-date nodes.
To avoid this disadvantage, now the core waits for a few blocks before sending replicates to workers.

## [[3.0.0]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/3.0.0) 2019-05-15

This release contains the core set of changes in the 3.0.0-alpha-X releases and some other features/fixes.
In summary this version introduces:

* A new architecture: the core has been completely re-architectured from the version 2.
* Latest PoCo use.
* Better management of transaction with the ethereum blockchain.
* Failover mechanisms: in case some workers are lost or restarted when working on a specific task, internal mechanisms
  will redistribute the task or use as much as possible the work performed by the lost / restarted workers.
* iExec End-To-End Encryption with Secret Management Service (SMS): from this version, inputs and outputs of the job can
  be fully encrypted using the Secret Management Service.
* Decentralized oracle: If the result is needed by a smart contract, it is available directly on the blockchain.
* IPFS: data can be retrieved from IPFS and public results can be published on IPFS.

For further information on this version, please read our documentation.

## [[3.0.0-alpha3]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/3.0.0-alpha3) 2019-04-15

* Possibility to choose between slow and fast transactions.
* Embedded IPFS node for default iexec-worker push of the results.
* Better engine for supporting iexec-worker disconnections and recovery actions.
* Brand new Continuous Integration and Delivery Pipeline.
* Enabled SockJS over HTTP WebSocket sub-protocol to bypass some proxies.

## [[3.0.0-alpha2]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/3.0.0-alpha2) 2019-02-08

* Some improvements have been made on the core:
* Some general refactoring on the detectors.
* Bug fix regarding the new PoCo version.
* The core now also checks the errors on the blockchain sent by the workers.
  Enhancement regarding the result repo.
  Updated PoCo chain version 3.0.21.

## [[3.0.0-alpha1]](https://github.com/iExecBlockchainComputing/iexec-core/releases/tag/3.0.0-alpha1) 2019-01-25

* This release is the first alpha release of the version 3.0.0.
