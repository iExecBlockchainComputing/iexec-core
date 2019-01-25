# iexec-core

## Overview

The core component is orchestrating tasks and provides workers  with work.

## Run it

Gradle is used to build the project. It can be built using the following command:
```
./gradle build
```
They are several ways to run the project.
1. Run it with gradle.
```
./gradle bootRun
```
2. Run it with docker:
```
docker run  -e MONGO_HOST=mongo \
    		-e IEXEC_CORE_WALLET_PATH=#path_to_wallet \
    		-e IEXEC_PRIVATE_CHAIN_ADDRESS=#chain_used_by_core \
    		-e IEXEC_PUBLIC_CHAIN_ADDRESS=#chain_used_by_workers \
    		-e IEXEC_HUB_ADDRESS=#address_of_hub_contract_onchain \
    		-e POOL_ADDRESS=#address_of_pool_contract_onchain \
    		-e IEXEC_START_BLOCK_NUMBER=#chain_start_block_number \
    		-v #path_to_wallet:/iexec-wallet \
    		-p 18090:18090 \
    	iexechub/iexec-core:3.0.0-alpha1
```
Some other variables are settable through docker. Please check the application.yml file.



The scheduling logic is in the core. It is in charge of orchestrating the tasks.
