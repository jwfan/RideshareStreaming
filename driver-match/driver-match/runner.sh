##############################################
# Runner Script for deploying samza job    ###
##############################################

# Preparing folder for deployment
mkdir -p deploy/samza

# Compile and build the jar
mvn clean package
rm -rf deploy/samza/*

# Extract tar.gz file to deployment folder
tar -xvf target/ny_cabs-0.0.1-dist.tar.gz -C deploy/samza/

# Copy the tar.gz file to hadoop file system
hadoop fs -copyFromLocal -f target/ny_cabs-0.0.1-dist.tar.gz /

# Run the job on the cluster
deploy/samza/bin/run-job.sh --config-factory=org.apache.samza.config.factories.PropertiesConfigFactory --config-path=file://$PWD/deploy/samza/config/driver-match.properties
