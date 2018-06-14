package com.psdev.aka.sweep.data;

import lombok.Data;

import java.util.List;

@Data
public class MasternodesApiResponse {
    public List<MasternodeStatus> data;
    public Boolean ok;
    public String message;
}
