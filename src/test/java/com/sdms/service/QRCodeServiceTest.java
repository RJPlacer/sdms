package com.sdms.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QRCodeServiceTest {

    @Test
    void buildsSelfContainedLocalPayloads() {
        assertEquals(
            "Athlete Track:ATHLETE|ID:ATH-2026-0001|NAME:Dela Cruz, Juan S.|SCHOOL:San Jose NHS",
            QRCodeService.buildAthletePayload("ATH-2026-0001", "Dela Cruz, Juan S.", "San Jose NHS"));
        assertEquals(
            "Athlete Track:OFFICIAL|ID:OFC-2026-0001|NAME:Maria Santos|SCHOOL:Division Office",
            QRCodeService.buildOfficialPayload("OFC-2026-0001", "Maria Santos", "Division Office"));
        assertEquals(
            "Athlete Track:COACH|ID:CCH-2026-0001|NAME:Pedro Reyes|SCHOOL:San Jose NHS",
            QRCodeService.buildCoachPayload("CCH-2026-0001", "Pedro Reyes", "San Jose NHS"));
    }
}
