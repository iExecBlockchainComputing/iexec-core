# iexec-core

## Overview

The goal of the core component is to monitor the chain to check if there is any work to perform. In case there is, the core will orchestrate the work among the different workers that will participate in the computation.

## Run an iexec-core

Gradle is used to build the project. It can be built using the gradle wrapper included in the project (so no need to have gradle installed on your machine) with the following command:
```
./gradlew build
```
They are several ways to run the project: with gradle and with docker. In both ways, there needs to be a running mongo instance as mongo is used to store data. Another pre-requisite is to have a wallet, the core component should be allowed to access it.

### With gradle
```
./gradlew bootRun
```
In case you use gradle to run the project, please update the variables set in the application.yml.

### With docker
```
docker run  -e MONGO_HOST=mongo \
            -e IEXEC_CORE_WALLET_PATH=path_to_encrypted_wallet \
            -e IEXEC_CORE_WALLET_PASSWORD=password_of_the_encrypted_wallet \
            -e IEXEC_PRIVATE_CHAIN_ADDRESS=http://chain_used_by_core \
            -e IEXEC_PUBLIC_CHAIN_ADDRESS=http://chain_used_by_workers \
            -e IEXEC_HUB_ADDRESS=0xaddress_of_hub_contract_onchain \
            -e POOL_ADDRESS=0xaddress_of_pool_contract_onchain \
            -e IEXEC_START_BLOCK_NUMBER=chain_start_block_number \
            -v #path_to_wallet:/iexec-wallet \
            -p 18090:18090 \
    	iexechub/iexec-core:3.0.0-alpha1
```
Some other variables are settable through docker. Please check the application.yml file.

**Please note that the wallet used by the core should contain at least 0.1 ETH and some RLC to start**.
Since the core will perform some transactions on the blockchain and handle some RLC, it needs both at start-up.

## Documentation

A more exhaustive documentation is available on [the official documentation of iExec](https://docs.iex.ec/)
