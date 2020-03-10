package com.iexec.core.result.repo.proxy;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface JwtRepository extends MongoRepository<Jwt, String> {

    Optional<Jwt> findByWalletAddress(String walletAddress);
}
