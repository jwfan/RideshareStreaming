import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.kafka.clients.producer.*;
import org.json.JSONObject;

public class DataProducer extends Thread {

	public static void main(String[] args) {
		/*
		 * Task 1: In Task 1, you need to read the content in the tracefile we
		 * give to you, and create two streams, feed the messages in the
		 * tracefile to different streams based on the value of "type" field in
		 * the JSON string.
		 * 
		 * Please note that you're working on an ec2 instance, but the streams
		 * should be sent to your samza cluster. Make sure you can consume the
		 * topics on the master node of your samza cluster before make a
		 * submission.
		 */
		Properties props = new Properties();
		props.put("bootstrap.servers", "localhost:9092");
		props.put("acks", "1");
		props.put("retries", 0);
		props.put("batch.size", 16384);
		props.put("linger.ms", 1);
		props.put("buffer.memory", 33554432);
		props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
		String fileName = "/home/hadoop/driver-match/trace";
		File file = new File(fileName);
		BufferedReader br = null;
		Producer<String, String> producer = new KafkaProducer<>(props);
		String line;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
			while ((line = br.readLine()) != null) {
				// driver-locations
				if (line.contains("DRIVER_LOCATION")) {
					JSONObject jo = new JSONObject(line);
					Integer blockId = jo.getInt("blockId");
					Double latitude = jo.getDouble("latitude");
					Double longitude = jo.getDouble("longitude");
					Double timestamp=System.currentTimeMillis()/1000000000.0;
					ArrayList<Double> output=new ArrayList<Double>();
					output.add(latitude);
					output.add(longitude);
					output.add(timestamp);
					producer.send(new ProducerRecord<String, String>("driver-locations", blockId.toString(), output.toString()));
				}

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		producer.close();
	}
}
