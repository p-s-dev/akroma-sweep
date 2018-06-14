package com.psdev.aka.sweep.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;

@PropertySource(value = "file:///${keys.file.path}/application.properties", ignoreResourceNotFound = true)
@Configuration
public class ApplicationConfig {

    @Value("${web3.rpc.url}")
    String web3RpcUrl;
    @Value("${akroma.public.web3.rpc.url}")
    String publicRpcUrl;
    @Value("${default.gas.limit}")
    String gasLimit;
    @Value("${default.gas.price}")
    String gasPrice;

    @Primary
    @Bean
    public Web3j web3j() {
        Web3j web3j = Web3j.build(new HttpService(web3RpcUrl));
        return web3j;
    }

    @Bean
    public Web3j publicWeb3Rpc() {
        Web3j web3j = Web3j.build(new HttpService(publicRpcUrl));
        return web3j;
    }

    @Bean
    BigInteger gasPrice() {
        return new BigInteger(gasPrice);
    }

    @Bean
    BigInteger gasLimit() {
        return new BigInteger(gasLimit);
    }

}
