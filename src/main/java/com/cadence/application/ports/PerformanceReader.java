package com.cadence.application.ports;

import com.cadence.domain.*;
import java.util.*;

public interface PerformanceReader {
  List<PerformanceSample> read(UUID brand, UUID workspace, PerformanceQuery query);
}
