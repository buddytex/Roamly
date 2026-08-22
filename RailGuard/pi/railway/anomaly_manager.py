"""
Anomaly Registry and Lifecycle Management
"""
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple
from config import AnomalyType, AnomalySeverity, AnomalyStatus, TrackStatus

@dataclass
class Anomaly:
    anomaly_id: str
    track_id: str
    chainage_km: float
    x: float
    y: float
    latitude: float = 9.9312
    longitude: float = 76.2673
    reported_type: AnomalyType = AnomalyType.UNKNOWN
    confirmed_type: Optional[AnomalyType] = None
    severity: AnomalySeverity = AnomalySeverity.UNASSESSED
    confidence: float = 0.0
    status: AnomalyStatus = AnomalyStatus.REPORTED
    reported_at: float = field(default_factory=time.time)
    inspected_at: Optional[float] = None
    evidence_images: List[str] = field(default_factory=list)
    recommended_action: str = "Awaiting inspection dispatch"

class AnomalyManager:
    """
    Manages reporting, verification, classification and resolution of railway anomalies.
    """
    def __init__(self):
        self.anomalies: Dict[str, Anomaly] = {}

    def report_anomaly(
        self,
        anomaly_id: str,
        track_id: str,
        chainage_km: float,
        x: float,
        y: float,
        reported_type: AnomalyType = AnomalyType.UNKNOWN,
        latitude: float = 9.9312,
        longitude: float = 76.2673
    ) -> Anomaly:
        anomaly = Anomaly(
            anomaly_id=anomaly_id,
            track_id=track_id,
            chainage_km=chainage_km,
            x=x,
            y=y,
            latitude=latitude,
            longitude=longitude,
            reported_type=reported_type,
            severity=AnomalySeverity.UNASSESSED,
            status=AnomalyStatus.REPORTED
        )
        self.anomalies[anomaly_id] = anomaly
        return anomaly

    def get_anomaly(self, anomaly_id: str) -> Optional[Anomaly]:
        return self.anomalies.get(anomaly_id)

    def assess_anomaly(
        self,
        anomaly_id: str,
        confirmed_type: AnomalyType,
        severity: AnomalySeverity,
        confidence: float,
        evidence_images: List[str]
    ) -> Optional[Anomaly]:
        anomaly = self.anomalies.get(anomaly_id)
        if not anomaly:
            return None

        anomaly.confirmed_type = confirmed_type
        anomaly.severity = severity
        anomaly.confidence = confidence
        anomaly.evidence_images = evidence_images
        anomaly.inspected_at = time.time()
        anomaly.status = AnomalyStatus.CONFIRMED

        # Determine recommended maintenance / track action
        if severity == AnomalySeverity.CRITICAL:
            anomaly.recommended_action = "IMMEDIATE TRACK CLOSURE - Emergency maintenance required"
        elif severity == AnomalySeverity.HIGH:
            anomaly.recommended_action = "SPEED RESTRICTION - Priority maintenance window within 12h"
        elif severity == AnomalySeverity.MEDIUM:
            anomaly.recommended_action = "MONITOR - Schedule maintenance at next cycle"
        else:
            anomaly.recommended_action = "NO RESTRICTION - Routine observation logged"

        return anomaly
