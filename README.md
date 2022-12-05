# iExec Core Scheduler

## Overview

The goal of the core component is to monitor the chain to check if there is any work to perform.
In case there is, the core will orchestrate the work among the different workers that will participate in the computation.

## Configuration

The iExec Scheduler is available as an OCI image on [Docker Hub](https://hub.docker.com/r/iexechub/iexec-core/tags).

To run properly, the iExec Scheduler requires:
* A blockchain node. iExec smart contracts must be deployed on the blockchain network.
* An Ethereum wallet to interact with smart contracts on the blockchain network.
* A MongoDB instance to persist its data.
* An iExec Chain Adapter for several blockchain network interactions.
* An iExec Result Proxy to check if tasks results have been published.
* An iExec Secret Management Service to secret and enclave sessions management of TEE tasks.

There needs to be a running mongo instance as mongo is used to store data.
Another pre-requisite is to have a wallet, the core component should be allowed to access it.
The wallet used by the core should contain at least 0.1 ETH and some RLC to start.
Since the core will perform some transactions on the blockchain and handle some RLC, it needs both at start-up.

You can configure the scheduler with the following properties:

| Environment variable | Description | Type | Default value |
| --- | --- | --- | --- |
| `IEXEC_CORE_PORT` | Server port of the Scheduler. | Positive integer | `13000` |
| `MONGO_HOST` | Mongo server host. Cannot be set with URI. | String | `localhost` |
| `MONGO_PORT` | Mongo server port. Cannot be set with URI. | Positive integer | `13002` |
| `REVEAL_TIMEOUT_PERIOD` | Timeout period for a iExec worker to reveal a task contribution. | Positive integer | `120000` |
| `IEXEC_ASK_REPLICATE_PERIOD` | Worker configuration, interval in milli seconds between 2 replicate requests. | Positive integer | `5000` |
| `IEXEC_CORE_REQUIRED_WORKER_VERSION` | Empty value will allow any worker version. | String | |
| `IEXEC_WORKERS_WHITELIST` | List of worker addresses allowed to connect to the Scheduler. | String | |
| `IEXEC_CORE_WALLET_PATH` | Path to the wallet of the server. | String | `./src/main/resources/wallet/encrypted-wallet_scheduler.json` |
| `IEXEC_CORE_WALLET_PASSWORD` | Password to unlock the wallet of the server. | String | `whatever` |
| `IEXEC_CHAIN_ID` | Chain ID of the blockchain network to connect. | Positive integer | `17` |
| `IEXEC_IS_SIDECHAIN` | Define if iExec on-chain protocol is built on top of token (`false`) or native currency (`true`). | Boolean | `false` |
| `IEXEC_PRIVATE_CHAIN_ADDRESS` | Private URL to connect to the blockchain node. | URL | `http://localhost:8545` |
| `IEXEC_PUBLIC_CHAIN_ADDRESS` | [unused] Public URL to connect to the blockchain node. | URL | `http://localhost:8545` |
| `IEXEC_HUB_ADDRESS` | Proxy contract address to interact with the iExec on-chain protocol. | String | `0xBF6B2B07e47326B7c8bfCb4A5460bef9f0Fd2002` |
| `POOL_ADDRESS` | On-chain address of the worker pool managed by the current scheduler. | String | `0x365E7BABAa85eC61Dffe5b520763062e6C29dA27` |
| `IEXEC_START_BLOCK_NUMBER` | Block number from which starting scans for Deal events. | Positive integer | `0` |
| `IEXEC_GAS_PRICE_MULTIPLIER` | Transactions will be sent with `networkGasPrice * gasPriceMultiplier`. | Float | `1.0` |
| `IEXEC_GAS_PRICE_CAP` | In Wei, will be used for transactions if `networkGasPrice * gasPriceMultiplier > gasPriceCap` | Integer | `22000000000` |
| `IEXEC_CORE_CHAIN_ADAPTER_PROTOCOL` | iExec Chain adapter communication protocol. | String | `http` |
| `IEXEC_CORE_CHAIN_ADAPTER_HOST` | iExec Chain Adapter server host. | String | `blockchain-adapter` |
| `IEXEC_CORE_CHAIN_ADAPTER_PORT` | iExec Chain Adapter server port. | Positive integer | `13010` |
| `IEXEC_CORE_CHAIN_ADAPTER_USERNAME` | Username to connect to the iExec Chain Adapter server. | String | `admin` |
| `IEXEC_CORE_CHAIN_ADAPTER_PASSWORD` | Password to connect to the iExec Chain Adapter server. | String | `whatever` |
| `IEXEC_RESULT_REPOSITORY_PROTOCOL` | iExec Result Proxy server communication protocol. | String | `http` |
| `IEXEC_RESULT_REPOSITORY_HOST` | iExec Result Proxy server host. | String | `localhost` |
| `IEXEC_RESULT_REPOSITORY_PORT` | iExec Result Proxy server port. | Positive integer | `13200` |
| `IEXEC_IPFS_HOST` | IPFS node host. | String | `127.0.0.1` |
| `IEXEC_IPFS_PORT` | IPFS node port. | Positive integer | `5001` |
| `IEXEC_SMS_PROTOCOL` | iExec SMS server communication protocol. | String | `http` |
| `IEXEC_SMS_HOST` | iExec SMS server host. | String | `localhost` |
| `IEXEC_SMS_PORT` | iExec SMS server port. | Positive integer | `13300` |
| `IEXEC_CORE_MANAGEMENT_PORT` | Management endpoint HTTP port (uses the same port as the application by default). Configure a different port to use management-specific SSL. | Positive integer | `13001` |
| `IEXEC_CORE_MANAGEMENT_ACTUATORS` | Endpoint IDs that should be included or '*' for all. | String | `health, info` |
| `IEXEC_CORE_GRAYLOG_HOST` | Graylog server host. | String | `localhost` |
| `IEXEC_CORE_GRAYLOG_PORT` | Graylog server port. | Positive integer | `12201` |
| `IEXEC_LOGS_PURGE_RATE_IN_DAYS` | Interval in days between 2 executions of the purge mechanism. | Positive integer | `1` |
| `IEXEC_LOGS_AVAILABILITY_PERIOD_IN_DAYS` | Number of days to keep logs of past tasks. | Positive integer | `3` |

A more exhaustive documentation is available on [the official documentation of iExec](https://docs.iex.ec/).

## Build from sources

```
./gradlew build
```
