# iExec Core Scheduler

## Overview

An iExec workerpool is orchestrated by an _iExec Core Scheduler_. The _iExec Core Scheduler_ watches on-chain deals and schedules off-chain computation among available workers.

## Configuration

The _iExec Core Scheduler_ is available as an OCI image on [Docker Hub](https://hub.docker.com/r/iexechub/iexec-core/tags).

To run properly, the _iExec Core Scheduler_ requires:
* A blockchain node. iExec smart contracts must be deployed on the blockchain network.
* An Ethereum wallet to interact with smart contracts on the blockchain network. To perform on-chain transactions, the wallet must be loaded with at least 0.1 ETH and some RLC.
* A _MongoDB_ instance to persist its data.
* An _iExec Blockchain Adapter_ for several blockchain network interactions.
* An _iExec Result Proxy_ to check if tasks results have been published.
* An _iExec Platform Registry_ to retrieve locations of _iExec SMS_ services.
* An _iExec Config Server_ to retrieve configuration about blockchain.
* One or many _iExec Secret Management Service_ instances (referenced by the _iExec Platform Registry_) to handle secrets and enclave sessions of TEE tasks.

You can configure the _iExec Core Scheduler_ with the following properties:

| Environment variable | Description | Type | Default value |
| --- | --- | --- | --- |
| `IEXEC_CORE_PORT` | Server port of the _iExec Core Scheduler_. | Positive integer | `13000` |
| `MONGO_HOST` | _MongoDB_ server host. Cannot be set with URI. | String | `localhost` |
| `MONGO_PORT` | _MongoDB_ server port. Cannot be set with URI. | Positive integer | `13002` |
| `IEXEC_PLATFORM_REGISTRY` | _iExec Platform Registry_ server URL. | URL | `http://localhost:8888` |
| `IEXEC_PLATFORM_REGISTRY_STACK` | [optional] Use a specific stack configuration exposed by the _iExec Platform Registry_. | String | |
| `IEXEC_PLATFORM_REGISTRY_LABEL` | [optional] Use a labeled version of configuration files exposed by the _iExec Platform Registry_. It might be a Git label such as `main`, `v10` or `07998be`. | String | |
| `REVEAL_TIMEOUT_PERIOD` | Detector period to track reveal timeouts for tasks. | Positive integer | `120000` |
| `IEXEC_ASK_REPLICATE_PERIOD` | Worker configuration, interval in milliseconds between 2 replicate requests. | Positive integer | `5000` |
| `IEXEC_CORE_REQUIRED_WORKER_VERSION` | Empty value will allow any worker version. | String | |
| `IEXEC_WORKERS_WHITELIST` | List of worker addresses allowed to connect to the _iExec Core Scheduler_. | String | |
| `IEXEC_CORE_WALLET_PATH` | Path to the wallet of the server. | String | `./src/main/resources/wallet/encrypted-wallet_scheduler.json` |
| `IEXEC_CORE_WALLET_PASSWORD` | Password to unlock the wallet of the server. | String | `whatever` |
| `IEXEC_PRIVATE_CHAIN_ADDRESS` | Private URL to connect to the blockchain node. | URL | `http://localhost:8545` |
| `POOL_ADDRESS` | On-chain address of the workerpool managed by the current _iExec Core Scheduler_. | String | `0x365E7BABAa85eC61Dffe5b520763062e6C29dA27` |
| `IEXEC_START_BLOCK_NUMBER` | Subscribe to new deal events from a specific block number. | Positive integer | `0` |
| `IEXEC_GAS_PRICE_MULTIPLIER` | Transactions will be sent with `networkGasPrice * gasPriceMultiplier`. | Float | `1.0` |
| `IEXEC_GAS_PRICE_CAP` | In Wei, will be used for transactions if `networkGasPrice * gasPriceMultiplier > gasPriceCap` | Integer | `22000000000` |
| `IEXEC_CORE_CHAIN_ADAPTER_PROTOCOL` | _iExec Blockchain Adapter_ communication protocol. | String | `http` |
| `IEXEC_CORE_CHAIN_ADAPTER_HOST` | _iExec Blockchain Adapter_ server host. | String | `localhost` |
| `IEXEC_CORE_CHAIN_ADAPTER_PORT` | _iExec Blockchain Adapter_ server port. | Positive integer | `13010` |
| `IEXEC_CORE_CHAIN_ADAPTER_USERNAME` | Username to connect to the _iExec Blockchain Adapter_ server. | String | `admin` |
| `IEXEC_CORE_CHAIN_ADAPTER_PASSWORD` | Password to connect to the _iExec Blockchain Adapter_ server. | String | `whatever` |
| `IEXEC_CONFIG_SERVER_PROTOCOL` | _iExec Config Server_ communication protocol. | String | `http` |
| `IEXEC_CONFIG_SERVER_HOST` | _iExec Config Server_ host. | String | `localhost` |
| `IEXEC_CONFIG_SERVER_PORT` | _iExec Config Server_ port. | Positive integer | `8888` |
| `IEXEC_RESULT_REPOSITORY_PROTOCOL` | _iExec Result Proxy_ server communication protocol. | String | `http` |
| `IEXEC_RESULT_REPOSITORY_HOST` | _iExec Result Proxy_ server host. | String | `localhost` |
| `IEXEC_RESULT_REPOSITORY_PORT` | _iExec Result Proxy_ server port. | Positive integer | `13200` |
| `IEXEC_CORE_MANAGEMENT_ACTUATORS` | Endpoint IDs that should be included or `*` for all. | String | `health, info` |
| `IEXEC_LOGS_PURGE_RATE_IN_DAYS` | Interval in days between 2 executions of the purge mechanism. | Positive integer | `1` |
| `IEXEC_LOGS_AVAILABILITY_PERIOD_IN_DAYS` | Number of days to keep logs of past tasks. | Positive integer | `3` |

If it is not the first startup of the _iExec Core Scheduler_ and if it received deals previously,
the _MongoDB_ instance will contain a __configuration Collection__ in the __iexec Database__.
The value stored in this document takes the precedence over the `IEXEC_START_BLOCK_NUMBER` configuration parameter.
To enforce deal observation starting from the `IEXEC_START_BLOCK_NUMBER` value, the aforementioned document has to be deleted in the _MongoDB_.
All deals prior to the `IEXEC_START_BLOCK_NUMBER` will then be ignored.

A more exhaustive documentation is available on [the official documentation of iExec](https://docs.iex.ec/).

## Health checks

A health endpoint (`/actuator/health`) is enabled by default and can be accessed on the `IEXEC_CORE_PORT`.
This endpoint allows to define health checks in an orchestrator or a [compose file](https://github.com/compose-spec/compose-spec/blob/master/spec.md#healthcheck).
No default strategy has been implemented in the [Dockerfile](Dockerfile) at the moment.

## Build from sources

```
./gradlew build
```

## License

This repository code is released under the [Apache License 2.0](LICENSE).
