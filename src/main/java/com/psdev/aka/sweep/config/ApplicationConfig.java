package com.psdev.aka.sweep.config;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
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

    @Value("${web3.rpc.auth.username}")
    String rpcuser;
    @Value("${web3.rpc.auth.password}")
    String rpcpass;


    @Primary
    @Bean
    public Web3j web3j() {
        OkHttpClient httpClient = new OkHttpClient.Builder().authenticator(new Authenticator() {
            public Request authenticate(Route route, Response response) throws IOException {
                String credential = Credentials.basic(rpcuser, rpcpass);
                if (responseCount(response) >= 3) {
                    return null;
                }
                return response.request().newBuilder().header("Authorization", credential).build();
            }
        }).build();
        Web3j web3j = Web3j.build(new HttpService(web3RpcUrl, httpClient, false));
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

    private static int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

}
