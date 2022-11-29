# iExec Core Scheduler

## Overview

The goal of the core component is to monitor the chain to check if there is any work to perform. In case there is, the core will orchestrate the work among the different workers that will participate in the computation.

## Configuration

There needs to be a running mongo instance as mongo is used to store data. Another pre-requisite is to have a wallet, the core component should be allowed to access it.
The wallet used by the core should contain at least 0.1 ETH and some RLC to start.
Since the core will perform some transactions on the blockchain and handle some RLC, it needs both at start-up.

| Environment variable | Description | Type | Default value |
| --- | --- | --- | --- |
| `IEXEC_CORE_PORT` |  |  | `13000` |
| `MONGO_HOST` |  |  | `localhost` |
| `MONGO_PORT` |  |  | `13002` |
| `REVEAL_TIMEOUT_PERIOD` | 2m |  | `120000` |
| `IEXEC_ASK_REPLICATE_PERIOD` |  |  | `5000` |
| `IEXEC_CORE_REQUIRED_WORKER_VERSION` | leave empty will allow any worker version |  |  |
| `IEXEC_WORKERS_WHITELIST` |  |  |  |
| `IEXEC_CORE_WALLET_PATH` |  |  | `./src/main/resources/wallet/encrypted-wallet_scheduler.json` |
| `IEXEC_CORE_WALLET_PASSWORD` |  |  | `whatever` |
| `IEXEC_CHAIN_ID` |  |  | `17` |
| `IEXEC_IS_SIDECHAIN` |  |  | `false` |
| `IEXEC_PRIVATE_CHAIN_ADDRESS` |  |  | `http://localhost:8545` |
| `IEXEC_PUBLIC_CHAIN_ADDRESS` |  |  | `http://localhost:8545` |
| `IEXEC_HUB_ADDRESS` |  |  | `0xBF6B2B07e47326B7c8bfCb4A5460bef9f0Fd2002` |
| `POOL_ADDRESS` |  |  | `0x365E7BABAa85eC61Dffe5b520763062e6C29dA27` |
| `IEXEC_START_BLOCK_NUMBER` |  |  | `0` |
| `IEXEC_GAS_PRICE_MULTIPLIER` | txs will be sent with networkGasPrice*gasPriceMultiplier, 4.0 means super fast |  | `1.0` |
| `IEXEC_GAS_PRICE_CAP` | In Wei, will be used for txs if networkGasPrice*gasPriceMultiplier > gasPriceCap |  | `22000000000` |
| `IEXEC_CORE_CHAIN_ADAPTER_PROTOCOL` |  |  | `http` |
| `IEXEC_CORE_CHAIN_ADAPTER_HOST` |  |  | `blockchain-adapter` |
| `IEXEC_CORE_CHAIN_ADAPTER_PORT` |  |  | `13010` |
| `IEXEC_CORE_CHAIN_ADAPTER_USERNAME` |  |  | `admin` |
| `IEXEC_CORE_CHAIN_ADAPTER_PASSWORD` |  |  | `whatever` |
| `IEXEC_RESULT_REPOSITORY_PROTOCOL` |  |  | `http` |
| `IEXEC_RESULT_REPOSITORY_HOST` |  |  | `localhost` |
| `IEXEC_RESULT_REPOSITORY_PORT` |  |  | `13200` |
| `IEXEC_IPFS_HOST` |  |  | `127.0.0.1` |
| `IEXEC_IPFS_PORT` |  |  | `5001` |
| `IEXEC_SMS_PROTOCOL` |  |  | `http` |
| `IEXEC_SMS_HOST` |  |  | `localhost` |
| `IEXEC_SMS_PORT` |  |  | `13300` |
| `IEXEC_CORE_MANAGEMENT_PORT` |  |  | `13001` |
| `IEXEC_CORE_MANAGEMENT_ACTUATORS` |  |  | `health, info` |
| `IEXEC_CORE_GRAYLOG_HOST` |  |  | `localhost` |
| `IEXEC_CORE_GRAYLOG_PORT` |  |  | `12201` |
| `IEXEC_LOGS_PURGE_RATE_IN_DAYS` |  |  | `1` |
| `IEXEC_LOGS_AVAILABILITY_PERIOD_IN_DAYS` |  |  | `3` |

A more exhaustive documentation is available on [the official documentation of iExec](https://docs.iex.ec/).

## Build from sources

```
./gradlew build
```
