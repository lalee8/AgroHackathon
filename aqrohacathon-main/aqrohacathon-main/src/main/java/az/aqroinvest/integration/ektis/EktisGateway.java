package az.aqroinvest.integration.ektis;

import java.math.BigDecimal;

/**
 * EKTİS (Elektron Kənd Təsərrüfatı İnformasiya Sistemi) ilə inteqrasiya portu (Ports & Adapters).
 * Portalın biznes məntiqi yalnız bu interfeysdən asılıdır; real EKTİS API-si təqdim olunduqda
 * yalnız adapter (HttpEktisGateway) konfiqurasiyası dəyişir.
 */
public interface EktisGateway {

    /** VÖEN və ya FİN üzrə fermer reyestrində yoxlama. */
    FarmerCheck checkFarmer(String taxId);

    /** Domen hadisəsinin EKTİS-ə çatdırılması. */
    DeliveryResult deliver(IntegrationEvent event);

    record FarmerCheck(boolean available, String status, String farmerId, BigDecimal registeredLandHa,
                       Integer subsidiesReceived, String message) {
        public static FarmerCheck unavailable(String reason) {
            return new FarmerCheck(false, "UNAVAILABLE", null, null, null, reason);
        }
    }

    record DeliveryResult(boolean success, String externalRef, String error) {}
}
