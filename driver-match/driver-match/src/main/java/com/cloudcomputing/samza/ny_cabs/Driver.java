package com.cloudcomputing.samza.ny_cabs;

public class Driver {

	private String driverId;
	// private String latitude;
	// private String longitude;
	// private String status;
	// private String rating;
	// private String salary;
	// private String gender;
	private Double matchScore;

	public Driver(String driverId, Double matchScore) {
		this.driverId = driverId;
		this.matchScore = matchScore;
	}

	public String getId() {
		return driverId;
	}

	public void setId(String id) {
		this.driverId = id;
	}

	public Double getMatchScore() {
		return matchScore;
	}

	public void setMatchScore(Double score) {
		this.matchScore = score;
	}

}
