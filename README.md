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
* An _iExec Secret Management Service_ (_iExec SMS_) for secret and enclave sessions management of TEE tasks.

You can configure the _iExec Core Scheduler_ with the following properties:

| Environment variable | Description | Type | Default value |
| --- | --- | --- | --- |
| `IEXEC_CORE_PORT` | Server port of the _iExec Core Scheduler_. | Positive integer | `13000` |
| `MONGO_HOST` | _MongoDB_ server host. Cannot be set with URI. | String | `localhost` |
| `MONGO_PORT` | _MongoDB_ server port. Cannot be set with URI. | Positive integer | `13002` |
| `REVEAL_TIMEOUT_PERIOD` | Detector period to track reveal timeouts for tasks. | Positive integer | `120000` |
| `IEXEC_ASK_REPLICATE_PERIOD` | Worker configuration, interval in milliseconds between 2 replicate requests. | Positive integer | `5000` |
| `IEXEC_CORE_REQUIRED_WORKER_VERSION` | Empty value will allow any worker version. | String | |
| `IEXEC_WORKERS_WHITELIST` | List of worker addresses allowed to connect to the _iExec Core Scheduler_. | String | |
| `IEXEC_CORE_WALLET_PATH` | Path to the wallet of the server. | String | `./src/main/resources/wallet/encrypted-wallet_scheduler.json` |
| `IEXEC_CORE_WALLET_PASSWORD` | Password to unlock the wallet of the server. | String | `whatever` |
| `IEXEC_CHAIN_ID` | Chain ID of the blockchain network to connect. | Positive integer | `17` |
| `IEXEC_IS_SIDECHAIN` | Define if iExec on-chain protocol is built on top of token (`false`) or native currency (`true`). | Boolean | `false` |
| `IEXEC_PRIVATE_CHAIN_ADDRESS` | Private URL to connect to the blockchain node. | URL | `http://localhost:8545` |
| `IEXEC_PUBLIC_CHAIN_ADDRESS` | [unused] Public URL to connect to the blockchain node. | URL | `http://localhost:8545` |
| `IEXEC_HUB_ADDRESS` | Proxy contract address to interact with the iExec on-chain protocol. | String | `0xBF6B2B07e47326B7c8bfCb4A5460bef9f0Fd2002` |
| `POOL_ADDRESS` | On-chain address of the workerpool managed by the current _iExec Core Scheduler_. | String | `0x365E7BABAa85eC61Dffe5b520763062e6C29dA27` |
| `IEXEC_START_BLOCK_NUMBER` | Subscribe to new deal events from a specific block number. | Positive integer | `0` |
| `IEXEC_GAS_PRICE_MULTIPLIER` | Transactions will be sent with `networkGasPrice * gasPriceMultiplier`. | Float | `1.0` |
| `IEXEC_GAS_PRICE_CAP` | In Wei, will be used for transactions if `networkGasPrice * gasPriceMultiplier > gasPriceCap` | Integer | `22000000000` |
| `IEXEC_CORE_CHAIN_ADAPTER_PROTOCOL` | _iExec Blockchain Adapter_ communication protocol. | String | `http` |
| `IEXEC_CORE_CHAIN_ADAPTER_HOST` | _iExec Blockchain Adapter_ server host. | String | `blockchain-adapter` |
| `IEXEC_CORE_CHAIN_ADAPTER_PORT` | _iExec Blockchain Adapter_ server port. | Positive integer | `13010` |
| `IEXEC_CORE_CHAIN_ADAPTER_USERNAME` | Username to connect to the _iExec Blockchain Adapter_ server. | String | `admin` |
| `IEXEC_CORE_CHAIN_ADAPTER_PASSWORD` | Password to connect to the _iExec Blockchain Adapter_ server. | String | `whatever` |
| `IEXEC_RESULT_REPOSITORY_PROTOCOL` | _iExec Result Proxy_ server communication protocol. | String | `http` |
| `IEXEC_RESULT_REPOSITORY_HOST` | _iExec Result Proxy_ server host. | String | `localhost` |
| `IEXEC_RESULT_REPOSITORY_PORT` | _iExec Result Proxy_ server port. | Positive integer | `13200` |
| `IEXEC_IPFS_HOST` | [unused] _IPFS_ node host. | String | `127.0.0.1` |
| `IEXEC_IPFS_PORT` | [unused] _IPFS_ node port. | Positive integer | `5001` |
| `IEXEC_SMS_PROTOCOL` | _iExec SMS_ server communication protocol. | String | `http` |
| `IEXEC_SMS_HOST` | _iExec SMS_ server host. | String | `localhost` |
| `IEXEC_SMS_PORT` | _iExec SMS_ server port. | Positive integer | `13300` |
| `IEXEC_CORE_MANAGEMENT_PORT` | Management endpoint HTTP port (uses the same port as the application by default). Configure a different port to use management-specific SSL. | Positive integer | `13001` |
| `IEXEC_CORE_MANAGEMENT_ACTUATORS` | Endpoint IDs that should be included or `*` for all. | String | `health, info` |
| `IEXEC_CORE_GRAYLOG_HOST` | _Graylog_ server host. | String | `localhost` |
| `IEXEC_CORE_GRAYLOG_PORT` | _Graylog_ server port. | Positive integer | `12201` |
| `IEXEC_LOGS_PURGE_RATE_IN_DAYS` | Interval in days between 2 executions of the purge mechanism. | Positive integer | `1` |
| `IEXEC_LOGS_AVAILABILITY_PERIOD_IN_DAYS` | Number of days to keep logs of past tasks. | Positive integer | `3` |

A more exhaustive documentation is available on [the official documentation of iExec](https://docs.iex.ec/).

## Build from sources

```
./gradlew build
```
