package com.cloudcomputing.samza.ny_cabs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

import scala.Array;

public class DriverMatchTask implements StreamTask, InitableTask, WindowableTask {
	private double MAX_MONEY = 100.0;
	private KeyValueStore<String, Map<String, Object>> driverLoc;

	@Override
	@SuppressWarnings("unchecked")
	public void init(Config config, TaskContext context) throws Exception {
		// blockId,<driverId,(la:1,lo:2)>
		driverLoc = (KeyValueStore<String, Map<String, Object>>) context.getStore("driver-loc");
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
		} else if (incomingStream.equals(DriverMatchConfig.EVENT_STREAM.getStream())) {
			// Handle Event messages
			processEvents((Map<String, Object>) envelope.getMessage(), collector);
		} else {
			throw new IllegalStateException("Unexpected input stream: " + envelope.getSystemStreamPartition());
		}
	}

	private void processDriverLocation(Map<String, Object> message) {
		if (!message.get("type").equals("DRIVER_LOCATION")) {
			throw new IllegalStateException("Unexpected event type on DriverLocation stream: " + message.get("type"));
		} else {

			String blockId = (String) message.get("blockId").toString();
			String driverId = (String) message.get("driverId").toString();
			if (driverLoc.get(blockId) != null && driverLoc.get(blockId).containsKey(driverId)) {
				ObjectMapper mapper = new ObjectMapper();
				mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
				String tempString = "";
				try {
					tempString = mapper.writeValueAsString(driverLoc.get(blockId).get(driverId));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if (tempString.startsWith("\""))
					tempString = tempString.substring(1);
				if (tempString.endsWith("\""))
					tempString = tempString.substring(0, tempString.length() - 1);
				tempString = tempString.replaceAll("\\\\", "");
				JSONObject temp = new JSONObject(tempString);
				temp.put("latitude", (Double) message.get("latitude"));
				temp.put("longitude", (Double) message.get("longitude"));
				Map<String, Object> map = driverLoc.get(blockId);
				if (map == null) {
					map = new HashMap<String, Object>();
				}
				map.put(driverId, temp.toString());
				driverLoc.put(blockId, map);
			}
		}
	}

	private void processEvents(Map<String, Object> message, MessageCollector collector) {
		if (message.get("type").equals("LEAVING_BLOCK")) {
			//when encounters leaving block ,delete the driver from the block
			String blockId = (String) message.get("blockId").toString();
			String driverId = (String) message.get("driverId").toString();
			if (driverLoc.get(blockId) != null && driverLoc.get(blockId).containsKey(driverId)) {
				Map<String, Object> map = driverLoc.get(blockId);
				if (map != null) {
					map.remove(driverId);
					driverLoc.put(blockId, map);
				}
			}
		} else if (message.get("type").equals("ENTERING_BLOCK")) {
			//when encountering entering block event, add the driver into block 
			//with both status of available and unavailable
			String blockId = (String) message.get("blockId").toString();
			String driverId = (String) message.get("driverId").toString();
			Map<String, Object> map = driverLoc.get(blockId);
			if (map == null) {
				map = new HashMap<String, Object>();
			}
			map.put(driverId, message);
			driverLoc.put(blockId, map);
		} else if (message.get("type").equals("RIDE_COMPLETE")) {
			//when encounters ride_complete, update the driver's rating and put
			//in to block, then sort the driverleader
			String blockId = (String) message.get("blockId").toString();
			String driverId = (String) message.get("driverId").toString();
			Double rating = (Double) message.get("rating");
			Double u_rating = (Double) message.get("user_rating");
			Map<String, Object> map = driverLoc.get(blockId);
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
			message.put("rating", ((rating + u_rating) / 2));
			if (map == null) {
				map = new HashMap<String, Object>();
			}
			map.put(driverId, message);
			driverLoc.put(blockId, map);
			ArrayList<Integer> rank = driverRank(blockId);

			JSONObject outJson = new JSONObject();
			outJson.put("blockId", Integer.parseInt(blockId));
			outJson.put("driverId", Integer.parseInt(driverId));
			outJson.put("ranks", rank);
			Object o = null;
			try {
				o = mapper.readValue(outJson.toString(), Object.class);
			} catch (Exception ee) {
				ee.printStackTrace();
			}
			driverOut(o, collector);
		} else if (message.get("type").equals("RIDE_REQUEST")) {

		} else {
			throw new IllegalStateException("Unexpected event type on messages stream: " + message.get("type"));
		}
	}

	private ArrayList<Integer> driverRank(String blockId) {
		//sort the driverId by rating in priority queue
		//when rating equal, sort by driverId
		PriorityQueue<Driver> driverRank = new PriorityQueue<Driver>(11, new Comparator<Driver>() {
			@Override
			public int compare(Driver o1, Driver o2) {
				if (o1.getMatchScore() > o2.getMatchScore()) {
					return -1;
				} else if (o1.getMatchScore().equals(o2.getMatchScore())) {
					return (Integer.valueOf(o1.getId()).compareTo(Integer.valueOf(o2.getId())));
				} else {
					return 1;
				}
			}
		});
		for (Entry<String, Object> entry : driverLoc.get(blockId).entrySet()) {
			String driverId = entry.getKey();
			Object object = entry.getValue();
			String objectString = "";
			try {
				ObjectMapper mapper = new ObjectMapper();
				mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
				objectString = mapper.writeValueAsString(object);
			} catch (Exception ee) {
				ee.printStackTrace();
			}
			if (objectString.startsWith("\""))
				objectString = objectString.substring(1);
			if (objectString.endsWith("\""))
				objectString = objectString.substring(0, objectString.length() - 1);
			objectString = objectString.replaceAll("\\\\", "");
			JSONObject jo = new JSONObject(objectString);
			Double rating = jo.getDouble("rating");
			driverRank.add(new Driver(driverId, rating));
		}
		ArrayList<Integer> rankList = new ArrayList<Integer>();
		if (driverRank.peek() != null) {
			rankList.add(Integer.valueOf(driverRank.poll().getId()));
		}
		if (driverRank.peek() != null) {
			rankList.add(Integer.valueOf(driverRank.poll().getId()));
		}
		if (driverRank.peek() != null) {
			rankList.add(Integer.valueOf(driverRank.poll().getId()));
		}
		return rankList;
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
