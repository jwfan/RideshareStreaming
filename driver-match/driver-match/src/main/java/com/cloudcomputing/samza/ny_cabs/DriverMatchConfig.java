package com.cloudcomputing.samza.ny_cabs;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;

import org.apache.samza.system.SystemStream;

public class DriverMatchConfig {
    public static final SystemStream DRIVER_LOC_STREAM = new SystemStream("kafka", "driver-locations");
    public static final SystemStream EVENT_STREAM = new SystemStream("kafka", "events");
    public static final SystemStream CHECK_STREAM = new SystemStream("kafka", "check-stream");
    public static final SystemStream MATCH_STREAM = new SystemStream("kafka", "match-stream");
    public static final SystemStream LOG_STREAM = new SystemStream("kafka", "log");
    public static final SystemStream LEADER_STREAM = new SystemStream("kafka", "leaderboard");
}
