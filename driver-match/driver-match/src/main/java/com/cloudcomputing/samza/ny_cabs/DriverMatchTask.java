package com.cloudcomputing.samza.ny_cabs;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

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

import java.util.Map.Entry;

/**
 * Consumes the stream of driver location updates and rider cab requests.
 * Outputs a stream which joins these 2 streams and gives a stream of rider to
 * driver matches.
 */
public class DriverMatchTask implements StreamTask, InitableTask, WindowableTask {

	/* Define per task state here. (kv stores etc) */
	private double MAX_MONEY = 100.0;
	private KeyValueStore<String, Map<String, Object>> driverLoc;

	@Override
	@SuppressWarnings("unchecked")
	public void init(Config config, TaskContext context) throws Exception {
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

	// driverlocation is used to update the location of driver in realtime
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
		// if the event is leaving block, simply delete this driver from current
		// block
		if (message.get("type").equals("LEAVING_BLOCK")) {
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
			// if event is entering block, only add this driver when the status
			// is available
			// if the status is unavailable, the event ride complete will handle
			// this driver
			String status = (String) message.get("status");
			if (status.equals("AVAILABLE")) {
				String blockId = (String) message.get("blockId").toString();
				String driverId = (String) message.get("driverId").toString();
				Map<String, Object> map = driverLoc.get(blockId);
				if (map == null) {
					map = new HashMap<String, Object>();
				}
				map.put(driverId, message);
				driverLoc.put(blockId, map);
			}
		} else if (message.get("type").equals("RIDE_REQUEST")) {
			// if the event is ride request, find the highest match score dirver
			// for the client
			// and delete this driver from the current available block
			String blockId = (String) message.get("blockId").toString();
			String clientId = (String) message.get("clientId").toString();
			Double latitude = (Double) message.get("latitude");
			Double longitude = (Double) message.get("longitude");
			String genderPref = (String) message.get("gender_preference");
			String driverId = driverMatch(blockId, latitude, longitude, genderPref);
			Map<String, Object> map = driverLoc.get(blockId);
			if (map != null) {
				map.remove(driverId);
				driverLoc.put(blockId, map);
			}
			JSONObject outJson = new JSONObject();
			outJson.put("clientId", Integer.parseInt(clientId));
			outJson.put("driverId", Integer.parseInt(driverId));

			Object o = null;
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
			try {
				o = mapper.readValue(outJson.toString(), Object.class);
			} catch (Exception ee) {
				ee.printStackTrace();
			}
			// output format:{"clietId":1,"driverId":2}
			driverOut(o, collector);
		} else if (message.get("type").equals("RIDE_COMPLETE")) {
			// if the event is ride complete, update the driver's rating by the
			// average of original rating and new user_rating, and add this
			// driver
			// to the block
			String blockId = (String) message.get("blockId").toString();
			String driverId = (String) message.get("driverId").toString();
			Double rating = (Double) message.get("rating");
			Double u_rating = (Double) message.get("user_rating");
			Map<String, Object> map = driverLoc.get(blockId);
			message.put("rating", ((rating + u_rating) / 2));
			if (map == null) {
				map = new HashMap<String, Object>();
			}
			map.put(driverId, message);
			driverLoc.put(blockId, map);
		} else {
			throw new IllegalStateException("Unexpected event type on messages stream: " + message.get("type"));
		}
	}

	// driverMatch input blockId and client's information, and return the
	// highest match score driver
	private String driverMatch(String blockId, Double cLatitude, Double cLongitude, String genderPref) {
		PriorityQueue<Driver> driverRank = new PriorityQueue<Driver>(11, new Comparator<Driver>() {
			@Override
			public int compare(Driver o1, Driver o2) {
				if (o1.getMatchScore() > o2.getMatchScore()) {
					return -1;
				} else if (o1.getMatchScore() == o2.getMatchScore()) {
					if (Long.valueOf(o1.getId()) > Long.valueOf(o2.getId())) {
						return -1;
					} else if (o1.getId().equals(o2.getId())) {
						return 0;
					} else {
						return 1;
					}
				} else {
					return 0;
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
			Double dLatitude = jo.getDouble("latitude");
			Double dLongitude = jo.getDouble("longitude");
			String gender = jo.getString("gender");
			Double rating = jo.getDouble("rating");
			Integer salary = jo.getInt("salary");
			Double cdDistance = Math
					.sqrt(Math.pow((dLatitude - cLatitude), 2) + Math.pow((dLongitude - cLongitude), 2));
			Double distanceScore = 1 * Math.pow(Math.E, -1 * cdDistance);
			Double genderScore = genderPref.equals(gender) ? 1.0 : 0.0;
			Double ratingScore = rating / 5.0;
			Double salaryScore = 1 - salary / 100.0;
			Double matchScore = distanceScore * 0.4 + genderScore * 0.2 + ratingScore * 0.2 + salaryScore * 0.2;
			driverRank.add(new Driver(driverId, matchScore));
		}

		return driverRank.peek().getId();
	}

	private void driverOut(Object sender, MessageCollector collector) {

		try {
			collector.send(new OutgoingMessageEnvelope(DriverMatchConfig.MATCH_STREAM, sender, null, sender));
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
