package com.gpb.replication.sapiq.service;


import java.util.List;

import com.gpb.replication.sapiq.dto.SourceDbConnections;

public interface DbSourcesService {
     List<SourceDbConnections> getDbConnections();
}
