package io.github.phlekies.smarthome.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RadioMathTest {

    private static final double WIFI_MHZ = 2400.0;

    @Nested
    @DisplayName("Path loss")
    class PathLoss {

        @Test
        void freeSpaceLossAtOneMetreMatchesFriis() {
            // 32.45 + 20·log10(2400) + 20·log10(0.001 km) = 40.05 dB
            assertEquals(40.0542, RadioMath.fsplDb(1.0, WIFI_MHZ), 1e-4);
        }

        @Test
        void doublingTheDistanceAddsSixDecibels() {
            double delta = RadioMath.fsplDb(20.0, WIFI_MHZ) - RadioMath.fsplDb(10.0, WIFI_MHZ);
            assertEquals(20.0 * Math.log10(2.0), delta, 1e-9);
        }

        @Test
        void logDistanceWithExponentTwoIsFreeSpace() {
            for (double d : new double[] { 0.5, 1.0, 3.0, 17.0, 42.0 }) {
                assertEquals(RadioMath.fsplDb(d, WIFI_MHZ), RadioMath.logDistanceLossDb(d, WIFI_MHZ, 2.0), 1e-9);
            }
        }

        @Test
        void logDistanceExponentSetsTheSlopePerDecade() {
            double oneMetre = RadioMath.logDistanceLossDb(1.0, WIFI_MHZ, 3.0);
            double tenMetres = RadioMath.logDistanceLossDb(10.0, WIFI_MHZ, 3.0);
            assertEquals(30.0, tenMetres - oneMetre, 1e-9);
        }

        @Test
        void zeroDistanceHasNoLoss() {
            assertEquals(0.0, RadioMath.fsplDb(0.0, WIFI_MHZ));
            assertEquals(0.0, RadioMath.logDistanceLossDb(0.0, WIFI_MHZ, 3.0));
        }
    }

    @Nested
    @DisplayName("Noise and capacity")
    class NoiseAndCapacity {

        @Test
        void noiseFloorOfA20MHzWifiChannel() {
            // −174 + 10·log10(20e6) + 7 = −93.99 dBm
            assertEquals(-93.9897, RadioMath.noiseFloorDbm(20e6, 7.0), 1e-4);
        }

        @Test
        void wavelengthAt2400MHz() {
            assertEquals(0.124913, RadioMath.wavelengthMeters(WIFI_MHZ), 1e-6);
        }

        @ParameterizedTest(name = "{0} dBm = {1} mW")
        @CsvSource({ "0, 1", "20, 100", "-30, 0.001", "10, 10" })
        void convertsBetweenDbmAndMilliwatts(double dbm, double milliwatt) {
            assertEquals(milliwatt, RadioMath.dbmToMilliwatt(dbm), milliwatt * 1e-12);
            assertEquals(dbm, RadioMath.milliwattToDbm(milliwatt), 1e-9);
        }

        @Test
        void shannonCapacityAtZeroDecibelsEqualsTheBandwidth() {
            // log2(1 + 1) = 1 bit/s/Hz
            assertEquals(20.0, RadioMath.shannonCapacityMbps(0.0, 20e6), 1e-9);
        }

        @Test
        void shannonCapacityAtThirtyDecibels() {
            double expected = 20e6 * Math.log(1001.0) / Math.log(2.0) / 1e6;
            assertEquals(expected, RadioMath.shannonCapacityMbps(30.0, 20e6), 1e-9);
        }
    }

    @Nested
    @DisplayName("Bit error rate")
    class BitErrorRate {

        @ParameterizedTest(name = "erfc({0}) = {1}")
        @CsvSource({ "0.0, 1.0", "0.5, 0.4795001", "1.0, 0.1572992", "2.0, 0.0046777", "-1.0, 1.8427008" })
        void erfcMatchesReferenceValues(double x, double expected) {
            assertEquals(expected, RadioMath.erfc(x), 2e-7);
        }

        @Test
        void bpskNeedsAbout9point6DecibelsForOneErrorInTenThousand() {
            // Textbook result: BER = 1e-5 at Eb/N0 ≈ 9.6 dB for BPSK.
            double ber = RadioMath.bpskBer(9.6);
            assertTrue(ber > 0.8e-5 && ber < 1.2e-5, "BER was " + ber);
        }

        @Test
        void berTendsToOneHalfWithoutSignal() {
            assertEquals(0.5, RadioMath.bpskBer(-60.0), 1e-3);
        }

        @Test
        void berDecreasesWithSnr() {
            double previous = 1.0;
            for (double snr = -10.0; snr <= 12.0; snr += 1.0) {
                double ber = RadioMath.bpskBer(snr);
                assertTrue(ber < previous, "BER must fall as SNR grows");
                previous = ber;
            }
        }
    }

    @Nested
    @DisplayName("Knife-edge diffraction")
    class KnifeEdge {

        @Test
        void grazingIncidenceCostsAboutSixDecibels() {
            assertEquals(6.03, RadioMath.knifeEdgeLossDb(0.0), 0.01);
        }

        @Test
        void clearFresnelZoneHasNoLoss() {
            assertEquals(0.0, RadioMath.knifeEdgeLossDb(-1.0));
        }

        @Test
        void deeperShadowMeansMoreLoss() {
            assertTrue(RadioMath.knifeEdgeLossDb(2.0) > RadioMath.knifeEdgeLossDb(1.0));
            assertTrue(RadioMath.knifeEdgeLossDb(1.0) > RadioMath.knifeEdgeLossDb(0.0));
        }
    }

    @Nested
    @DisplayName("Angles")
    class Angles {

        @ParameterizedTest(name = "normalize({0}) = {1}")
        @CsvSource({ "-90, 270", "720, 0", "365, 5", "0, 0", "359.5, 359.5" })
        void normalizesToZeroThreeSixty(double input, double expected) {
            assertEquals(expected, RadioMath.normalizeAngleDeg(input), 1e-9);
        }

        @ParameterizedTest(name = "|{0} - {1}| = {2}")
        @CsvSource({ "350, 10, 20", "10, 350, 20", "0, 180, 180", "90, 90, 0", "-45, 45, 90" })
        void angularDistanceTakesTheShortWayRound(double a, double b, double expected) {
            assertEquals(expected, RadioMath.angularDistanceDeg(a, b), 1e-9);
        }
    }
}
