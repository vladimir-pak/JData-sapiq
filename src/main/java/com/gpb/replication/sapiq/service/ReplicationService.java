package com.gpb.replication.sapiq.service;

public interface ReplicationService {
   void startReplicationAsync(String serviceName);
   void startReplication(String serviceName);
}
