package de.zalando.zmon.metriccache;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Created by jmussler on 4/22/15.
 */
public class CheckData {
    public String time;
    public String worker;
    public int check_id;
    public String entity_id;
    public Map<String,String> entity;
    public double run_time;
    public JsonNode check_result;
    public boolean exception;
    public Map<String, AlertData> alerts;
}
