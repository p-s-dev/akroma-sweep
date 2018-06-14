package com.psdev.aka.sweep.service;

import com.psdev.aka.sweep.data.MasternodeStatus;
import com.psdev.aka.sweep.data.MasternodesApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class AkromaApiService {

    RestTemplate restTemplate = new RestTemplate();

//    @Value("${akroma.network.api}")
//    String akromaApi;

    @Value("${akroma.masternodes.status}")
    String masternodeStatusUrl;

//    public BigInteger getAkromaApiCurrentBlock() {
//        AkromaNetwork network = restTemplate.getForEntity(akromaApi, AkromaNetwork.class).getBody();
//        return network.getHeight();
//    }

    public List<MasternodeStatus> getNodeStatus() {
        MasternodesApiResponse nodesList = restTemplate.getForEntity(masternodeStatusUrl, MasternodesApiResponse.class).getBody();
        return nodesList.data;

    }

}
