package com.cloudcomputing.samza.ny_cabs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Map.Entry;

import org.apache.samza.config.Config;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskContext;
import org.apache.samza.task.TaskCoordinator;
import org.apache.samza.task.WindowableTask;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.json.JSONObject;

public class Driverlocation implements StreamTask, InitableTask, WindowableTask {
	private double MAX_MONEY = 100.0;
	private KeyValueStore<String, Object> driverLoc;

	@Override
	@SuppressWarnings("unchecked")
	public void init(Config config, TaskContext context) throws Exception {
		driverLoc = (KeyValueStore<String, Object>) context.getStore("driver-loc");
	}

	@Override
	@SuppressWarnings("unchecked")
	public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator coordinator) {
		// The main part of your code. Remember that all the messages for a
		// particular partition
		// come here (somewhat like MapReduce). So for task 1 messages for a
		// blockId will arrive
		// at one task only, thereby enabling you to do stateful stream
		// processing.
		String incomingStream = envelope.getSystemStreamPartition().getStream();
		
		if (incomingStream.equals(DriverMatchConfig.DRIVER_LOC_STREAM.getStream())) {
			// Handle Driver Location messages
			processDriverLocation((Map<String, Object>) envelope.getMessage());
		} else {
			throw new IllegalStateException("Unexpected input stream: " + envelope.getSystemStreamPartition());
		}
	}

	private void processDriverLocation(Map<String, Object> message) {
		if (!message.get("type").equals("DRIVER_LOCATION")) {
			throw new IllegalStateException("Unexpected event type on DriverLocation stream: " + message.get("type"));
		} else {
			
		}
	}

	private void driverOut(Object sender, MessageCollector collector) {

		try {
			collector.send(new OutgoingMessageEnvelope(DriverMatchConfig.LEADER_STREAM, sender, null, sender));
		} catch (Exception e) {
			// TODO: handle exception
			collector.send(new OutgoingMessageEnvelope(DriverMatchConfig.LOG_STREAM, e.toString(), null, sender));
		}
	}

	@Override
	public void window(MessageCollector collector, TaskCoordinator coordinator) {
		// this function is called at regular intervals, not required for this
		// project
	}
}
